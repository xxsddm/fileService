package com.mini.config;

import com.mini.service.FileService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import jakarta.annotation.Resource;

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
        log.info("delete invalid files start");
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
            log.info("delete invalid files finish, nDelete =  {}", count);
        } catch (Exception e) {
            log.error("delete invalid files error", e);
        }
    }

    /**
     * 每天凌晨2点清理过期文件
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanExpiredFiles() {
        log.info("delete expired files start");
        try {
            fileService.cleanExpiredFiles();
            log.info("delete expired files finish");
        } catch (Exception e) {
            log.error("delete expired files error", e);
        }
    }
}