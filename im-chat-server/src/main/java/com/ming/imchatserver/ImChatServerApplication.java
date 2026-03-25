package com.ming.imchatserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * IM 聊天服务启动入口。
 * <p>
 * 负责引导 Spring Boot 容器启动，并触发后续 Netty 组件初始化。
 */
@SpringBootApplication
public class ImChatServerApplication {

    /**
     * 应用主函数。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(ImChatServerApplication.class, args);
    }
}
