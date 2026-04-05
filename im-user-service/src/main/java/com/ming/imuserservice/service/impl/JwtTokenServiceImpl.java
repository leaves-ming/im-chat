package com.ming.imuserservice.service.impl;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.ming.imapicontract.user.IntrospectTokenResponse;
import com.ming.imuserservice.config.JwtTokenProperties;
import com.ming.imuserservice.service.JwtTokenService;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * JWT 服务实现。
 */
@Service
public class JwtTokenServiceImpl implements JwtTokenService {

    private final JwtTokenProperties jwtTokenProperties;

    public JwtTokenServiceImpl(JwtTokenProperties jwtTokenProperties) {
        this.jwtTokenProperties = jwtTokenProperties;
    }

    @Override
    public String issueToken(Long userId, String username) {
        Algorithm algorithm = Algorithm.HMAC256(jwtTokenProperties.getSecret());
        Date now = new Date();
        Date expiresAt = new Date(now.getTime() + expiresInSeconds() * 1000L);
        return JWT.create()
                .withIssuer(jwtTokenProperties.getIssuer())
                .withClaim("userId", userId)
                .withClaim("username", username)
                .withIssuedAt(now)
                .withExpiresAt(expiresAt)
                .sign(algorithm);
    }

    @Override
    public IntrospectTokenResponse introspect(String token) {
        if (token == null || token.isBlank()) {
            return new IntrospectTokenResponse(false, null, null, null, null);
        }
        try {
            JWTVerifier verifier = JWT.require(Algorithm.HMAC256(jwtTokenProperties.getSecret()))
                    .withIssuer(jwtTokenProperties.getIssuer())
                    .build();
            DecodedJWT jwt = verifier.verify(token);
            Long issuedAt = jwt.getIssuedAt() == null ? null : jwt.getIssuedAt().getTime() / 1000L;
            Long expiresAt = jwt.getExpiresAt() == null ? null : jwt.getExpiresAt().getTime() / 1000L;
            return new IntrospectTokenResponse(
                    true,
                    jwt.getClaim("userId").asLong(),
                    jwt.getClaim("username").asString(),
                    issuedAt,
                    expiresAt
            );
        } catch (JWTVerificationException ex) {
            return new IntrospectTokenResponse(false, null, null, null, null);
        }
    }

    @Override
    public long expiresInSeconds() {
        return jwtTokenProperties.getExpireSeconds();
    }
}
