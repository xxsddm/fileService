package com.mini.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 文件信息DTO
 */
@Data
public class FileInfoDTO {
    private Long id;
    private String fileName;
    private String filePath;
    private Integer fileSize;
    private Integer status;
    private LocalDateTime uploadDate;
} 