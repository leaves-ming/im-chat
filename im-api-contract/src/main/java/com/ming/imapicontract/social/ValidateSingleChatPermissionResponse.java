package com.ming.imapicontract.social;

/**
 * 单聊权限校验响应。
 */
public record ValidateSingleChatPermissionResponse(boolean allowed, String reasonCode) {
}
