package com.ming.imchatserver.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 最小单机配置。
 */
@Configuration
@ConditionalOnProperty(name = "im.redisson.enabled", havingValue = "true", matchIfMissing = true)
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient redissonClient(@Value("${redisson.single-server-config.address:redis://127.0.0.1:6379}") String address,
                                         @Value("${redisson.single-server-config.password:}") String password) {
        Config config = new Config();
        config.useSingleServer().setAddress(address);
        if (password != null && !password.isBlank()) {
            config.useSingleServer().setPassword(password);
        }
        return Redisson.create(config);
    }
}
