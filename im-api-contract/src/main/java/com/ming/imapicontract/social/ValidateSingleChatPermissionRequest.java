package com.ming.imapicontract.social;

/**
 * 单聊权限校验请求。
 */
public record ValidateSingleChatPermissionRequest(Long fromUserId, Long toUserId) {
}
