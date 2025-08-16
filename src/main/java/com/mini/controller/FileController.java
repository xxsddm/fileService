package com.mini.controller;

import com.mini.dto.FileInfoDTO;
import com.mini.dto.PageResult;
import com.mini.service.FileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * 文件控制器
 */
@Slf4j
@RestController
public class FileController {

    @Resource
    private FileService fileService;

    /**
     * 批量上传文件，兼容前端index.html
     */
    @PostMapping("/upload/")
    public ResponseEntity<?> uploadFiles(@RequestParam("files") List<MultipartFile> files) {
        try {
            List<FileInfoDTO> fileInfos = fileService.uploadFiles(files);
            return ResponseEntity.ok().body(new ResultMsg("upload success", fileInfos));
        } catch (Exception e) {
            log.error("upload files failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ResultMsg("upload failed: " + e.getMessage(), null));
        }
    }

    /**
     * 下载文件，兼容前端index.html
     */
    @GetMapping("/download/{fileName}")
    public ResponseEntity<byte[]> downloadFile(@PathVariable String fileName) {
        try {
            byte[] fileData = fileService.downloadFile(fileName);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString()));
            return ResponseEntity.ok().headers(headers).body(fileData);
        } catch (Exception e) {
            log.error("download file failed: {}", fileName, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * 分页查询文件列表，兼容前端index.html
     */
    @GetMapping("/files/")
    public ResponseEntity<?> getFileList(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "page_size", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "filename_filter", required = false) String fileName) {
        try {
            PageResult<FileInfoDTO> result = fileService.getFileList(page, pageSize, fileName);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("query file list failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ResultMsg("query file list failed: " + e.getMessage(), null));
        }
    }

    /**
     * 兼容前端的返回结构
     */
    static class ResultMsg {
        public String message;
        public Object files;
        public ResultMsg(String message, Object files) {
            this.message = message;
            this.files = files;
        }
    }
}