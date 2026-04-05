package com.ming.imgateway.filter;

import com.ming.imgateway.auth.AuthenticatedUser;
import com.ming.imgateway.auth.GatewayAuthAdapter;
import com.ming.imgateway.config.GatewayAccessProperties;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 为下游 message/social 路由统一做 token 透传和可选 introspect。
 */
@Component
public class TokenRelayGlobalFilter implements GlobalFilter, Ordered {

    private final GatewayAuthAdapter gatewayAuthAdapter;
    private final GatewayAccessProperties properties;

    public TokenRelayGlobalFilter(GatewayAuthAdapter gatewayAuthAdapter, GatewayAccessProperties properties) {
        this.gatewayAuthAdapter = gatewayAuthAdapter;
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange,
                             org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (!matchesProtectedPrefix(path)) {
            return chain.filter(exchange);
        }
        String token = gatewayAuthAdapter.resolveToken(exchange);
        ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate();
        String traceId = exchange.getAttributeOrDefault(TraceWebFilter.TRACE_ID_ATTR, "");
        if (StringUtils.hasText(traceId)) {
            requestBuilder.header(properties.getTraceHeaderName(), traceId);
        }
        if (!properties.isIntrospectEnabled()) {
            if (!StringUtils.hasText(token)) {
                return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
            }
            requestBuilder.headers(headers -> headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token));
            return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
        }
        if (!StringUtils.hasText(token)) {
            return unauthorized(exchange, traceId, "missing token");
        }
        requestBuilder.headers(headers -> headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token));
        return gatewayAuthAdapter.introspect(token)
                .switchIfEmpty(Mono.error(new IllegalStateException("invalid token")))
                .flatMap(user -> {
                    if (properties.isRelayUserHeaders() && user.userId() != null) {
                        requestBuilder.header("X-Auth-User-Id", String.valueOf(user.userId()));
                        if (StringUtils.hasText(user.username())) {
                            requestBuilder.header("X-Auth-Username", user.username());
                        }
                    }
                    return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
                })
                .onErrorResume(ex -> unauthorized(exchange, traceId, "invalid token"));
    }

    @Override
    public int getOrder() {
        return -50;
    }

    private boolean matchesProtectedPrefix(String path) {
        return properties.getProtectedPrefixes().stream().anyMatch(path::startsWith);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String traceId, String message) {
        byte[] payload = ("{\"code\":401,\"msg\":\"" + message + "\",\"traceId\":\""
                + (traceId == null ? "" : traceId) + "\"}").getBytes(StandardCharsets.UTF_8);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(traceId)) {
            exchange.getResponse().getHeaders().set(properties.getTraceHeaderName(), traceId);
        }
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(payload);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
