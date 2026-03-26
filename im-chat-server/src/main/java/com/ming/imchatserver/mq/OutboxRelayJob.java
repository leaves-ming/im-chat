package com.ming.imchatserver.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ming.imchatserver.config.ReliabilityProperties;
import com.ming.imchatserver.dao.OutboxMessageDO;
import com.ming.imchatserver.mapper.OutboxMapper;
import com.ming.imchatserver.metrics.MetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * Outbox relay: 扫描待发送事件并投递到 MQ。
 */
@Component
public class OutboxRelayJob {

    private static final Logger logger = LoggerFactory.getLogger(OutboxRelayJob.class);

    private final OutboxMapper outboxMapper;
    private final DispatchProducer dispatchProducer;
    private final ReliabilityProperties reliabilityProperties;
    private final MetricsService metricsService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OutboxRelayJob(OutboxMapper outboxMapper,
                          DispatchProducer dispatchProducer,
                          ReliabilityProperties reliabilityProperties,
                          MetricsService metricsService) {
        this.outboxMapper = outboxMapper;
        this.dispatchProducer = dispatchProducer;
        this.reliabilityProperties = reliabilityProperties;
        this.metricsService = metricsService;
    }

    @Scheduled(fixedDelayString = "${im.reliability.relay-fixed-delay-ms:1000}")
    public void relay() {
        List<OutboxMessageDO> batch = outboxMapper.findReadyBatch(new Date(), reliabilityProperties.getRelayBatchSize());
        if (batch.isEmpty()) {
            return;
        }

        for (OutboxMessageDO outbox : batch) {
            metricsService.incrementRelaySend();
            try {
                DispatchMessagePayload payload = objectMapper.readValue(outbox.getPayload(), DispatchMessagePayload.class);
                dispatchProducer.sendSingleDispatch(payload);
                outboxMapper.markSent(outbox.getId());
                logger.debug("outbox sent eventId={} serverMsgId={}", outbox.getEventId(), payload.getServerMsgId());
            } catch (Exception ex) {
                handleRetry(outbox, ex);
            }
        }
    }

    private void handleRetry(OutboxMessageDO outbox, Exception ex) {
        metricsService.incrementRelayFail();
        int nextRetryCount = (outbox.getRetryCount() == null ? 0 : outbox.getRetryCount()) + 1;
        boolean toDlq = nextRetryCount >= reliabilityProperties.getMaxRetryCount();
        int nextStatus = toDlq ? 3 : 2;
        Date nextRetryAt = toDlq ? new Date() : new Date(System.currentTimeMillis() + backoffMs(nextRetryCount));
        String reason = ex.getMessage();
        if (reason != null && reason.length() > 250) {
            reason = reason.substring(0, 250);
        }

        outboxMapper.markRetryOrDlq(outbox.getId(), nextStatus, nextRetryCount, nextRetryAt, reason);
        logger.warn("outbox send failed eventId={} retryCount={} status={} reason={}",
                outbox.getEventId(), nextRetryCount, nextStatus, reason);
    }

    private long backoffMs(int retryCount) {
        return switch (retryCount) {
            case 1 -> 1_000L;
            case 2 -> 5_000L;
            case 3 -> 30_000L;
            case 4 -> 120_000L;
            case 5 -> 600_000L;
            default -> 1_800_000L;
        };
    }
}
