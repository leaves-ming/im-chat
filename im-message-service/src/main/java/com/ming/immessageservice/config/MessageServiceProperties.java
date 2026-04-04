package com.ming.immessageservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 消息服务配置。
 */
@Component
@ConfigurationProperties(prefix = "im.message-service")
@Getter
@Setter
public class MessageServiceProperties {

    private int offlinePullMaxLimit = 200;
}
