package com.ming.imapicontract.social;

/**
 * 群消息撤回权限校验请求。
 */
public record CheckGroupRecallPermissionRequest(Long groupId, Long operatorUserId, Long targetUserId) {
}
