package com.ming.imapicontract.message;

import java.util.Date;

/**
 * ACK 状态推进响应。
 */
public record AckMessageStatusResponse(MessageDTO message,
                                       String status,
                                       int updated,
                                       Date ackAt,
                                       boolean statusNotifyAppended) {
}
