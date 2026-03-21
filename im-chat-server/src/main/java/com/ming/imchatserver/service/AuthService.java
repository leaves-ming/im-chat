package com.ming.imchatserver.service;

import java.util.Optional;

public interface AuthService {
    // 占位登录接口，返回 token（示例）
    AuthResult login(String username, String password);

    boolean verifyToken(String token);

    Optional<AuthUser> parseToken(String token);

    class AuthResult {
        public boolean success;
        public String token;
        public long userId;
    }

    class AuthUser {
        public long userId;
        public String username;
    }
}

