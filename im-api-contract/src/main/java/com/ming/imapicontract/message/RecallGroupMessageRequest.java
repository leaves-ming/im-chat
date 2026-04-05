package com.ming.imapicontract.message;

/**
 * 群聊消息撤回请求。
 */
public record RecallGroupMessageRequest(Long operatorUserId,
                                        String serverMsgId,
                                        long recallWindowSeconds) {
}
