package com.ming.imchatserver.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Redis 固定窗口限流配置。
 */
@Component
@ConfigurationProperties(prefix = "im.rate-limit")
@Getter
@Setter
public class RateLimitProperties {
    private Rule loginFail = new Rule(20, 300);
    private Rule messageSend = new Rule(120, 60);
    private Rule fileUpload = new Rule(30, 60);
    private Rule fileDownload = new Rule(60, 60);

    @Getter
    @Setter
    public static class Rule {
        private long limit;
        private long windowSeconds;

        public Rule() {
        }

        public Rule(long limit, long windowSeconds) {
            this.limit = limit;
            this.windowSeconds = windowSeconds;
        }
    }
}

