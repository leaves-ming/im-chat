package com.ming.imgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.HashMap;
import java.util.Map;

/**
 * gateway 启动入口。
 */
@SpringBootApplication
public class ImGatewayApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(ImGatewayApplication.class);
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("im.instance.startup-time", Long.toString(System.currentTimeMillis()));
        application.setDefaultProperties(defaults);
        application.run(args);
    }
}
