package com.ming.imchatserver.dao;

import lombok.Data;

import java.util.Date;

/**
 * 事务外盒消息实体（映射 im_message_outbox）。
 */
@Data
public class OutboxMessageDO {
    private Long id;
    private String eventId;
    private Long messageId;
    private String topic;
    private String tag;
    private String payload;
    /** 0=NEW,1=SENT,2=FAILED,3=DLQ */
    private Integer status;
    private Integer retryCount;
    private Date nextRetryAt;
    private Date sentAt;
    private String failReason;
    private Date createdAt;
    private Date updatedAt;
}
