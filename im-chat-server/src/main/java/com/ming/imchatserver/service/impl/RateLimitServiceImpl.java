package com.ming.imchatserver.service.impl;

import com.ming.imchatserver.redis.RedisKeyFactory;
import com.ming.imchatserver.service.RateLimitService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 固定窗口限流实现，Lua脚本保证incr+expire原子性。
 */
@Service
public class RateLimitServiceImpl implements RateLimitService {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisKeyFactory redisKeyFactory;
    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT;

    static {
        RATE_LIMIT_SCRIPT = new DefaultRedisScript<>();
        RATE_LIMIT_SCRIPT.setScriptText("""
                local key = KEYS[1]
                local window = tonumber(ARGV[1])
                local current = redis.call('INCR', key)
                if current == 1 then
                    redis.call('EXPIRE', key, window)
                end
                return current
                """);
        RATE_LIMIT_SCRIPT.setResultType(Long.class);
    }

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
        List<String> keys = Collections.singletonList(key);
        Long current = stringRedisTemplate.execute(RATE_LIMIT_SCRIPT, keys, String.valueOf(windowSeconds));
        long count = current == null ? 0L : current;
        return new Decision(count <= limit, count, limit);
    }
}

