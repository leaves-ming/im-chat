package com.ming.imchatserver.netty;

import com.ming.imchatserver.config.RedisStateProperties;
import com.ming.imchatserver.service.PresenceService;
import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
/**
 * 管理“用户ID <-> Netty Channel”双向映射关系。
 * <p>
 * 支持一人多端连接，并提供按用户查连接、按连接反查用户、连接解绑等能力，
 * 是消息路由和在线状态判断的基础组件。
 */
@Component
public class ChannelUserManager {

    private static final Logger logger = LoggerFactory.getLogger(ChannelUserManager.class);
    private final PresenceService presenceService;
    private final RedisStateProperties redisStateProperties;

    public ChannelUserManager(PresenceService presenceService, RedisStateProperties redisStateProperties) {
        this.presenceService = presenceService;
        this.redisStateProperties = redisStateProperties;
    }

    // 全部在线 channel
    private final ChannelGroup allChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    // userId -> set of Channel (支持一人多端)
    private final Map<Long, Set<Channel>> userChannels = new ConcurrentHashMap<>();

    // channelId -> userId 反向索引，方便 O(1) 解绑/查询
    private final Map<String, Long> channelToUser = new ConcurrentHashMap<>();

    /**
     * 绑定用户到 channel（幂等）。
     * 时间复杂度：O(1)
     */
    
    public void bindUser(Channel channel, Long userId) {
        String cid = channel.id().asLongText();
        // synchronize on the channel instance to make bind/unbind for the same channel atomic
        synchronized (channel) {
            Long prevUser = channelToUser.get(cid);
            // already bound to same user -> ensure in user's set and return (idempotent)
            if (prevUser != null && prevUser.equals(userId)) {
                // ensure present in userChannels
                userChannels.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(channel);
                allChannels.add(channel);
                logger.debug("bind called but already bound user {} -> channel {} (ignored)", userId, channel.id());
                return;
            }

            // bound to different user -> remove from old user's set first (safe rebind)
            if (prevUser != null && !prevUser.equals(userId)) {
                Set<Channel> oldSet = userChannels.get(prevUser);
                if (oldSet != null) {
                    oldSet.remove(channel);
                    if (oldSet.isEmpty()) {
                        userChannels.remove(prevUser);
                    }
                }
                logger.info("rebind channel {}: removed from previous user {}", channel.id(), prevUser);
            }

            // set reverse mapping to new user and add to user's set
            channelToUser.put(cid, userId);
            userChannels.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(channel);
            allChannels.add(channel);
            String deviceId = channel.attr(NettyAttr.DEVICE_ID).get();
            presenceService.register(userId, redisStateProperties.getServerId(), deviceId, cid, System.currentTimeMillis());
            logger.info("bind user {} to channel {}", userId, channel.id());
        }
    }
    /**
     * 按 userId 解绑指定 channel（幂等）。
     */
    
    public void unbindUser(Channel channel, Long userId) {
        String cid = channel.id().asLongText();
        allChannels.remove(channel);
        // remove reverse mapping only if matches
        channelToUser.remove(cid, userId);
        Set<Channel> set = userChannels.get(userId);
        if (set != null) {
            set.remove(channel);
            if (set.isEmpty()) {
                userChannels.remove(userId);
            }
        }
        presenceService.unregister(userId, cid);
        logger.info("unbind user {} from channel {}", userId, channel.id());
    }
    /**
     * 按 channel 解绑（O(1)），利用反向索引快速找到 userId
     */
    
    public void unbindByChannel(Channel channel) {
        String cid = channel.id().asLongText();
        allChannels.remove(channel);
        Long userId = channelToUser.remove(cid);
        if (userId != null) {
            Set<Channel> set = userChannels.get(userId);
            if (set != null) {
                set.remove(channel);
                if (set.isEmpty()) {
                    userChannels.remove(userId);
                }
            }
            presenceService.unregister(userId, cid);
            logger.info("removed channel {} from user {} mapping", channel.id(), userId);
        } else {
            logger.debug("unbindByChannel: no user mapping found for channel {}", channel.id());
        }
    }
    /**
     * 获取用户的所有 Channel（返回视图副本，避免外部修改内部集合）
     * 时间复杂度：O(k) where k = 用户在线连接数
     */
    
    public Collection<Channel> getChannels(Long userId) {
        Set<Channel> set = userChannels.get(userId);
        if (set == null || set.isEmpty()) {
            return java.util.List.of();
        }
        return java.util.List.copyOf(set);
    }    /**
     * 返回当前所有在线连接集合（Netty ChannelGroup）。
     */
    
    public ChannelGroup allChannels() {
        return allChannels;
    }
    /** 返回 channelId 对应的 userId，若不存在返回 null。O(1) */
    
    public Long getUserIdByChannelId(String channelId) {
        return channelToUser.get(channelId);
    }
    /** 当前在线人数（按 userId 计数）*/
    
    public int onlineCount() {
        return userChannels.size();
    }

    public void refreshHeartbeat(Channel channel) {
        if (channel == null) {
            return;
        }
        Long userId = channelToUser.get(channel.id().asLongText());
        if (userId == null) {
            userId = channel.attr(NettyAttr.USER_ID).get();
        }
        if (userId != null) {
            presenceService.refreshHeartbeat(userId, channel.id().asLongText(), System.currentTimeMillis());
        }
    }
}
