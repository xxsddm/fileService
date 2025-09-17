package com.mini.service.impl;

import com.google.common.collect.Lists;
import com.mini.annotation.ControllerCommonAnnotation;
import com.mini.dto.FileInfoDTO;
import com.mini.dto.PageResult;
import com.mini.entity.FileInfo;
import com.mini.mapper.FileInfoMapper;
import com.mini.nio.NioAsyncExecutor;
import com.mini.service.FileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.stream.Collectors;

/**
 * 文件服务实现类
 */
@Slf4j
@Service
//@Transactional
public class FileServiceImpl implements FileService {

    @Resource
    private FileInfoMapper fileInfoMapper;

    @Value("${file.upload.path}")
    private String uploadPath;

    @Value("${file.upload.max-size}")
    private Long maxFileSize;

    @Value("${file.upload.allowed-types}")
    private String allowedTypes;

    @Resource(name = "invalidFileIdQueue")
    private LinkedBlockingDeque<Long> invalidFileIdQueue;

    @Resource
    private NioAsyncExecutor nioAsyncExecutor;

    // 雪花算法生成id
    private static final SnowflakeIdWorker idWorker = new SnowflakeIdWorker(1, 1);

    @ControllerCommonAnnotation
    @Override
    public FileInfoDTO uploadFile(MultipartFile file) {
        try {
            validateFile(file);
            String fileName = saveFile(file);
            FileInfo fileInfo = new FileInfo();
            fileInfo.setId(idWorker.nextId());
            fileInfo.setFileName(fileName);
            fileInfo.setFilePath(uploadPath + File.separator + fileName);
            fileInfo.setFileSize((int) file.getSize());
            fileInfo.setStatus(0);
            fileInfo.setUploadDate(LocalDateTime.now());
            nioAsyncExecutor.submit(() -> fileInfoMapper.insert(fileInfo)).get();
            return convertToDTO(fileInfo);
        } catch (Exception e) {
            log.error("file upload error", e);
            throw new RuntimeException("file upload error: " + e.getMessage());
        }
    }

    @ControllerCommonAnnotation
    @Override
    public List<FileInfoDTO> uploadFiles(List<MultipartFile> files) {
        List<FileInfoDTO> results = new ArrayList<>();
        List<FileInfo> fileInfos = new ArrayList<>();
        for (MultipartFile file : files) {
            try {
                validateFile(file);
                String fileName = saveFile(file);
                FileInfo fileInfo = new FileInfo();
                fileInfo.setId(idWorker.nextId());
                fileInfo.setFileName(fileName);
                fileInfo.setFilePath(uploadPath + File.separator + fileName);
                fileInfo.setFileSize((int) file.getSize());
                fileInfo.setStatus(0);
                fileInfo.setUploadDate(LocalDateTime.now());
                fileInfos.add(fileInfo);
                results.add(convertToDTO(fileInfo));
            } catch (Exception e) {
                log.error("file upload error: {}", file.getOriginalFilename(), e);
            }
        }
        if (!fileInfos.isEmpty()) {
            try {
                nioAsyncExecutor.submit(() -> fileInfoMapper.batchInsert(fileInfos)).get();
            } catch (Exception e) {
                log.error("file upload nio error", e);
                throw new RuntimeException("file upload nio error: " + e.getMessage());
            }
        }
        return results;
    }

    @ControllerCommonAnnotation
    @Override
    public byte[] downloadFile(String fileName) {
        try {
            FileInfo fileInfo = nioAsyncExecutor.submit(() -> fileInfoMapper.selectByFileName(fileName)).get();
            if (fileInfo == null) {
                throw new RuntimeException("file not found: " + fileName);
            }
            Path filePath = Paths.get(fileInfo.getFilePath());
            if (!Files.exists(filePath)) {
                if (Objects.nonNull(fileInfo.getId())) {
                    invalidFileIdQueue.offer(fileInfo.getId());
                }
                throw new RuntimeException("file not found: " + fileName);
            }
            return Files.readAllBytes(filePath);
        } catch (IOException e) {
            log.error("file download error: {}", fileName, e);
            throw new RuntimeException("file download error: " + fileName);
        } catch (ExecutionException | InterruptedException e) {
            log.error("file download nio error: {}", fileName, e);
            throw new RuntimeException(e);
        }
    }

    @ControllerCommonAnnotation
    @Override
    public PageResult<FileInfoDTO> getFileList(Integer page, Integer pageSize, String fileName) {
        int offset = (page - 1) * pageSize;
        Future<List<FileInfo>> fileInfosFuture = nioAsyncExecutor.submit(() -> fileInfoMapper.selectPage(offset, pageSize, fileName));
        Future<Long> totalFuture = nioAsyncExecutor.submit(() -> fileInfoMapper.selectCount(fileName));
        List<FileInfo> fileInfos = null;
        Long total = 0L;
        try {
            fileInfos = fileInfosFuture.get();
            total = totalFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("file list nio error", e);
            throw new RuntimeException(e);
        }
        List<FileInfoDTO> fileInfoDTOs = fileInfos.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return new PageResult<>(fileInfoDTOs, total, page, pageSize);
    }

    @Override
    public FileInfoDTO getFileById(Long id) {
        FileInfo fileInfo = fileInfoMapper.selectById(id);
        return fileInfo != null ? convertToDTO(fileInfo) : null;
    }

    @Override
    public boolean deleteFile(Long id) {
        FileInfo fileInfo = fileInfoMapper.selectById(id);
        if (fileInfo == null) {
            return false;
        }
        try {
            Path filePath = Paths.get(fileInfo.getFilePath());
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.error("delete actual file error: {}", fileInfo.getFilePath(), e);
        }
        return fileInfoMapper.deleteById(id) > 0;
    }

