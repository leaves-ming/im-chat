package com.ming.immessageservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 实例状态配置。
 */
@Component
@ConfigurationProperties(prefix = "im.redis")
@Getter
@Setter
public class RedisStateProperties {

    private String serverId = "im-message-service";
}
