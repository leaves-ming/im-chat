package com.ming.imapicontract.user;

/**
 * 登录请求。
 */
public record LoginRequest(
        String username,
        String password,
        String clientIp,
        String deviceId
) {
}
