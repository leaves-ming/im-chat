package com.ming.imgateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ming.imgateway.auth.AuthenticatedUser;
import com.ming.imgateway.auth.GatewayAuthAdapter;
import com.ming.imgateway.config.GatewayAccessProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WebSocket 握手统一适配：抽取 token、补充 trace 和可信代理头。
 */
@Component
public class WebSocketProxyGlobalFilter implements GlobalFilter, Ordered {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final GatewayAuthAdapter gatewayAuthAdapter;
    private final GatewayAccessProperties properties;

    public WebSocketProxyGlobalFilter(GatewayAuthAdapter gatewayAuthAdapter, GatewayAccessProperties properties) {
        this.gatewayAuthAdapter = gatewayAuthAdapter;
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!isWebSocketHandshake(exchange.getRequest())) {
            return chain.filter(exchange);
        }
        String token = gatewayAuthAdapter.resolveToken(exchange);
        String traceId = exchange.getAttributeOrDefault(TraceWebFilter.TRACE_ID_ATTR, "");
        ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate();
        if (StringUtils.hasText(traceId)) {
            requestBuilder.header(properties.getTraceHeaderName(), traceId);
        }
        requestBuilder.header(properties.getGatewayProxyHeaderName(), properties.getGatewayProxyHeaderValue());
        requestBuilder.header(properties.getGatewaySecretHeaderName(), properties.getGatewaySharedSecret());
        requestBuilder.header(properties.getClientIpHeaderName(), resolveClientIp(exchange));
        if (StringUtils.hasText(token)) {
            requestBuilder.headers(headers -> headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token));
        }
        if (!properties.isIntrospectEnabled()) {
            return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
        }
        if (!StringUtils.hasText(token)) {
            return unauthorized(exchange, traceId, "missing token");
        }
        return gatewayAuthAdapter.introspect(token)
                .switchIfEmpty(Mono.error(new IllegalStateException("invalid token")))
                .flatMap(user -> {
                    if (properties.isRelayUserHeaders()) {
                        relayUserHeaders(requestBuilder, user);
                    }
                    return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
                })
                .onErrorResume(ex -> unauthorized(exchange, traceId, "invalid token"));
    }

    @Override
    public int getOrder() {
        return -80;
    }

    private boolean isWebSocketHandshake(ServerHttpRequest request) {
        return properties.getWebsocketPath().equals(request.getURI().getPath())
                && "websocket".equalsIgnoreCase(request.getHeaders().getUpgrade());
    }

    private void relayUserHeaders(ServerHttpRequest.Builder requestBuilder, AuthenticatedUser user) {
        if (user.userId() != null) {
            requestBuilder.header("X-Auth-User-Id", String.valueOf(user.userId()));
        }
        if (StringUtils.hasText(user.username())) {
            requestBuilder.header("X-Auth-Username", user.username());
        }
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        String realIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            return xff.split(",")[0].trim();
        }
        if (exchange.getRequest().getRemoteAddress() == null) {
            return "unknown";
        }
        if (exchange.getRequest().getRemoteAddress().getAddress() != null) {
            return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }
        return String.valueOf(exchange.getRequest().getRemoteAddress());
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String traceId, String message) {
        try {
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            if (StringUtils.hasText(traceId)) {
                response.getHeaders().set(properties.getTraceHeaderName(), traceId);
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("code", 401);
            payload.put("msg", message);
            payload.put("traceId", traceId);
            byte[] bytes = MAPPER.writeValueAsBytes(payload);
            DataBuffer dataBuffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(dataBuffer));
        } catch (Exception ex) {
            byte[] fallback = "{\"code\":401,\"msg\":\"invalid token\"}".getBytes(StandardCharsets.UTF_8);
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(fallback)));
        }
    }
}
