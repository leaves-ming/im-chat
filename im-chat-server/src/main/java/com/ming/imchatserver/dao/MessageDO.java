package com.ming.imchatserver.dao;

import lombok.Data;

import java.util.Date;

/**
 * 聊天消息实体（映射 im_message 表）。
 * @author ming
 */
@Data
public class MessageDO {
    /** 自增主键。 */
    private Long id;
    /** 服务端消息 ID（全局唯一）。 */
    private String serverMsgId;
    /** 客户端消息 ID（用于幂等）。 */
    private String clientMsgId;
    /** 发送方用户 ID。 */
    private Long fromUserId;
    /** 接收方用户 ID。 */
    private Long toUserId;
    /** 消息类型（TEXT/FILE）。 */
    private String msgType;
    /** 消息内容。 */
    private String content;
    /** 消息状态（SENT/DELIVERED/ACKED）。 */
    private String status;
    /** 创建时间。 */
    private Date createdAt;
    /** 送达时间。 */
    private Date deliveredAt;
    /** 已读/确认时间。 */
    private Date ackedAt;
    /** 撤回时间。 */
    private Date retractedAt;
    /** 撤回操作人。 */
    private Long retractedBy;
}
