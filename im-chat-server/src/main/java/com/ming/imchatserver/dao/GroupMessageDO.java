package com.ming.imchatserver.dao;

import lombok.Data;

import java.util.Date;

/**
 * 群消息实体（映射 im_group_message）。
 * @author ming
 */
@Data
public class GroupMessageDO {
    /** 主键 ID。 */
    private Long id;
    /** 群 ID。 */
    private Long groupId;
    /** 群内递增序号。 */
    private Long seq;
    /** 服务端消息 ID（全局唯一）。 */
    private String serverMsgId;
    /** 客户端消息 ID（用于幂等）。 */
    private String clientMsgId;
    /** 发送方用户 ID。 */
    private Long fromUserId;
    /** 消息类型（TEXT/FILE）。 */
    private String msgType;
    /**
     * JSON 字符串（DB: json），服务层负责编解码为文本内容。
     */
    private String content;
    /** 消息状态。 */
    private Integer status;
    /** 创建时间。 */
    private Date createdAt;
    /** 撤回时间。 */
    private Date retractedAt;
    /** 撤回操作人。 */
    private Long retractedBy;
}
