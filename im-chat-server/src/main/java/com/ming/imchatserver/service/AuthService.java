package com.ming.imchatserver.service;

import java.util.Optional;

/**
 * 认证服务接口。
 * <p>
 * 负责登录、Token 校验与 Token 解析，供 HTTP 登录接口与 WebSocket 握手鉴权使用。
 */
public interface AuthService {

    /**
     * 执行用户名密码登录并生成 token。
     *
     * @param username 用户名
     * @param password 明文密码
     * @return 登录结果，包含是否成功及 token 信息
     */
    AuthResult login(String username, String password);

    /**
     * 校验 token 是否合法且未过期。
     *
     * @param token JWT token
     * @return 合法返回 true，否则 false
     */
    boolean verifyToken(String token);

    /**
     * 解析 token 中的用户信息。
     *
     * @param token JWT token
     * @return 解析成功返回用户信息，否则返回空
     */
    Optional<AuthUser> parseToken(String token);

    /** 登录返回模型。 */
    class AuthResult {
        public boolean success;
        public String token;
        public long userId;
    }

    /** token 解析后的用户模型。 */
    class AuthUser {
        public long userId;
        public String username;
    }
}
