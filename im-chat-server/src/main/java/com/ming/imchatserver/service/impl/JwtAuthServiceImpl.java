package com.ming.imchatserver.service.impl;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.ming.imchatserver.config.NettyProperties;
import com.ming.imchatserver.service.AuthService;
import com.ming.imchatserver.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Optional;

/**
 * {@link AuthService} 的 JWT 实现。
 * <p>
 * 功能包括：
 * - 登录时校验用户名密码并签发 JWT；
 * - 校验 token 合法性；
 * - 从 token 中解析用户信息。
 */
@Service
public class JwtAuthServiceImpl implements AuthService {

    @Value("${im.auth.jwt.secret:im-default-secret}")
    private String jwtSecret;

    private final NettyProperties nettyProperties;
    private final UserService userService;

    /**
     * @param nettyProperties Netty 配置（含 token 过期秒数）
     * @param userService     用户服务，用于登录时校验账号密码
     */
    public JwtAuthServiceImpl(NettyProperties nettyProperties, UserService userService) {
        this.nettyProperties = nettyProperties;
        this.userService = userService;
    }

    /**
     * 执行登录并生成 JWT token。
     */
    @Override
    public AuthResult login(String username, String password) {
        AuthResult r = new AuthResult();
        if (username == null || username.isEmpty()) {
            r.success = false;
            return r;
        }

        var userOpt = userService.findByUsername(username);
        if (userOpt.isEmpty()) {
            r.success = false;
            return r;
        }

        var core = userOpt.get();
        boolean ok = userService.verifyPassword(password, core.getPasswordHash());
        if (!ok) {
            r.success = false;
            return r;
        }

        long userId = core.getUserId();
        Algorithm alg = Algorithm.HMAC256(jwtSecret);
        Date now = new Date();
        Date exp = new Date(now.getTime() + nettyProperties.getTokenExpireSeconds() * 1000L);
        String token = JWT.create()
                .withClaim("userId", userId)
                .withClaim("username", username)
                .withIssuedAt(now)
                .withExpiresAt(exp)
                .sign(alg);

        r.success = true;
        r.token = token;
        r.userId = userId;
        return r;
    }

    /**
     * 校验 token 是否可验证且未过期。
     */
    @Override
    public boolean verifyToken(String token) {
        try {
            Algorithm alg = Algorithm.HMAC256(jwtSecret);
            JWTVerifier verifier = JWT.require(alg).build();
            verifier.verify(token);
            return true;
        } catch (JWTVerificationException ex) {
            return false;
        }
    }

    /**
     * 解析 token 中的 userId/username 信息。
     */
    @Override
    public Optional<AuthUser> parseToken(String token) {
        try {
            Algorithm alg = Algorithm.HMAC256(jwtSecret);
            JWTVerifier verifier = JWT.require(alg).build();
            DecodedJWT jwt = verifier.verify(token);
            AuthUser u = new AuthUser();
            u.userId = jwt.getClaim("userId").asLong();
            u.username = jwt.getClaim("username").asString();
            return Optional.of(u);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }
}