    @Override
    public void cleanExpiredFiles() {
        List<FileInfo> expiredFiles;
        try {
            expiredFiles = nioAsyncExecutor.submit(() -> fileInfoMapper.selectExpiredFiles()).get();
        } catch (Exception e) {
            log.error("clean expired nio files error", e);
            throw new RuntimeException(e);
        }
        for (FileInfo fileInfo : expiredFiles) {
            try {
                Path filePath = Paths.get(fileInfo.getFilePath());
                Files.deleteIfExists(filePath);
                fileInfoMapper.deleteById(fileInfo.getId());
                log.info("delete expired file: {}", fileInfo.getFileName());
            } catch (IOException e) {
                log.error("delete expired file error: {}", fileInfo.getFileName(), e);
            }
        }
    }

    @ControllerCommonAnnotation
    @Override
    public int deleteFilesByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (List<Long> subList : Lists.partition(ids, 500)) {
            // 批量查询有效文件
            List<FileInfo> fileInfos = fileInfoMapper.selectByIds(subList);
            List<Long> validIds = new ArrayList<>();

            // 删除物理文件
            for (FileInfo fileInfo : fileInfos) {
                try {
                    Path path = Paths.get(fileInfo.getFilePath());
                    Files.deleteIfExists(path);
                    validIds.add(fileInfo.getId());
                } catch (IOException e) {
                    log.error("delete actual file error: {}", fileInfo.getFilePath(), e);
                }
            }

            // 批量更新数据库状态
            if (!validIds.isEmpty()) {
                Future<Integer> future = nioAsyncExecutor.submit(() -> fileInfoMapper.batchDeleteByIds(validIds));
                try {
                    count += future.get();
                } catch (InterruptedException | ExecutionException e) {
                    log.error("delete files error", e);
                    throw new RuntimeException(e);
                }
            }
        }

        return count;
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new RuntimeException("file is empty");
        }
        if (file.getSize() > maxFileSize) {
            throw new RuntimeException("file size exceeds the limit");
        }
        String originalFilename = file.getOriginalFilename();
        if (!StringUtils.hasText(originalFilename)) {
            throw new RuntimeException("file is empty");
        }
        String fileExtension = getFileExtension(originalFilename);
        if (!isAllowedFileType(fileExtension)) {
            throw new RuntimeException("file type not supported");
        }
    }

    private String saveFile(MultipartFile file) throws IOException {
        Path uploadDir = Paths.get(uploadPath);
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }
        String originalFilename = file.getOriginalFilename();
        String fileName = generateFileName(originalFilename);
        Path filePath = uploadDir.resolve(fileName);
        Files.copy(file.getInputStream(), filePath);
        return fileName;
    }

    private String generateFileName(String originalFilename) {
        String extension = getFileExtension(originalFilename);
        String baseName = originalFilename.substring(0, originalFilename.lastIndexOf('.'));
        return baseName + "_" + System.currentTimeMillis() + "." + extension;
    }

    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        return lastDotIndex > 0 ? fileName.substring(lastDotIndex + 1).toLowerCase() : "";
    }

    private boolean isAllowedFileType(String fileExtension) {
        if (!StringUtils.hasText(allowedTypes)) {
            return true;
        }
        String[] allowedTypeArray = allowedTypes.split(",");
        for (String allowedType : allowedTypeArray) {
            if (allowedType.trim().equalsIgnoreCase(fileExtension)) {
                return true;
            }
        }
        return false;
    }

    private FileInfoDTO convertToDTO(FileInfo fileInfo) {
        FileInfoDTO dto = new FileInfoDTO();
        BeanUtils.copyProperties(fileInfo, dto);
        return dto;
    }
}

// 雪花算法实现
class SnowflakeIdWorker {
    private final long workerId;
    private final long datacenterId;
    private long sequence = 0L;
    private final long twepoch = 1288834974657L;
    private final long workerIdBits = 5L;
    private final long datacenterIdBits = 5L;
    private final long maxWorkerId = -1L ^ (-1L << workerIdBits);
    private final long maxDatacenterId = -1L ^ (-1L << datacenterIdBits);
    private final long sequenceBits = 12L;
    private final long workerIdShift = sequenceBits;
    private final long datacenterIdShift = sequenceBits + workerIdBits;
    private final long timestampLeftShift = sequenceBits + workerIdBits + datacenterIdBits;
    private final long sequenceMask = -1L ^ (-1L << sequenceBits);
    private long lastTimestamp = -1L;
    public SnowflakeIdWorker(long workerId, long datacenterId) {
        if (workerId > maxWorkerId || workerId < 0) {
            throw new IllegalArgumentException(String.format("worker Id can't be greater than %d or less than 0", maxWorkerId));
        }
        if (datacenterId > maxDatacenterId || datacenterId < 0) {
            throw new IllegalArgumentException(String.format("datacenter Id can't be greater than %d or less than 0", maxDatacenterId));
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
    }
    public synchronized long nextId() {
        long timestamp = timeGen();
        if (timestamp < lastTimestamp) {
            throw new RuntimeException(
                    String.format("Clock moved backwards.  Refusing to generate id for %d milliseconds", lastTimestamp - timestamp));
        }
        if (lastTimestamp == timestamp) {
            sequence = (sequence + 1) & sequenceMask;
            if (sequence == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - twepoch) << timestampLeftShift) |
                (datacenterId << datacenterIdShift) |
                (workerId << workerIdShift) |
                sequence;
    }
    private long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }
    private long timeGen() {
        return System.currentTimeMillis();
    }
}