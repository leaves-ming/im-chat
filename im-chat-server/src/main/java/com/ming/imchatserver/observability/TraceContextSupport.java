package com.ming.imchatserver.observability;

import com.ming.imchatserver.netty.NettyAttr;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpHeaders;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * TraceId 解析与 MDC 绑定工具。
 */
public final class TraceContextSupport {

    private static final String TRACEPARENT = "traceparent";

    private TraceContextSupport() {
    }

    public static String resolveHttpTraceId(HttpHeaders headers, RuntimeObservabilitySettings settings) {
        String headerName = settings.getTraceHeaderName();
        String traceId = trim(headers.get(headerName));
        if (traceId == null) {
            traceId = parseTraceparent(headers.get(TRACEPARENT));
        }
        if (traceId == null && settings.isTraceGenerateIfMissing()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        return traceId;
    }

    public static String ensureChannelTraceId(Channel channel, String candidateTraceId, RuntimeObservabilitySettings settings) {
        String traceId = trim(candidateTraceId);
        if (traceId == null) {
            traceId = trim(channel.attr(NettyAttr.TRACE_ID).get());
        }
        if (traceId == null && settings.isTraceGenerateIfMissing()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        if (traceId != null) {
            channel.attr(NettyAttr.TRACE_ID).set(traceId);
        }
        return traceId;
    }

    public static void putMdc(String traceId) {
        if (traceId != null && !traceId.isBlank()) {
            MDC.put("traceId", traceId);
        }
    }

    public static void clearMdc() {
        MDC.remove("traceId");
    }

    public static String currentTraceId(Channel channel) {
        return channel.attr(NettyAttr.TRACE_ID).get();
    }

    private static String parseTraceparent(String traceparent) {
        String value = trim(traceparent);
        if (value == null) {
            return null;
        }
        String[] parts = value.split("-");
        if (parts.length >= 4 && parts[1].length() == 32) {
            return parts[1];
        }
        return null;
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
