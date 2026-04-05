package com.ming.imapicontract.social;

/**
 * 联系人操作请求。
 */
public record ContactOperateRequest(Long ownerUserId, Long peerUserId) {
}
