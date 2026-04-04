package com.ming.imsocialservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * social 服务基础配置。
 */
@Component
@ConfigurationProperties(prefix = "im.social-service")
@Getter
@Setter
public class SocialServiceProperties {

    private int defaultContactListLimit = 50;
    private int defaultGroupMemberListLimit = 50;
    private boolean redisEnabled = true;
}
