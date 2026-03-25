package com.ming.imchatserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步事件线程池配置。
 * <p>
 * 为应用内事件监听（如消息推送监听器）提供独立线程池，避免阻塞 Netty I/O 线程。
 */
@Configuration
@EnableAsync
public class AsyncEventConfig {

    /**
     * 定义消息事件执行器。
     *
     * @return 供 {@code @Async("imEventExecutor")} 使用的线程池
     */
    @Bean(name = "imEventExecutor")
    public Executor imEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("im-event-");
        executor.initialize();
        return executor;
    }
}
