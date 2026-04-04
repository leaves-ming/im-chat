package com.ming.imapicontract.message;

/**
 * ACK 状态推进请求。
 */
public record AckMessageStatusRequest(Long reporterUserId,
                                      String serverMsgId,
                                      String targetStatus) {
}
