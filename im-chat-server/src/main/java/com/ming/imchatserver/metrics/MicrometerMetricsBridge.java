package com.ming.imchatserver.metrics;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 复用现有 MetricsService 并桥接到 Micrometer。
 */
@Component
public class MicrometerMetricsBridge {

    public MicrometerMetricsBridge(MeterRegistry registry, MetricsService metricsService) {
        Gauge.builder("im.outbox.backlog", metricsService, value -> value.snapshot().getImOutboxBacklog())
                .description("待投递 outbox 堆积量")
                .register(registry);
        Gauge.builder("im.outbox.processing.backlog", metricsService, value -> value.snapshot().getImOutboxProcessingBacklog())
                .description("处理中 outbox 堆积量")
                .register(registry);
        Gauge.builder("im.ack.latency.p95", metricsService, value -> value.snapshot().getImAckLatencyDeliveredP95Ms())
                .tag("type", "DELIVERED")
                .description("消息投递延迟 P95 毫秒")
                .register(registry);
        Gauge.builder("im.ack.latency.p95", metricsService, value -> value.snapshot().getImAckLatencyAckedP95Ms())
                .tag("type", "ACKED")
                .description("消息 ACK 延迟 P95 毫秒")
                .register(registry);
        FunctionCounter.builder("im.relay.send.total", metricsService, value -> value.snapshot().getImRelaySendTotal())
                .description("消息投递发送总数")
                .register(registry);
        FunctionCounter.builder("im.relay.fail.total", metricsService, value -> value.snapshot().getImRelayFailTotal())
                .description("消息投递失败总数")
                .register(registry);
        FunctionCounter.builder("im.group.push.attempt.total", metricsService, value -> value.snapshot().getImGroupPushAttemptTotal())
                .description("群推送尝试总数")
                .register(registry);
        FunctionCounter.builder("im.group.push.fail.total", metricsService, value -> value.snapshot().getImGroupPushFailTotal())
                .description("群推送失败总数")
                .register(registry);
        FunctionCounter.builder("im.group.push.reject.total", metricsService, value -> value.snapshot().getImGroupPushRejectTotal())
                .description("群推送拒绝总数")
                .register(registry);
        FunctionCounter.builder("im.sensitive.check.total", metricsService, value -> value.snapshot().getImSensitiveCheckTotal())
                .description("敏感词检测总数")
                .register(registry);
        FunctionCounter.builder("im.sensitive.hit.total", metricsService, value -> value.snapshot().getImSensitiveHitTotal())
                .description("敏感词命中总数")
                .register(registry);
        FunctionCounter.builder("im.sensitive.replace.total", metricsService, value -> value.snapshot().getImSensitiveReplaceTotal())
                .description("敏感词替换总数")
                .register(registry);
    }
}
