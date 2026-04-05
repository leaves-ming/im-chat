package com.ming.imapicontract.user;

/**
 * 按 userId 查询用户。
 */
public record QueryUserByIdRequest(Long userId) {
}
