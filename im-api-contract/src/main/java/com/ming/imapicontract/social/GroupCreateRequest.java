package com.ming.imapicontract.social;

/**
 * 创建群组请求。
 */
public record GroupCreateRequest(Long ownerUserId, String name, Integer memberLimit) {
}
