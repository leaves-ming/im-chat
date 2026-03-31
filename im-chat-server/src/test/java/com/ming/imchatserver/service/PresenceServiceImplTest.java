package com.ming.imchatserver.service;

import com.ming.imchatserver.config.RedisStateProperties;
import com.ming.imchatserver.redis.RedisKeyFactory;
import com.ming.imchatserver.service.impl.PresenceServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PresenceServiceImplTest {

    @Test
    void listOnlineEndpointsShouldRemoveStaleSessions() {
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOperations = mock(SetOperations.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);

        RedisKeyFactory redisKeyFactory = new RedisKeyFactory();
        RedisStateProperties redisStateProperties = new RedisStateProperties();
        PresenceServiceImpl presenceService = new PresenceServiceImpl(stringRedisTemplate, redisKeyFactory, redisStateProperties);

        when(setOperations.members("im:presence:user:9:sessions")).thenReturn(Set.of("s1", "stale"));
        when(hashOperations.entries("im:presence:session:s1")).thenReturn(Map.of(
                "serverId", "node-1",
                "deviceId", "ios",
                "sessionId", "s1",
                "lastHeartbeatAt", "123"));
        when(hashOperations.entries("im:presence:session:stale")).thenReturn(Map.of());

        var endpoints = presenceService.listOnlineEndpoints(9L);

        assertEquals(1, endpoints.size());
        assertEquals("s1", endpoints.get(0).sessionId());
        verify(setOperations).remove("im:presence:user:9:sessions", any());
    }

    @Test
    void listOnlineEndpointsShouldReturnEmptyWhenOnlyStaleSessionsExist() {
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOperations = mock(SetOperations.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);

        RedisKeyFactory redisKeyFactory = new RedisKeyFactory();
        RedisStateProperties redisStateProperties = new RedisStateProperties();
        PresenceServiceImpl presenceService = new PresenceServiceImpl(stringRedisTemplate, redisKeyFactory, redisStateProperties);

        when(setOperations.members("im:presence:user:9:sessions")).thenReturn(Set.of("stale-1", "stale-2"));
        when(hashOperations.entries("im:presence:session:stale-1")).thenReturn(Map.of());
        when(hashOperations.entries("im:presence:session:stale-2")).thenReturn(Map.of());

        var endpoints = presenceService.listOnlineEndpoints(9L);

        assertTrue(endpoints.isEmpty());
        verify(setOperations).remove("im:presence:user:9:sessions", any());
    }
}
