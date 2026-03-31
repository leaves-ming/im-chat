package com.ming.imchatserver.service.impl;

import com.ming.imchatserver.redis.RedisKeyFactory;
import com.ming.imchatserver.service.RateLimitService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 固定窗口限流实现。
 */
@Service
public class RateLimitServiceImpl implements RateLimitService {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisKeyFactory redisKeyFactory;

    public RateLimitServiceImpl(StringRedisTemplate stringRedisTemplate, RedisKeyFactory redisKeyFactory) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisKeyFactory = redisKeyFactory;
    }

    @Override
    public Decision checkAndIncrement(String scope, String dimension, String subject, long limit, long windowSeconds) {
        if (limit <= 0L || windowSeconds <= 0L || subject == null || subject.isBlank()) {
            return new Decision(true, 0L, limit);
        }
        long now = System.currentTimeMillis() / 1000L;
        long windowStart = now - (now % windowSeconds);
        String key = redisKeyFactory.rateLimit(scope, dimension, subject, windowStart);
        Long current = stringRedisTemplate.opsForValue().increment(key);
        if (current != null && current == 1L) {
            stringRedisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
        }
        long count = current == null ? 0L : current;
        return new Decision(count <= limit, count, limit);
    }
}

