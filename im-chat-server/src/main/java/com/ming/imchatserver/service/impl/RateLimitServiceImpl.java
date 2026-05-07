package com.ming.imchatserver.service.impl;

import com.ming.imchatserver.redis.RedisKeyFactory;
import com.ming.imchatserver.service.RateLimitService;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

/**
 * 令牌桶限流实现，Redisson RRateLimiter保证原子性，支持平滑流量控制。
 */
@Service
public class RateLimitServiceImpl implements RateLimitService {

    private final RedissonClient redissonClient;
    private final RedisKeyFactory redisKeyFactory;

    public RateLimitServiceImpl(RedissonClient redissonClient, RedisKeyFactory redisKeyFactory) {
        this.redissonClient = redissonClient;
        this.redisKeyFactory = redisKeyFactory;
    }

    @Override
    public Decision checkAndIncrement(String scope, String dimension, String subject, long limit, long windowSeconds) {
        if (limit <= 0L || windowSeconds <= 0L || subject == null || subject.isBlank()) {
            return new Decision(true, 0L, limit);
        }
        // 限流key不带时间窗口后缀，Redisson内部自动维护过期
        String key = redisKeyFactory.rateLimit(scope, dimension, subject);
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        
        // 幂等初始化限流器，已存在则不会覆盖配置
        rateLimiter.trySetRate(RateType.OVERALL, limit, windowSeconds, RateIntervalUnit.SECONDS);
        
        // 尝试获取1个令牌
        boolean allowed = rateLimiter.tryAcquire(1);
        // 剩余可用令牌数
        long availablePermits = rateLimiter.availablePermits();
        // 已使用令牌数 = 总配额 - 剩余
        long currentCount = limit - availablePermits;
        
        return new Decision(allowed, currentCount, limit);
    }
}

