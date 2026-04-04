package com.ming.imchatserver.dao;

import lombok.Data;

import java.util.Date;

/**
 * 事务外盒消息实体（映射 im_message_outbox）。
 * <p>
 * 状态口径：
 * 0=NEW, 1=SENT, 2=FAILED, 3=DLQ, 4=PROCESSING。
 * 其中 backlog 统计包含 NEW/FAILED/PROCESSING，PROCESSING 可由 reclaim 任务按超时阈值回收。
 * @author ming
 */
@Data
public class OutboxMessageDO {
    /** 主键 ID。 */
    private Long id;
    /** 事件 ID（全局唯一）。 */
    private String eventId;
    /** 关联消息 ID。 */
    private Long messageId;
    /** 分发 topic。 */
    private String topic;
    /** 分发 tag。 */
    private String tag;
    /** 分发载荷（JSON）。 */
    private String payload;
    /** 0=NEW,1=SENT,2=FAILED,3=DLQ,4=PROCESSING */
    private Integer status;
    /** 重试次数。 */
    private Integer retryCount;
    /** 下次重试时间。 */
    private Date nextRetryAt;
    /** 进入 PROCESSING 的时间。 */
    private Date processingAt;
    /** 发送成功时间。 */
    private Date sentAt;
    /** 失败原因。 */
    private String failReason;
    /** 创建时间。 */
    private Date createdAt;
    /** 更新时间。 */
    private Date updatedAt;
}
