package com.ming.imchatserver.service;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 分布式协调能力，协调状态不是业务真相。
 */
public interface DistributedCoordinationService {

    boolean executeIfLeader(String scope, String resourceId, Duration waitTimeout, Duration leaseTimeout, Runnable task);

    <T> T executeWithLockOrLocalFallback(String scope,
                                        String resourceId,
                                        Duration waitTimeout,
                                        Duration leaseTimeout,
                                        Supplier<T> task,
                                        Supplier<T> fallback);

    boolean markOnce(String scope, String markerId, Duration ttl);
}

