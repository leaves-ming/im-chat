package com.ming.imsocialservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

import java.util.HashMap;
import java.util.Map;

/**
 * 关系域独立服务启动入口。
 */
@EnableFeignClients
@SpringBootApplication
public class ImSocialServiceApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(ImSocialServiceApplication.class);
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("im.instance.startup-time", Long.toString(System.currentTimeMillis()));
        application.setDefaultProperties(defaults);
        application.run(args);
    }
}
