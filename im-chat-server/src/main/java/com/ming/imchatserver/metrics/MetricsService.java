package com.ming.imchatserver.metrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 轻量指标服务：提供计数器、定时采样和 ACK 延迟 p95 统计。
 */
@Service
public class MetricsService {

    private static final Logger logger = LoggerFactory.getLogger(MetricsService.class);

    private static final String ACK_TYPE_DELIVERED = "DELIVERED";
    private static final String ACK_TYPE_ACKED = "ACKED";

    private final AtomicLong outboxBacklog = new AtomicLong(0L);
    private final AtomicLong outboxProcessingBacklog = new AtomicLong(0L);
    private final AtomicLong relaySendTotal = new AtomicLong(0L);
    private final AtomicLong relayFailTotal = new AtomicLong(0L);
    private final AtomicLong groupPushAttemptTotal = new AtomicLong(0L);
    private final AtomicLong groupPushFailTotal = new AtomicLong(0L);
    private final AtomicLong groupPushRejectTotal = new AtomicLong(0L);
    private final AtomicLong sensitiveCheckTotal = new AtomicLong(0L);
    private final AtomicLong sensitiveHitTotal = new AtomicLong(0L);
    private final AtomicLong sensitiveReplaceTotal = new AtomicLong(0L);
    private final SlidingWindowLatency deliveredLatencyWindow = new SlidingWindowLatency(4096);
    private final SlidingWindowLatency ackedLatencyWindow = new SlidingWindowLatency(4096);

    public MetricsService() {
    }

    public void incrementRelaySend() {
        relaySendTotal.incrementAndGet();
    }

    public void incrementRelayFail() {
        relayFailTotal.incrementAndGet();
    }

    public void incrementGroupPushAttempt() {
        groupPushAttemptTotal.incrementAndGet();
    }

    public void incrementGroupPushAttempt(long delta) {
        if (delta > 0) {
            groupPushAttemptTotal.addAndGet(delta);
        }
    }

    public void incrementGroupPushFail() {
        groupPushFailTotal.incrementAndGet();
    }

    public void incrementGroupPushReject() {
        groupPushRejectTotal.incrementAndGet();
    }

    public void incrementSensitiveCheck() {
        sensitiveCheckTotal.incrementAndGet();
    }

    public void incrementSensitiveHit() {
        sensitiveHitTotal.incrementAndGet();
    }

    public void incrementSensitiveReplace() {
        sensitiveReplaceTotal.incrementAndGet();
    }

    /**
     * 记录状态推进延迟，延迟值单位毫秒。
     * delivered latency = delivered_at - created_at
     * acked latency = acked_at - created_at
     */
    public void observeAckLatency(String ackType, long createdAtMs, long ackAtMs) {
        if (ackType == null || ackAtMs < createdAtMs) {
            return;
        }
        long latencyMs = ackAtMs - createdAtMs;
        if (ACK_TYPE_ACKED.equalsIgnoreCase(ackType)) {
            ackedLatencyWindow.record(latencyMs);
            return;
        }
        if (ACK_TYPE_DELIVERED.equalsIgnoreCase(ackType)) {
            deliveredLatencyWindow.record(latencyMs);
        }
    }

    public MetricsSnapshot snapshot() {
        long sendTotal = relaySendTotal.get();
        long failTotal = relayFailTotal.get();
        double failRate = sendTotal <= 0 ? 0D : (double) failTotal / (double) sendTotal;
        long groupAttemptTotal = groupPushAttemptTotal.get();
        long groupFailTotal = groupPushFailTotal.get();
        long groupRejectTotal = groupPushRejectTotal.get();
        long checkTotal = sensitiveCheckTotal.get();
        long hitTotal = sensitiveHitTotal.get();
        long replaceTotal = sensitiveReplaceTotal.get();
        long deliveredP95 = deliveredLatencyWindow.p95();
        long ackedP95 = ackedLatencyWindow.p95();
        return new MetricsSnapshot(
                outboxBacklog.get(),
                outboxProcessingBacklog.get(),
                sendTotal,
                failTotal,
                failRate,
                groupAttemptTotal,
                groupFailTotal,
                groupRejectTotal,
                checkTotal,
                hitTotal,
                replaceTotal,
                deliveredP95,
                ackedP95
        );
    }

    /**
     * 定时采样 outbox backlog（status in NEW/FAILED/PROCESSING）。
     */
    @Scheduled(fixedDelayString = "${im.metrics.sample-fixed-delay-ms:10000}")
    public void sampleOutboxBacklog() {
        outboxBacklog.set(0L);
        outboxProcessingBacklog.set(0L);
    }

    /**
     * 周期输出关键指标，作为无监控系统时的最小观测面。
     */
    @Scheduled(fixedDelayString = "${im.metrics.log-fixed-delay-ms:60000}")
    public void logMetrics() {
        MetricsSnapshot snapshot = snapshot();
        logger.info("metrics im_outbox_backlog={} im_outbox_processing_backlog={} im_relay_send_total={} im_relay_fail_total={} im_relay_fail_rate={} im_group_push_attempt_total={} im_group_push_fail_total={} im_group_push_reject_total={} im_sensitive_check_total={} im_sensitive_hit_total={} im_sensitive_replace_total={} im_ack_latency_ms{{type=DELIVERED,p95={}}} im_ack_latency_ms{{type=ACKED,p95={}}}",
                snapshot.getImOutboxBacklog(),
                snapshot.getImOutboxProcessingBacklog(),
                snapshot.getImRelaySendTotal(),
                snapshot.getImRelayFailTotal(),
                snapshot.getImRelayFailRate(),
                snapshot.getImGroupPushAttemptTotal(),
                snapshot.getImGroupPushFailTotal(),
                snapshot.getImGroupPushRejectTotal(),
                snapshot.getImSensitiveCheckTotal(),
                snapshot.getImSensitiveHitTotal(),
                snapshot.getImSensitiveReplaceTotal(),
                snapshot.getImAckLatencyDeliveredP95Ms(),
                snapshot.getImAckLatencyAckedP95Ms());
    }

