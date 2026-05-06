package com.ming.imchatserver.dao;

import lombok.Data;

import java.util.Date;

/**
 * 本地消息表实体。
 */
@Data
public class OutboxMessageDO {

    private Long id;
    private String eventId;
    private Long messageId;
    private String clientMsgId;
    private Long fromUserId;
    private String topic;
    private String tag;
    private String payload;
    private Integer status; // 0=PENDING,1=SUCCESS,2=ACKED,-1=FAILED
    private Integer ackStatus; // 0=未确认,1=已送达,2=已读
    private Integer retryCount;
    private Integer maxRetryCount;
    private Date nextRetryAt;
    private Date processingAt;
    private Date sentAt;
    private String failReason;
    private Date createdAt;
    private Date updatedAt;
}