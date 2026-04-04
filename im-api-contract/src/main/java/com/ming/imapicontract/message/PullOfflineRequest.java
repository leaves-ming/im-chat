package com.ming.imapicontract.message;

/**
 * 离线消息拉取请求。
 */
public record PullOfflineRequest(Long userId,
                                 String deviceId,
                                 SyncCursorDTO syncCursor,
                                 int limit,
                                 boolean useCheckpoint) {
}
