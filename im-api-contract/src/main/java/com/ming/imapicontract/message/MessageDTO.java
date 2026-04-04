package com.ming.imapicontract.message;

import java.util.Date;

/**
 * 消息传输对象。
 */
public record MessageDTO(Long id,
                         String serverMsgId,
                         String clientMsgId,
                         Long fromUserId,
                         Long toUserId,
                         String msgType,
                         String content,
                         String status,
                         Date createdAt,
                         Date deliveredAt,
                         Date ackedAt,
                         Date retractedAt,
                         Long retractedBy) {
}
