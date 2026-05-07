package com.ming.imchatserver.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.concurrent.TimeUnit;

/**
 * Caffeine本地缓存配置
 */
@Data
@ConfigurationProperties(prefix = "im.cache")
public class CacheProperties {

    private CacheConfig contactActive = new CacheConfig(10000, 5, TimeUnit.MINUTES);
    private CacheConfig singleChatPermission = new CacheConfig(5000, 10, TimeUnit.MINUTES);
    private CacheConfig groupMemberIds = new CacheConfig(2000, 3, TimeUnit.MINUTES);
    private CacheConfig groupRecallPermission = new CacheConfig(2000, 10, TimeUnit.MINUTES);

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CacheConfig {
        /**
         * 缓存最大容量
         */
        private int maxSize;
        /**
         * 写入后过期时间
         */
        private long expire;
        /**
         * 过期时间单位
         */
        private TimeUnit timeUnit;
    }
}
