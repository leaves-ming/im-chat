package com.ming.imapicontract.social;

/**
 * 群消息撤回权限校验响应。
 */
public record CheckGroupRecallPermissionResponse(boolean allowed, String reasonCode) {
}
