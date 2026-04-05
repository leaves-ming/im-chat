package com.ming.immessageservice.infrastructure.dao;

import lombok.Data;

import java.util.Date;

/**
 * outbox 实体。
 */
@Data
public class OutboxMessageDO {

    private Long id;
    private String eventId;
    private Long messageId;
    private String topic;
    private String tag;
    private String payload;
    private Integer status;
    private Integer retryCount;
    private Date nextRetryAt;
    private Date processingAt;
    private Date sentAt;
    private String failReason;
    private Date createdAt;
    private Date updatedAt;
}
