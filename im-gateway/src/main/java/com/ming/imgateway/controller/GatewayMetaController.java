package com.ming.imgateway.controller;

import com.ming.imgateway.config.InstanceProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.actuate.endpoint.HealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * gateway 自身探针与元数据接口。
 */
@RestController
public class GatewayMetaController {

    private final InstanceProperties instanceProperties;
    private final HealthEndpoint healthEndpoint;

    @Value("${spring.application.name}")
    private String applicationName;

    public GatewayMetaController(InstanceProperties instanceProperties, HealthEndpoint healthEndpoint) {
        this.instanceProperties = instanceProperties;
        this.healthEndpoint = healthEndpoint;
    }

    @GetMapping("/internal/health")
    public Mono<Map<String, Object>> health() {
        HealthDescriptor health = healthEndpoint.health();
        return Mono.just(okPayload(health));
    }

    @GetMapping("/internal/instance")
    public Mono<Map<String, Object>> instance() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("service", applicationName);
        data.put("version", instanceProperties.getVersion());
        data.put("zone", instanceProperties.getZone());
        data.put("protocol", instanceProperties.getProtocol());
        data.put("startupTime", instanceProperties.getStartupTime());
        return Mono.just(okPayload(data));
    }

    @GetMapping("/internal/meta")
    public Mono<Map<String, Object>> meta() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("service", applicationName);
        data.put("version", instanceProperties.getVersion());
        data.put("status", "UP");
        data.put("stage", "phase-3-gateway-only-ws-entry");
        data.put("capabilities", java.util.List.of(
                "auth-login-proxy",
                "message-http-routing-skeleton",
                "social-http-routing-skeleton",
                "websocket-entry-proxy",
                "trace-access-log-health-instance"));
        return Mono.just(okPayload(data));
    }

    private Map<String, Object> okPayload(Object data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", 0);
        payload.put("msg", "ok");
        payload.put("data", data);
        return payload;
    }
}
