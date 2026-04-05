package com.ming.imapicontract.user;

/**
 * token 解析结果。
 */
public record IntrospectTokenResponse(
        boolean active,
        Long userId,
        String username,
        Long issuedAt,
        Long expiresAt
) {
}
