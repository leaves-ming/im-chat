package com.ming.imchatserver.service.impl;

import com.ming.imchatserver.redis.RedisKeyFactory;
import com.ming.imchatserver.service.IdempotencyService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis 短期幂等实现。
 */
@Service
public class IdempotencyServiceImpl implements IdempotencyService {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisKeyFactory redisKeyFactory;

    public IdempotencyServiceImpl(StringRedisTemplate stringRedisTemplate, RedisKeyFactory redisKeyFactory) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisKeyFactory = redisKeyFactory;
    }

    @Override
    public boolean claimClientMessage(Long userId, String clientMsgId, Duration ttl) {
        if (userId == null || clientMsgId == null || clientMsgId.isBlank() || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return true;
        }
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                .setIfAbsent(redisKeyFactory.idempotentClientMsg(userId, clientMsgId), "1", ttl));
    }

    @Override
    public boolean consumeOnce(String scope, String token, Duration ttl) {
        if (scope == null || scope.isBlank() || token == null || token.isBlank() || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return false;
        }
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                .setIfAbsent(redisKeyFactory.idempotentOneTime(scope, token), "1", ttl));
    }
}

