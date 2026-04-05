package com.ming.imapicontract.social;

/**
 * 入群请求。
 */
public record GroupJoinRequest(Long groupId, Long userId) {
}
