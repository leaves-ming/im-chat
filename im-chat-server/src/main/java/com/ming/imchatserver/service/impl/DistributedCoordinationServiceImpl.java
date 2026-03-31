package com.ming.imchatserver.service.impl;

import com.ming.imchatserver.redis.RedisKeyFactory;
import com.ming.imchatserver.service.DistributedCoordinationService;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Redisson 分布式协调实现。
 */
@Service
public class DistributedCoordinationServiceImpl implements DistributedCoordinationService {

    private static final Logger logger = LoggerFactory.getLogger(DistributedCoordinationServiceImpl.class);

    private final RedissonClient redissonClient;
    private final RedisKeyFactory redisKeyFactory;

    public DistributedCoordinationServiceImpl(ObjectProvider<RedissonClient> redissonClientProvider, RedisKeyFactory redisKeyFactory) {
        this.redissonClient = redissonClientProvider.getIfAvailable();
        this.redisKeyFactory = redisKeyFactory;
    }

    @Override
    public boolean executeIfLeader(String scope, String resourceId, Duration waitTimeout, Duration leaseTimeout, Runnable task) {
        return executeWithLockOrLocalFallback(scope, resourceId, waitTimeout, leaseTimeout, () -> {
            task.run();
            return true;
        }, () -> false);
    }

    @Override
    public <T> T executeWithLockOrLocalFallback(String scope,
                                                String resourceId,
                                                Duration waitTimeout,
                                                Duration leaseTimeout,
                                                Supplier<T> task,
                                                Supplier<T> fallback) {
        if (redissonClient == null) {
            logger.warn("redisson unavailable, fallback to local execution scope={} resourceId={}", scope, resourceId);
            return fallback.get();
        }
        String lockKey = redisKeyFactory.coordinationLock(scope, resourceId);
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        try {
            locked = lock.tryLock(waitTimeout.toMillis(), leaseTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!locked) {
                logger.warn("failed to acquire distributed lock scope={} resourceId={}", scope, resourceId);
                return fallback.get();
            }
            return task.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("interrupted while acquiring distributed lock scope={} resourceId={}", scope, resourceId, ex);
            return fallback.get();
        } catch (Exception ex) {
            logger.error("distributed coordination failed scope={} resourceId={}", scope, resourceId, ex);
            return fallback.get();
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public boolean markOnce(String scope, String markerId, Duration ttl) {
        if (redissonClient == null) {
            logger.error("redisson unavailable, can not mark distributed coordination state scope={} markerId={}", scope, markerId);
            throw new IllegalStateException("redisson unavailable");
        }
        try {
            RBucket<String> bucket = redissonClient.getBucket(redisKeyFactory.coordinationMarker(scope, markerId));
            return bucket.trySet("1", ttl.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception ex) {
            logger.error("failed to mark distributed coordination state scope={} markerId={}", scope, markerId, ex);
            throw new IllegalStateException("distributed coordination marker failed", ex);
        }
    }
}
