package com.ming.imchatserver.netty;

import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChannelUserManager {

    private static final Logger logger = LoggerFactory.getLogger(ChannelUserManager.class);

    // 全部在线 channel
    private final ChannelGroup allChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    // userId -> set of channel ids
    private final Map<Long, Set<String>> userChannels = new ConcurrentHashMap<>();

    public void bindUser(Channel channel, Long userId) {
        allChannels.add(channel);
        userChannels.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(channel.id().asLongText());
        logger.info("bind user {} to channel {}", userId, channel.id());
    }

    public void unbindUser(Channel channel, Long userId) {
        allChannels.remove(channel);
        Set<String> set = userChannels.get(userId);
        if (set != null) {
            set.remove(channel.id().asLongText());
            if (set.isEmpty()) {
                userChannels.remove(userId);
            }
        }
        logger.info("unbind user {} from channel {}", userId, channel.id());
    }

    public void unbindByChannel(Channel channel) {
        allChannels.remove(channel);
        String id = channel.id().asLongText();
        ArrayList<Long> toRemove = new ArrayList<>();
        for (Map.Entry<Long, Set<String>> e : userChannels.entrySet()) {
            Set<String> set = e.getValue();
            if (set != null && set.remove(id)) {
                logger.info("removed channel {} from user {} mapping", channel.id(), e.getKey());
                if (set.isEmpty()) toRemove.add(e.getKey());
            }
        }
        for (Long k : toRemove) userChannels.remove(k);
    }

    public Collection<Channel> getChannels(Long userId) {
        Set<String> set = userChannels.get(userId);
        if (set == null || set.isEmpty()) return java.util.List.of();
        java.util.List<Channel> list = new java.util.ArrayList<>();
        for (Channel c : allChannels) {
            if (set.contains(c.id().asLongText())) list.add(c);
        }
        return list;
    }

    public ChannelGroup allChannels() {
        return allChannels;
    }
}

