package com.ming.imuserservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * 登录风控配置，支持 Nacos 动态刷新。
 */
@Getter
@Setter
@RefreshScope
@Component
@ConfigurationProperties(prefix = "im.user.login-risk")
public class LoginRiskControlProperties {

    private Rule ip = new Rule(20, 300);
    private Rule device = new Rule(20, 300);
    private Rule username = new Rule(10, 300);

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
