package com.ming.imsocialservice.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Redis 使用开关占位配置。
 */
@Configuration
@ConditionalOnProperty(prefix = "im.social-service", name = "redis-enabled", havingValue = "true", matchIfMissing = true)
public class RedisUsageConfig {
}
