package com.ming.imapicontract.social;

/**
 * 联系人单向有效关系校验请求。
 */
public record CheckContactActiveRequest(Long ownerUserId, Long peerUserId) {
}
