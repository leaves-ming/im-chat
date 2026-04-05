package com.ming.imchatserver.application.model;

import java.util.Date;

public record GroupMessageView(Long id,
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
