package com.mini.config;

import com.mini.service.FileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import jakarta.annotation.Resource;

/**
 * 定时任务配置
 */
@Slf4j
@Configuration
@EnableScheduling
public class ScheduleConfig {

    @Resource
    private FileService fileService;

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