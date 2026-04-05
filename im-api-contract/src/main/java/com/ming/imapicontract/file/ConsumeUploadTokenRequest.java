package com.ming.imapicontract.file;

/**
 * 消费 uploadToken 请求。
 */
public record ConsumeUploadTokenRequest(String rawIncomingContent, Long senderUserId) {
}
