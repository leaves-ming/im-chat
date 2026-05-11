package com.ming.imgateway.controller;

import com.ming.imgateway.config.GatewayAccessProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 登录入口由 gateway 接管，对外保持稳定路径。
 */
@RestController
public class AuthGatewayController {

    private final WebClient plainWebClient;
    private final WebClient gatewayWebClient;
    private final GatewayAccessProperties properties;

    public AuthGatewayController(WebClient plainWebClient, WebClient gatewayWebClient, GatewayAccessProperties properties) {
        this.plainWebClient = plainWebClient;
        this.gatewayWebClient = gatewayWebClient;
        this.properties = properties;
    }

    @PostMapping(path = "/api/auth/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<String>> login(@RequestBody String body, ServerWebExchange exchange) {
        return gatewayWebClient.post()
                .uri("lb://im-user-service/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> copyForwardHeaders(exchange, headers))
                .body(BodyInserters.fromValue(body))
                .exchangeToMono(response -> response.toEntity(String.class));
    }

    private void copyForwardHeaders(ServerWebExchange exchange, HttpHeaders downstreamHeaders) {
        HttpHeaders incomingHeaders = exchange.getRequest().getHeaders();
        String traceId = exchange.getAttributeOrDefault("traceId", "");
        if (StringUtils.hasText(traceId)) {
            downstreamHeaders.set(properties.getTraceHeaderName(), traceId);
        }
        copyIfPresent(incomingHeaders, downstreamHeaders, "X-Device-Id");
        copyIfPresent(incomingHeaders, downstreamHeaders, "X-Real-IP");
        copyIfPresent(incomingHeaders, downstreamHeaders, "X-Forwarded-For");
        copyIfPresent(incomingHeaders, downstreamHeaders, "X-Forwarded-Proto");
        copyIfPresent(incomingHeaders, downstreamHeaders, HttpHeaders.USER_AGENT);
    }

    private void copyIfPresent(HttpHeaders incomingHeaders, HttpHeaders downstreamHeaders, String headerName) {
        String value = incomingHeaders.getFirst(headerName);
        if (StringUtils.hasText(value)) {
            downstreamHeaders.set(headerName, value);
        }
    }
}
