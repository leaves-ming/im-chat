package com.ming.imuserservice.service.impl;

import com.ming.imuserservice.service.RateLimitService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 基于 Redis 的固定窗口限流实现。
 */
@Service
public class RedisRateLimitServiceImpl implements RateLimitService {

    private final StringRedisTemplate stringRedisTemplate;

    public RedisRateLimitServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public Decision checkAndIncrement(String scope, String dimension, String subject, long limit, long windowSeconds) {
        if (limit <= 0L || windowSeconds <= 0L || subject == null || subject.isBlank()) {
            return new Decision(true, 0L, limit);
        }
        long now = System.currentTimeMillis() / 1000L;
        long windowStart = now - (now % windowSeconds);
        String key = key(scope, dimension, subject, windowStart);
        Long current = stringRedisTemplate.opsForValue().increment(key);
        if (current != null && current == 1L) {
            stringRedisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
        }
        long count = current == null ? 0L : current;
        return new Decision(count <= limit, count, limit);
    }

    @Override
    public void reset(String scope, String dimension, String subject, long windowSeconds) {
        if (windowSeconds <= 0L || subject == null || subject.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis() / 1000L;
        long windowStart = now - (now % windowSeconds);
        stringRedisTemplate.delete(key(scope, dimension, subject, windowStart));
    }

    private String key(String scope, String dimension, String subject, long windowStart) {
        return "im:user:rate_limit:" + scope + ":" + dimension + ":" + subject + ":" + windowStart;
    }
}
