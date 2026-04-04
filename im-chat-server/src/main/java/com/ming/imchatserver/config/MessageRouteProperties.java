package com.ming.imchatserver.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 单聊消息远程路由配置。
 */
@Component
@ConfigurationProperties(prefix = "im.route.message")
@Getter
@Setter
public class MessageRouteProperties {

    /**
     * 是否启用 gateway -> im-message-service 远程路由。
     */
    private boolean remoteEnabled = false;

    /**
     * 远程调用失败时是否回退本地实现。
     */
    private boolean fallbackToLocalOnError = true;
}
