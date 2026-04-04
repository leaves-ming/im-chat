package com.ming.imapicontract.message;

/**
 * 单聊消息落库响应。
 */
public record PersistSingleMessageResponse(String clientMsgId,
                                           String serverMsgId,
                                           boolean createdNew) {
}
