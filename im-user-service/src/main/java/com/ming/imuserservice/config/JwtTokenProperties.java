package com.ming.imuserservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "im.user.jwt")
public class JwtTokenProperties {

    private String secret = "PLEASE_REPLACE_JWT_SECRET";
    private long expireSeconds = 3600L;
    private String issuer = "im-user-service";
}
