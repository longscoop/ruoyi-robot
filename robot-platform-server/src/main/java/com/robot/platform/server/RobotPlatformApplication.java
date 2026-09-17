package com.robot.platform.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"cn.iocoder.yudao", "com.robot.platform"})
public class RobotPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(RobotPlatformApplication.class, args);
    }
}
