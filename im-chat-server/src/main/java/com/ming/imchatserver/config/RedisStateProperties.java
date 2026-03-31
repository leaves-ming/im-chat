package com.ming.imchatserver.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Redis 跨实例状态配置。
 */
@Component
@ConfigurationProperties(prefix = "im.redis")
@Getter
@Setter
public class RedisStateProperties {
    /** 当前实例 ID，用于跨机在线端记录与协调锁 owner 标识。 */
    private String serverId = "im-chat-server";
    /** 在线端信息 TTL（秒）。 */
    private long presenceTtlSeconds = 180L;
    /** clientMsgId 短期幂等窗口（秒）。 */
    private long clientMsgIdTtlSeconds = 600L;
}

