package com.ming.imgateway.filter;

import com.ming.imgateway.config.GatewayAccessProperties;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 统一 traceId 解析与响应头写回。
 */
@Component
public class TraceWebFilter implements WebFilter {

    public static final String TRACE_ID_ATTR = "traceId";

    private final GatewayAccessProperties properties;

    public TraceWebFilter(GatewayAccessProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String traceId = resolveTraceId(exchange.getRequest());
        exchange.getAttributes().put(TRACE_ID_ATTR, traceId);
        if (StringUtils.hasText(traceId)) {
            exchange.getResponse().getHeaders().set(properties.getTraceHeaderName(), traceId);
        }
        return chain.filter(exchange);
    }

    private String resolveTraceId(ServerHttpRequest request) {
        String traceId = trim(request.getHeaders().getFirst(properties.getTraceHeaderName()));
        if (!StringUtils.hasText(traceId)) {
            traceId = parseTraceparent(request.getHeaders().getFirst("traceparent"));
        }
        if (!StringUtils.hasText(traceId) && properties.isGenerateTraceIdIfMissing()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        return traceId;
    }

    private String parseTraceparent(String traceparent) {
        String value = trim(traceparent);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String[] parts = value.split("-");
        if (parts.length >= 4 && parts[1].length() == 32) {
            return parts[1];
        }
        return null;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
