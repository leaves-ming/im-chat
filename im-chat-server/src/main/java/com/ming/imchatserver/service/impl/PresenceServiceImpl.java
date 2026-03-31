package com.ming.imchatserver.service.impl;

import com.ming.imchatserver.config.RedisStateProperties;
import com.ming.imchatserver.redis.RedisKeyFactory;
import com.ming.imchatserver.service.PresenceService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Redis 在线状态视图实现。
 */
@Service
public class PresenceServiceImpl implements PresenceService {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisKeyFactory redisKeyFactory;
    private final RedisStateProperties redisStateProperties;

    public PresenceServiceImpl(StringRedisTemplate stringRedisTemplate,
                               RedisKeyFactory redisKeyFactory,
                               RedisStateProperties redisStateProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisKeyFactory = redisKeyFactory;
        this.redisStateProperties = redisStateProperties;
    }

    @Override
    public void register(Long userId, String serverId, String deviceId, String sessionId, long lastHeartbeatAt) {
        if (userId == null || sessionId == null || sessionId.isBlank()) {
            return;
        }
        String userSessionsKey = redisKeyFactory.presenceUserSessions(userId);
        String sessionKey = redisKeyFactory.presenceSession(sessionId);
        Duration ttl = Duration.ofSeconds(redisStateProperties.getPresenceTtlSeconds());
        stringRedisTemplate.opsForSet().add(userSessionsKey, sessionId);
        stringRedisTemplate.expire(userSessionsKey, ttl);
        stringRedisTemplate.opsForHash().put(sessionKey, "userId", String.valueOf(userId));
        stringRedisTemplate.opsForHash().put(sessionKey, "serverId", serverId == null ? "" : serverId);
        stringRedisTemplate.opsForHash().put(sessionKey, "deviceId", deviceId == null ? "" : deviceId);
        stringRedisTemplate.opsForHash().put(sessionKey, "sessionId", sessionId);
        stringRedisTemplate.opsForHash().put(sessionKey, "lastHeartbeatAt", String.valueOf(lastHeartbeatAt));
        stringRedisTemplate.expire(sessionKey, ttl);
    }

    @Override
    public void refreshHeartbeat(Long userId, String sessionId, long lastHeartbeatAt) {
        if (userId == null || sessionId == null || sessionId.isBlank()) {
            return;
        }
        String userSessionsKey = redisKeyFactory.presenceUserSessions(userId);
        String sessionKey = redisKeyFactory.presenceSession(sessionId);
        Duration ttl = Duration.ofSeconds(redisStateProperties.getPresenceTtlSeconds());
        stringRedisTemplate.opsForHash().put(sessionKey, "lastHeartbeatAt", String.valueOf(lastHeartbeatAt));
        stringRedisTemplate.expire(sessionKey, ttl);
        stringRedisTemplate.expire(userSessionsKey, ttl);
    }

    @Override
    public void unregister(Long userId, String sessionId) {
        if (userId == null || sessionId == null || sessionId.isBlank()) {
            return;
        }
        stringRedisTemplate.opsForSet().remove(redisKeyFactory.presenceUserSessions(userId), sessionId);
        stringRedisTemplate.delete(redisKeyFactory.presenceSession(sessionId));
    }

    @Override
    public List<OnlineEndpoint> listOnlineEndpoints(Long userId) {
        if (userId == null) {
            return List.of();
        }
        Set<String> sessionIds = stringRedisTemplate.opsForSet().members(redisKeyFactory.presenceUserSessions(userId));
        if (sessionIds == null || sessionIds.isEmpty()) {
            return List.of();
        }
        List<OnlineEndpoint> endpoints = new ArrayList<>();
        for (String sessionId : sessionIds) {
            Map<Object, Object> session = stringRedisTemplate.opsForHash().entries(redisKeyFactory.presenceSession(sessionId));
            if (session == null || session.isEmpty()) {
                continue;
            }
            endpoints.add(new OnlineEndpoint(
                    String.valueOf(session.getOrDefault("serverId", "")),
                    String.valueOf(session.getOrDefault("deviceId", "")),
                    String.valueOf(session.getOrDefault("sessionId", sessionId)),
                    parseLong(session.get("lastHeartbeatAt"))));
        }
        return endpoints;
    }

    @Override
    public boolean isOnline(Long userId) {
        return !listOnlineEndpoints(userId).isEmpty();
    }

    private long parseLong(Object value) {
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }
}

