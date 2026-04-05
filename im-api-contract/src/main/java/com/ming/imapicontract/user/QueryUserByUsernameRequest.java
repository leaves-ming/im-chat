package com.ming.imapicontract.user;

/**
 * 按用户名查询用户。
 */
public record QueryUserByUsernameRequest(String username) {
}
