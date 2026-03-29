package com.quant.monitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling; // 引入排程

@SpringBootApplication
@EnableScheduling // 【關鍵支撐】開啟 Spring Boot 背景定時任務功能
public class StockTickMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockTickMonitorApplication.class, args);
    }
}