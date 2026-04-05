package com.ming.imgateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 当前 gateway 实例元数据。
 */
@Component
@ConfigurationProperties(prefix = "im.instance")
@Getter
@Setter
public class InstanceProperties {

    private String version = "1.0.0-SNAPSHOT";

    private String zone = "default";

    private String protocol = "http-gateway";

    private String startupTime = "unknown";
}
