package com.ming.imchatserver.redis;

/**
 * Redis key 前缀。
 */
public final class RedisPrefixes {

    public static final String PRESENCE = "im:presence:";
    public static final String IDEMPOTENT = "im:idempotent:";
    public static final String RATE_LIMIT = "im:rate_limit:";
    public static final String COORD = "im:coord:";

    private RedisPrefixes() {
    }
}

