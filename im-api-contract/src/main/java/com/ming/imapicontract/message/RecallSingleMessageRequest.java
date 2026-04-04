package com.ming.imapicontract.message;

/**
 * 单聊消息撤回请求。
 */
public record RecallSingleMessageRequest(Long operatorUserId,
                                         String serverMsgId,
                                         long recallWindowSeconds) {
}
