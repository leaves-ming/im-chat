package com.ming.imapicontract.user;

/**
 * 用户/认证域接口路径常量。
 */
public final class UserApiPaths {

    public static final String BASE = "/api/user";
    public static final String AUTH_LOGIN = "/auth/login";
    public static final String AUTH_INTROSPECT = "/auth/introspect";
    public static final String QUERY_BY_ID = "/query/by-id";
    public static final String QUERY_BY_USERNAME = "/query/by-username";

    private UserApiPaths() {
    }
}
