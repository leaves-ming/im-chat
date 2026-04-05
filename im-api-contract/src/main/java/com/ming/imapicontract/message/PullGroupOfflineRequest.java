package com.ming.imapicontract.message;

/**
 * 群离线消息拉取请求。
 */
public record PullGroupOfflineRequest(Long groupId,
                                      Long userId,
                                      Long cursorSeq,
                                      int limit) {
}
