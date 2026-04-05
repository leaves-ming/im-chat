package com.ming.imapicontract.message;

/**
 * 群聊消息落库请求。
 */
public record PersistGroupMessageRequest(Long groupId,
                                         Long fromUserId,
                                         String clientMsgId,
                                         String msgType,
                                         String content) {
}
