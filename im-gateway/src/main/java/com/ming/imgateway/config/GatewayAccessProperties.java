package com.ming.imgateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * gateway 接入层运行配置。
 */
@Component
@ConfigurationProperties(prefix = "im.gateway")
@Getter
@Setter
public class GatewayAccessProperties {

    private static final String DEFAULT_SHARED_SECRET = "PLEASE_REPLACE_GATEWAY_WS_SECRET";

    private boolean accessLogEnabled = true;

    private String traceHeaderName = "X-Trace-Id";

    private boolean generateTraceIdIfMissing = true;

    private boolean introspectEnabled = false;

    private boolean relayUserHeaders = true;

    private List<String> protectedPrefixes = new ArrayList<>(List.of("/api/message/", "/api/social/"));

    private String websocketPath = "/ws";

    private String backendWebsocketPath = "/ws-internal";

    private String gatewayProxyHeaderName = "X-IM-Gateway-Proxy";

    private String gatewayProxyHeaderValue = "im-gateway";

    private String gatewaySecretHeaderName = "X-IM-Gateway-Secret";

    private String gatewaySharedSecret = DEFAULT_SHARED_SECRET;

    private String clientIpHeaderName = "X-IM-Client-IP";
}
