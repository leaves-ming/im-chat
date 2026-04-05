package com.ming.imgateway.filter;

import com.ming.imgateway.config.GatewayAccessProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * gateway 统一 access log。
 */
@Component
public class AccessLogWebFilter implements WebFilter {

    private static final Logger logger = LoggerFactory.getLogger(AccessLogWebFilter.class);

    private final GatewayAccessProperties properties;

    public AccessLogWebFilter(GatewayAccessProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!properties.isAccessLogEnabled()) {
            return chain.filter(exchange);
        }
        long startAt = System.currentTimeMillis();
        return chain.filter(exchange)
                .doFinally(signalType -> logger.info(
                        "gateway access method={} path={} status={} remoteIp={} traceId={} costMs={}",
                        exchange.getRequest().getMethod(),
                        exchange.getRequest().getURI().getRawPath(),
                        exchange.getResponse().getStatusCode(),
                        exchange.getRequest().getRemoteAddress(),
                        exchange.getAttributeOrDefault(TraceWebFilter.TRACE_ID_ATTR, "na"),
                        System.currentTimeMillis() - startAt));
    }
}
