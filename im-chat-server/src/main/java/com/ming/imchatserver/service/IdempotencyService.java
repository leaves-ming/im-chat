package com.ming.imchatserver.service;

import java.time.Duration;

/**
 * 短期幂等/防重放服务。
 */
public interface IdempotencyService {

    boolean claimClientMessage(Long userId, String clientMsgId, Duration ttl);

    boolean consumeOnce(String scope, String token, Duration ttl);
}

