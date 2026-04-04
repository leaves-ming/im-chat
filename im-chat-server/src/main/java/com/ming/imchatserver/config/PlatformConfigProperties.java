package com.ming.imchatserver.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 平台底座配置。
 */
@Component
@ConfigurationProperties(prefix = "im.platform")
@Getter
@Setter
public class PlatformConfigProperties {

    private final Config config = new Config();

    @Getter
    @Setter
    public static class Config {
        private String sharedDataId = "im-shared.yaml";
        private String serviceDataId = "im-chat-server.yaml";
        private List<String> hotRefreshSafePrefixes = new ArrayList<>();
    }
}
