package com.ming.imchatserver.metrics;

import com.ming.imchatserver.mapper.OutboxMapper;
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
    private static final String ACK_TYPE_READ = "READ";

    private final OutboxMapper outboxMapper;
    private final AtomicLong outboxBacklog = new AtomicLong(0L);
    private final AtomicLong relaySendTotal = new AtomicLong(0L);
    private final AtomicLong relayFailTotal = new AtomicLong(0L);
    private final SlidingWindowLatency deliveredLatencyWindow = new SlidingWindowLatency(4096);
    private final SlidingWindowLatency readLatencyWindow = new SlidingWindowLatency(4096);

    public MetricsService(OutboxMapper outboxMapper) {
        this.outboxMapper = outboxMapper;
    }

    public void incrementRelaySend() {
        relaySendTotal.incrementAndGet();
    }

    public void incrementRelayFail() {
        relayFailTotal.incrementAndGet();
    }

    /**
     * 记录 ACK 延迟，延迟值单位毫秒。
     * delivered latency = delivered_at - created_at
     * read latency = read_at - created_at
     */
    public void observeAckLatency(String ackType, long createdAtMs, long ackAtMs) {
        if (ackType == null || ackAtMs < createdAtMs) {
            return;
        }
        long latencyMs = ackAtMs - createdAtMs;
        if (ACK_TYPE_READ.equalsIgnoreCase(ackType)) {
            readLatencyWindow.record(latencyMs);
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
        long deliveredP95 = deliveredLatencyWindow.p95();
        long readP95 = readLatencyWindow.p95();
        return new MetricsSnapshot(
                outboxBacklog.get(),
                sendTotal,
                failTotal,
                failRate,
                deliveredP95,
                readP95
        );
    }

    /**
     * 定时采样 outbox backlog（status in NEW/FAILED）。
     */
    @Scheduled(fixedDelayString = "${im.metrics.sample-fixed-delay-ms:10000}")
    public void sampleOutboxBacklog() {
        try {
            long count = outboxMapper.countBacklog();
            outboxBacklog.set(count);
        } catch (Exception ex) {
            logger.warn("sample outbox backlog failed", ex);
        }
    }

    /**
     * 周期输出关键指标，作为无监控系统时的最小观测面。
     */
    @Scheduled(fixedDelayString = "${im.metrics.log-fixed-delay-ms:60000}")
    public void logMetrics() {
        MetricsSnapshot snapshot = snapshot();
        logger.info("metrics im_outbox_backlog={} im_relay_send_total={} im_relay_fail_total={} im_relay_fail_rate={} im_ack_latency_ms{{type=DELIVERED,p95={}}} im_ack_latency_ms{{type=READ,p95={}}}",
                snapshot.getImOutboxBacklog(),
                snapshot.getImRelaySendTotal(),
                snapshot.getImRelayFailTotal(),
                snapshot.getImRelayFailRate(),
                snapshot.getImAckLatencyDeliveredP95Ms(),
                snapshot.getImAckLatencyReadP95Ms());
    }

    /**
     * 对外最小查询结构。
     */
    public static class MetricsSnapshot {
        private final long imOutboxBacklog;
        private final long imRelaySendTotal;
        private final long imRelayFailTotal;
        private final double imRelayFailRate;
        private final long imAckLatencyDeliveredP95Ms;
        private final long imAckLatencyReadP95Ms;

        public MetricsSnapshot(long imOutboxBacklog,
                               long imRelaySendTotal,
                               long imRelayFailTotal,
                               double imRelayFailRate,
                               long imAckLatencyDeliveredP95Ms,
                               long imAckLatencyReadP95Ms) {
            this.imOutboxBacklog = imOutboxBacklog;
            this.imRelaySendTotal = imRelaySendTotal;
            this.imRelayFailTotal = imRelayFailTotal;
            this.imRelayFailRate = imRelayFailRate;
            this.imAckLatencyDeliveredP95Ms = imAckLatencyDeliveredP95Ms;
            this.imAckLatencyReadP95Ms = imAckLatencyReadP95Ms;
        }

        public long getImOutboxBacklog() {
            return imOutboxBacklog;
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

        public long getImAckLatencyDeliveredP95Ms() {
            return imAckLatencyDeliveredP95Ms;
        }

        public long getImAckLatencyReadP95Ms() {
            return imAckLatencyReadP95Ms;
        }

        public Map<String, Object> asMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("im_outbox_backlog", imOutboxBacklog);
            m.put("im_relay_send_total", imRelaySendTotal);
            m.put("im_relay_fail_total", imRelayFailTotal);
            m.put("im_relay_fail_rate", imRelayFailRate);
            m.put("im_ack_latency_ms", Map.of(
                    "delivered_p95", imAckLatencyDeliveredP95Ms,
                    "read_p95", imAckLatencyReadP95Ms
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
