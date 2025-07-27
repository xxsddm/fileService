package com.mini.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.LinkedBlockingDeque;

@Configuration
public class BeanConfig {

    // 待删除路径队列
    @Bean(name = "invalidFileIdQueue")
    public LinkedBlockingDeque<Long> buildInvalidFileIdQueue() {
        return new LinkedBlockingDeque<Long>(10000);
    }
}