package com.ming.imchatserver.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * WebSocket 命令路由开关。
 * <p>
 * 默认保守关闭，通过配置中心灰度开启新链路。
 */
@Component
@ConfigurationProperties(prefix = "route")
@Getter
@Setter
public class WsRouteProperties {

    private Toggle chat = new Toggle();
    private Toggle group = new Toggle();
    private Toggle contact = new Toggle();
    private Toggle file = new Toggle();

    public boolean chatV2Enabled() {
        return chat.isV2();
    }

    public boolean groupV2Enabled() {
        return group.isV2();
    }

    public boolean contactV2Enabled() {
        return contact.isV2();
    }

    public boolean fileV2Enabled() {
        return file.isV2();
    }

    @Getter
    @Setter
    public static class Toggle {
        private boolean v2 = false;
    }
}
