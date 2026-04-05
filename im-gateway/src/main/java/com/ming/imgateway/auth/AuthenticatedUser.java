package com.ming.imgateway.auth;

/**
 * introspect 成功后的用户信息。
 */
public record AuthenticatedUser(Long userId, String username, String token) {
}