    /**
     * 对外最小查询结构。
     */
    public static class MetricsSnapshot {
        private final long imOutboxBacklog;
        private final long imOutboxProcessingBacklog;
        private final long imRelaySendTotal;
        private final long imRelayFailTotal;
        private final double imRelayFailRate;
        private final long imGroupPushAttemptTotal;
        private final long imGroupPushFailTotal;
        private final long imGroupPushRejectTotal;
        private final long imSensitiveCheckTotal;
        private final long imSensitiveHitTotal;
        private final long imSensitiveReplaceTotal;
        private final long imAckLatencyDeliveredP95Ms;
        private final long imAckLatencyAckedP95Ms;

        public MetricsSnapshot(long imOutboxBacklog,
                               long imOutboxProcessingBacklog,
                               long imRelaySendTotal,
                               long imRelayFailTotal,
                               double imRelayFailRate,
                               long imGroupPushAttemptTotal,
                               long imGroupPushFailTotal,
                               long imGroupPushRejectTotal,
                               long imSensitiveCheckTotal,
                               long imSensitiveHitTotal,
                               long imSensitiveReplaceTotal,
                               long imAckLatencyDeliveredP95Ms,
                               long imAckLatencyAckedP95Ms) {
            this.imOutboxBacklog = imOutboxBacklog;
            this.imOutboxProcessingBacklog = imOutboxProcessingBacklog;
            this.imRelaySendTotal = imRelaySendTotal;
            this.imRelayFailTotal = imRelayFailTotal;
            this.imRelayFailRate = imRelayFailRate;
            this.imGroupPushAttemptTotal = imGroupPushAttemptTotal;
            this.imGroupPushFailTotal = imGroupPushFailTotal;
            this.imGroupPushRejectTotal = imGroupPushRejectTotal;
            this.imSensitiveCheckTotal = imSensitiveCheckTotal;
            this.imSensitiveHitTotal = imSensitiveHitTotal;
            this.imSensitiveReplaceTotal = imSensitiveReplaceTotal;
            this.imAckLatencyDeliveredP95Ms = imAckLatencyDeliveredP95Ms;
            this.imAckLatencyAckedP95Ms = imAckLatencyAckedP95Ms;
        }

        public long getImOutboxBacklog() {
            return imOutboxBacklog;
        }

        public long getImOutboxProcessingBacklog() {
            return imOutboxProcessingBacklog;
        }

        public long getImRelaySendTotal() {
            return imRelaySendTotal;
        }

        public long getImRelayFailTotal() {
            return imRelayFailTotal;
        }

        public double getImRelayFailRate() {
            return imRelayFailRate;
        }

        public long getImGroupPushAttemptTotal() {
            return imGroupPushAttemptTotal;
        }

        public long getImGroupPushFailTotal() {
            return imGroupPushFailTotal;
        }

        public long getImGroupPushRejectTotal() {
            return imGroupPushRejectTotal;
        }

        public long getImSensitiveCheckTotal() {
            return imSensitiveCheckTotal;
        }

        public long getImSensitiveHitTotal() {
            return imSensitiveHitTotal;
        }

        public long getImSensitiveReplaceTotal() {
            return imSensitiveReplaceTotal;
        }

        public long getImAckLatencyDeliveredP95Ms() {
            return imAckLatencyDeliveredP95Ms;
        }

        public long getImAckLatencyAckedP95Ms() {
            return imAckLatencyAckedP95Ms;
        }

        public Map<String, Object> asMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("im_outbox_backlog", imOutboxBacklog);
            m.put("im_outbox_processing_backlog", imOutboxProcessingBacklog);
            m.put("im_relay_send_total", imRelaySendTotal);
            m.put("im_relay_fail_total", imRelayFailTotal);
            m.put("im_relay_fail_rate", imRelayFailRate);
            m.put("im_group_push_attempt_total", imGroupPushAttemptTotal);
            m.put("im_group_push_fail_total", imGroupPushFailTotal);
            m.put("im_group_push_reject_total", imGroupPushRejectTotal);
            m.put("im_sensitive_check_total", imSensitiveCheckTotal);
            m.put("im_sensitive_hit_total", imSensitiveHitTotal);
            m.put("im_sensitive_replace_total", imSensitiveReplaceTotal);
            m.put("im_ack_latency_ms", Map.of(
                    "delivered_p95", imAckLatencyDeliveredP95Ms,
                    "acked_p95", imAckLatencyAckedP95Ms
            ));
            return m;
        }
    }

    /**
     * 固定容量滑动窗口，用于近似延迟分布统计。
     */
    private static class SlidingWindowLatency {
        private final long[] samples;
        private int cursor;
        private int size;

        SlidingWindowLatency(int capacity) {
            this.samples = new long[capacity];
            this.cursor = 0;
            this.size = 0;
        }

        synchronized void record(long latencyMs) {
            if (latencyMs < 0) {
                return;
            }
            samples[cursor] = latencyMs;
            cursor = (cursor + 1) % samples.length;
            if (size < samples.length) {
                size++;
            }
        }

        synchronized long p95() {
            if (size == 0) {
                return 0L;
            }
            long[] copy = Arrays.copyOf(samples, size);
            Arrays.sort(copy);
            int idx = (int) Math.ceil(size * 0.95D) - 1;
            if (idx < 0) {
                idx = 0;
            }
            return copy[idx];
        }
    }
}
