package com.ming.imchatserver.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * social 远程调用路由与缓存配置。
 */
@Component
@ConfigurationProperties(prefix = "im.route.social")
@Getter
@Setter
public class SocialRouteProperties {

    /**
     * 是否启用 social 远程路由。
     */
    private boolean remoteEnabled = true;

    /**
     * 单聊权限缓存 TTL，单位秒。
     */
    private int singleChatPermissionCacheTtlSeconds = 15;

    /**
     * 联系人单向关系缓存 TTL，单位秒。
     */
    private int contactActiveCacheTtlSeconds = 15;

    /**
     * 群成员列表缓存 TTL，单位秒。
     */
    private int groupMemberIdsCacheTtlSeconds = 5;

    /**
     * 群撤回权限缓存 TTL，单位秒。
     */
    private int groupRecallPermissionCacheTtlSeconds = 5;
}
