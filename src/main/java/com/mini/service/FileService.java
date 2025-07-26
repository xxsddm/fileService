package com.mini.service;

import com.mini.dto.FileInfoDTO;
import com.mini.dto.PageResult;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

/**
 * 文件服务接口
 */
public interface FileService {
    
    /**
     * 上传单个文件
     */
    FileInfoDTO uploadFile(MultipartFile file);
    
    /**
     * 批量上传文件
     */
    List<FileInfoDTO> uploadFiles(List<MultipartFile> files);
    
    /**
     * 下载文件
     */
    byte[] downloadFile(String fileName);
    
    /**
     * 分页查询文件列表
     */
    PageResult<FileInfoDTO> getFileList(Integer page, Integer pageSize, String fileName);
    
    /**
     * 根据ID查询文件信息
     */
    FileInfoDTO getFileById(Long id);
    
    /**
     * 删除文件
     */
    boolean deleteFile(Long id);
    
    /**
     * 清理过期文件
     */
    void cleanExpiredFiles();
} 