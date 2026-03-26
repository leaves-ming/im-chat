package com.ming.imchatserver.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 可靠性与 MQ 分发配置。
 */
@Component
@ConfigurationProperties(prefix = "im.reliability")
@Getter
@Setter
public class ReliabilityProperties {
    /** Relay 扫描批次大小。 */
    private int relayBatchSize = 100;
    /** Relay 固定延迟（毫秒）。 */
    private long relayFixedDelayMs = 1000L;
    /** 最大重试次数，超过进入 DLQ。 */
    private int maxRetryCount = 8;
    /** 分发 topic。 */
    private String dispatchTopic = "im.msg.dispatch";
}
