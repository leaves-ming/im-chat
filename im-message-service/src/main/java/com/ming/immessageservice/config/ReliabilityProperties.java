package com.ming.immessageservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * outbox 可靠投递配置。
 */
@Component
@ConfigurationProperties(prefix = "im.reliability")
@Getter
@Setter
public class ReliabilityProperties {

    private int relayBatchSize = 100;
    private long relayFixedDelayMs = 1000L;
    private long processingTimeoutMs = 30000L;
    private int maxRetryCount = 8;
    private String dispatchTopic = "im.msg.dispatch";
}
