package com.mini.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 文件信息实体类
 */
@Data
public class FileInfo {
    private Long id;
    private String fileName;
    private String filePath;
    private Integer fileSize;
    private Integer status;
    private LocalDateTime uploadDate;
} 