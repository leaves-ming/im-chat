package com.ming.imchatserver.dao;

import lombok.Data;

import java.util.Date;

/**
 * 事务外盒消息实体（映射 im_message_outbox）。
 * <p>
 * 状态口径：
 * 0=NEW, 1=SENT, 2=FAILED, 3=DLQ, 4=PROCESSING。
 * 其中 backlog 统计包含 NEW/FAILED/PROCESSING，PROCESSING 可由 reclaim 任务按超时阈值回收。
 */
@Data
public class OutboxMessageDO {
    private Long id;
    private String eventId;
    private Long messageId;
    private String topic;
    private String tag;
    private String payload;
    /** 0=NEW,1=SENT,2=FAILED,3=DLQ,4=PROCESSING */
    private Integer status;
    private Integer retryCount;
    private Date nextRetryAt;
    private Date processingAt;
    private Date sentAt;
    private String failReason;
    private Date createdAt;
    private Date updatedAt;
}
