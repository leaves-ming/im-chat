package com.ming.immessageservice.infrastructure.dao;

import lombok.Data;

import java.util.Date;

/**
 * 单聊消息表映射。
 */
@Data
public class MessageDO {
    private Long id;
    private String serverMsgId;
    private String clientMsgId;
    private Long fromUserId;
    private Long toUserId;
    private String msgType;
    private String content;
    private String status;
    private Date createdAt;
    private Date deliveredAt;
    private Date ackedAt;
    private Date retractedAt;
    private Long retractedBy;
}
