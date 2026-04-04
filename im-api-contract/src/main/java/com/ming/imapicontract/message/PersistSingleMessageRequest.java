package com.ming.imapicontract.message;

/**
 * 单聊消息落库请求。
 */
public record PersistSingleMessageRequest(Long fromUserId,
                                          Long targetUserId,
                                          String clientMsgId,
                                          String msgType,
                                          String content) {
}
