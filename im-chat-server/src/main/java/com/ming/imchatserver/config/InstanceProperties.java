package com.ming.imchatserver.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 当前实例基础信息。
 */
@Component
@ConfigurationProperties(prefix = "im.instance")
@Getter
@Setter
public class InstanceProperties {

    private String version = "1.0.0-SNAPSHOT";

    private String zone = "default";

    private String protocol = "netty-ws-http";

    private String startupTime = "unknown";
}
