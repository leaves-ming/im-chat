package com.ming.imuserservice.service;

import com.ming.imapicontract.user.IntrospectTokenResponse;

/**
 * JWT 服务。
 */
public interface JwtTokenService {

    String issueToken(Long userId, String username);

    IntrospectTokenResponse introspect(String token);

    long expiresInSeconds();
}
