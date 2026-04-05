package com.ming.imapicontract.user;

/**
 * 登录成功返回。
 */
public record LoginResponse(
        Long userId,
        String username,
        String token,
        Long expiresIn
) {
}
