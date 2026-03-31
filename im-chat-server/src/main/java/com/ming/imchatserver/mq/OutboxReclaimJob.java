package com.ming.imchatserver.mq;

import com.ming.imchatserver.config.ReliabilityProperties;
import com.ming.imchatserver.mapper.OutboxMapper;
import com.ming.imchatserver.service.DistributedCoordinationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.time.Duration;

/**
 * 回收超时 PROCESSING 的 outbox，避免实例宕机后永久卡死。
 * <p>
 * 兼容历史脏数据：
 * - processing_at 有值：按 processing_at 超时回收
 * - processing_at 为空：按 updated_at 超时回收
 */
@Component
public class OutboxReclaimJob {

    private static final Logger logger = LoggerFactory.getLogger(OutboxReclaimJob.class);

    private final OutboxMapper outboxMapper;
    private final ReliabilityProperties reliabilityProperties;
    private final DistributedCoordinationService distributedCoordinationService;

    public OutboxReclaimJob(OutboxMapper outboxMapper,
                            ReliabilityProperties reliabilityProperties,
                            DistributedCoordinationService distributedCoordinationService) {
        this.outboxMapper = outboxMapper;
        this.reliabilityProperties = reliabilityProperties;
        this.distributedCoordinationService = distributedCoordinationService;
    }

    @Scheduled(fixedDelayString = "${im.reliability.relay-fixed-delay-ms:1000}")
    public void reclaimTimeoutProcessing() {
        if (distributedCoordinationService != null) {
            boolean executed = distributedCoordinationService.executeIfLeader(
                    "job",
                    "outbox_reclaim",
                    Duration.ZERO,
                    Duration.ofSeconds(30),
                    this::doReclaimTimeoutProcessing);
            if (!executed) {
                logger.warn("skip outbox reclaim on this instance because distributed lock was not acquired");
            }
            return;
        }
        doReclaimTimeoutProcessing();
    }

    private void doReclaimTimeoutProcessing() {
        long timeoutMs = Math.max(1000L, reliabilityProperties.getProcessingTimeoutMs());
        Date now = new Date();
        Date timeoutBefore = new Date(now.getTime() - timeoutMs);
        int reclaimed = outboxMapper.reclaimTimeoutProcessing(timeoutBefore, now);
        if (reclaimed > 0) {
            logger.warn("reclaimed timeout outbox processing messages count={} timeoutMs={}", reclaimed, timeoutMs);
        }
    }
}
