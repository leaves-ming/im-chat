package com.ming.imchatserver.application.model;

import java.util.Date;

public record SingleMessageView(Long id,
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
