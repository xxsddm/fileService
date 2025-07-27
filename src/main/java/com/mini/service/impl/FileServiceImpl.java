package com.mini.service.impl;

import com.google.common.collect.Lists;
import com.mini.dto.FileInfoDTO;
import com.mini.dto.PageResult;
import com.mini.entity.FileInfo;
import com.mini.mapper.FileInfoMapper;
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

    // 雪花算法生成id
    private static final SnowflakeIdWorker idWorker = new SnowflakeIdWorker(1, 1);

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
            fileInfoMapper.insert(fileInfo);
            return convertToDTO(fileInfo);
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new RuntimeException("文件上传失败: " + e.getMessage());
        }
    }

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
                log.error("文件上传失败: {}", file.getOriginalFilename(), e);
            }
        }
        if (!fileInfos.isEmpty()) {
            fileInfoMapper.batchInsert(fileInfos);
        }
        return results;
    }

    @Override
    public byte[] downloadFile(String fileName) {
        try {
            FileInfo fileInfo = fileInfoMapper.selectByFileName(fileName);
            if (fileInfo == null) {
                throw new RuntimeException("文件不存在");
            }
            Path filePath = Paths.get(fileInfo.getFilePath());
            if (!Files.exists(filePath)) {
                if (Objects.nonNull(fileInfo.getId())) {
                    invalidFileIdQueue.offer(fileInfo.getId());
                }
                throw new RuntimeException("文件不存在");
            }
            return Files.readAllBytes(filePath);
        } catch (IOException e) {
            log.error("文件下载失败: {}", fileName, e);
            throw new RuntimeException("文件下载失败");
        }
    }

    @Override
    public PageResult<FileInfoDTO> getFileList(Integer page, Integer pageSize, String fileName) {
        int offset = (page - 1) * pageSize;
        List<FileInfo> fileInfos = fileInfoMapper.selectPage(offset, pageSize, fileName);
        Long total = fileInfoMapper.selectCount(fileName);
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
            log.error("删除物理文件失败: {}", fileInfo.getFilePath(), e);
        }
        return fileInfoMapper.deleteById(id) > 0;
    }

    @Override
    public void cleanExpiredFiles() {
        List<FileInfo> expiredFiles = fileInfoMapper.selectExpiredFiles();
        for (FileInfo fileInfo : expiredFiles) {
            try {
                Path filePath = Paths.get(fileInfo.getFilePath());
                Files.deleteIfExists(filePath);
                fileInfoMapper.deleteById(fileInfo.getId());
                log.info("清理过期文件: {}", fileInfo.getFileName());
            } catch (IOException e) {
                log.error("清理过期文件失败: {}", fileInfo.getFileName(), e);
            }
        }
    }

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
                    log.error("物理文件删除失败: {}", fileInfo.getFilePath(), e);
                }
            }

            // 批量更新数据库状态
            if (!validIds.isEmpty()) {
                count += fileInfoMapper.batchDeleteByIds(validIds);
            }
        }

        return count;
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new RuntimeException("文件不能为空");
        }
        if (file.getSize() > maxFileSize) {
            throw new RuntimeException("文件大小超过限制");
        }
        String originalFilename = file.getOriginalFilename();
        if (!StringUtils.hasText(originalFilename)) {
            throw new RuntimeException("文件名不能为空");
        }
        String fileExtension = getFileExtension(originalFilename);
        if (!isAllowedFileType(fileExtension)) {
            throw new RuntimeException("不支持的文件类型");
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