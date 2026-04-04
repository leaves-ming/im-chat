package com.ming.imchatserver.redis;

import org.springframework.stereotype.Component;

/**
 * 统一构造 Redis key。
 * @author maing
 */
@Component
public class RedisKeyFactory {

    public String presenceUserSessions(Long userId) {
        return RedisPrefixes.PRESENCE + "user:" + userId + ":sessions";
    }

    public String presenceSession(String sessionId) {
        return RedisPrefixes.PRESENCE + "session:" + sessionId;
    }

    public String idempotentClientMsg(Long userId, String clientMsgId) {
        return RedisPrefixes.IDEMPOTENT + "client_msg:" + userId + ":" + clientMsgId;
    }

    public String idempotentOneTime(String scope, String token) {
        return RedisPrefixes.IDEMPOTENT + scope + ":" + token;
    }

    public String rateLimit(String scope, String dimension, String subject, long windowStart) {
        return RedisPrefixes.RATE_LIMIT + scope + ":" + dimension + ":" + subject + ":" + windowStart;
    }

    public String coordinationLock(String scope, String resourceId) {
        return RedisPrefixes.COORD + "lock:" + scope + ":" + resourceId;
    }

    public String coordinationMarker(String scope, String markerId) {
        return RedisPrefixes.COORD + "marker:" + scope + ":" + markerId;
    }
}

