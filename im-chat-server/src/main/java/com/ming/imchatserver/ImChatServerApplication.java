package com.ming.imchatserver;

import com.ming.imchatserver.config.CacheProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;

import java.util.HashMap;
import java.util.Map;

/**
 * IM 聊天服务启动入口。
 * <p>
 * 负责引导 Spring Boot 容器启动，并触发后续 Netty 组件初始化。
 */
@SpringBootApplication(excludeName = {
        "org.redisson.spring.starter.RedissonAutoConfigurationV2"
})
@EnableFeignClients
@EnableConfigurationProperties(CacheProperties.class)
public class ImChatServerApplication {

    /**
     * 应用主函数。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(ImChatServerApplication.class);
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("im.instance.startup-time", Long.toString(System.currentTimeMillis()));
        application.setDefaultProperties(defaults);
        application.run(args);
    }
}
