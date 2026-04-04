package com.ming.imapicontract.message;

/**
 * 离线消息拉取响应。
 */
public record PullOfflineResponse(CursorPageDTO page) {
}
