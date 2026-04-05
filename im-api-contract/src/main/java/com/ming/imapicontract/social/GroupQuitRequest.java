package com.ming.imapicontract.social;

/**
 * 退群请求。
 */
public record GroupQuitRequest(Long groupId, Long userId) {
}
