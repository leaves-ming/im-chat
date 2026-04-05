package com.ming.imsocialservice.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * social 服务探活接口。
 */
@RestController
@RequestMapping("/api/social-service/meta")
public class ServiceMetaController {

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${im.instance.version:unknown}")
    private String version;

    @GetMapping
    public Map<String, Object> info() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("service", applicationName);
        payload.put("version", version);
        payload.put("status", "UP");
        payload.put("stage", "relationship-migrated");
        return payload;
    }
}
