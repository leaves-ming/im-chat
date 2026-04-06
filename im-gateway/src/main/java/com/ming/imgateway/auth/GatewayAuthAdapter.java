package com.ming.imgateway.auth;

import com.ming.imapicontract.common.ApiResponse;
import com.ming.imapicontract.user.IntrospectTokenRequest;
import com.ming.imapicontract.user.IntrospectTokenResponse;
import com.ming.imgateway.config.GatewayAccessProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 认证适配层，负责 token 提取与可选 introspect。
 */
@Component
public class GatewayAuthAdapter {

    private static final ParameterizedTypeReference<ApiResponse<IntrospectTokenResponse>> INTROSPECT_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final WebClient webClient;
    private final GatewayAccessProperties properties;

    public GatewayAuthAdapter(WebClient gatewayWebClient, GatewayAccessProperties properties) {
        this.webClient = gatewayWebClient;
        this.properties = properties;
    }

    public String resolveToken(ServerWebExchange exchange) {
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authorization) && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authorization.substring(7).trim();
        }
        String headerToken = exchange.getRequest().getHeaders().getFirst("X-Access-Token");
        if (StringUtils.hasText(headerToken)) {
            return headerToken.trim();
        }
        List<String> queryTokens = exchange.getRequest().getQueryParams().get("token");
        if (queryTokens != null) {
            for (String queryToken : queryTokens) {
                if (StringUtils.hasText(queryToken)) {
                    return queryToken.trim();
                }
            }
        }
        return null;
    }

    public Mono<AuthenticatedUser> introspect(String token) {
        if (!properties.isIntrospectEnabled() || !StringUtils.hasText(token)) {
            return Mono.empty();
        }
        return webClient.post()
                .uri("http://im-user-service/api/user/auth/introspect")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new IntrospectTokenRequest(token))
                .retrieve()
                .bodyToMono(INTROSPECT_RESPONSE_TYPE)
                .filter(ApiResponse::isSuccess)
                .map(ApiResponse::getData)
                .filter(data -> data != null && Boolean.TRUE.equals(data.active()))
                .map(data -> new AuthenticatedUser(data.userId(), data.username(), token))
                .onErrorResume(ex -> Mono.empty());
    }
}
