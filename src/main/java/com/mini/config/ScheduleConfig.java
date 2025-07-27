package com.mini.config;

import com.mini.service.FileService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import jakarta.annotation.Resource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * 定时任务配置
 */
@Slf4j
@Configuration
@EnableScheduling
public class ScheduleConfig {

    @Resource
    private FileService fileService;

    @Resource(name = "invalidFileIdQueue")
    private LinkedBlockingDeque<Long> invalidFileIdQueue;

    @PostConstruct
    public void init() {
        cleanExpiredFiles();
    }

    /**
     * 使用fixedDelay属性，单位是毫秒（5分钟=300000毫秒）
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void cleanInvalidFiles() {
        if (invalidFileIdQueue.isEmpty()) {
            log.info("no invalid files");
            return;
        }
        log.info("开始清理失效文件...");
        List<Long> idList = new ArrayList<>(invalidFileIdQueue.size());
        while (!invalidFileIdQueue.isEmpty()) {
            Long fileId = invalidFileIdQueue.poll();
            if (Objects.isNull(fileId)) {
                continue;
            }
            idList.add(fileId);
        }
        try {
            int count = fileService.deleteFilesByIds(idList);
            log.info("失效文件清理完成, 清理文件 {} 个", count);
        } catch (Exception e) {
            log.error("失效过期文件失败", e);
        }
    }

    /**
     * 每天凌晨2点清理过期文件
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanExpiredFiles() {
        log.info("开始清理过期文件...");
        try {
            fileService.cleanExpiredFiles();
            log.info("过期文件清理完成");
        } catch (Exception e) {
            log.error("清理过期文件失败", e);
        }
    }
}