package com.ming.immessageservice.mq;

import com.ming.immessageservice.config.ReliabilityProperties;
import com.ming.immessageservice.infrastructure.mapper.OutboxMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 回收超时 outbox。
 */
@Component
public class OutboxReclaimJob {

    private static final Logger logger = LoggerFactory.getLogger(OutboxReclaimJob.class);

    private final OutboxMapper outboxMapper;
    private final ReliabilityProperties reliabilityProperties;

    public OutboxReclaimJob(OutboxMapper outboxMapper,
                            ReliabilityProperties reliabilityProperties) {
        this.outboxMapper = outboxMapper;
        this.reliabilityProperties = reliabilityProperties;
    }

    @Scheduled(fixedDelayString = "${im.reliability.relay-fixed-delay-ms:1000}")
    public void reclaimTimeoutProcessing() {
        long timeoutMs = Math.max(1000L, reliabilityProperties.getProcessingTimeoutMs());
        Date now = new Date();
        Date timeoutBefore = new Date(now.getTime() - timeoutMs);
        int reclaimed = outboxMapper.reclaimTimeoutProcessing(timeoutBefore, now);
        if (reclaimed > 0) {
            logger.warn("reclaimed timeout outbox processing messages count={} timeoutMs={}", reclaimed, timeoutMs);
        }
    }
}
