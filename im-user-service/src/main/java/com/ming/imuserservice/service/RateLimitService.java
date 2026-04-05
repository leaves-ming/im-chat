package com.ming.imuserservice.service;

/**
 * 固定窗口限流服务。
 */
public interface RateLimitService {

    record Decision(boolean allowed, long currentCount, long limit) {
    }

    Decision checkAndIncrement(String scope, String dimension, String subject, long limit, long windowSeconds);

    void reset(String scope, String dimension, String subject, long windowSeconds);
}
