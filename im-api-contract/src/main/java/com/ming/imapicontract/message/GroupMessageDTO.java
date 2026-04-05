package com.ming.imapicontract.message;

import java.util.Date;

/**
 * 群消息传输对象。
 */
public record GroupMessageDTO(Long id,
                              Long groupId,
                              Long seq,
                              String serverMsgId,
                              String clientMsgId,
                              Long fromUserId,
                              String msgType,
                              String content,
                              Integer status,
                              Date createdAt,
                              Date retractedAt,
                              Long retractedBy) {
}
