package com.ming.imchatserver.dao;

import lombok.Data;

import java.util.Date;

/**
 * 群消息实体（映射 im_group_message）。
 */
@Data
public class GroupMessageDO {
    private Long id;
    private Long groupId;
    private Long seq;
    private String serverMsgId;
    private String clientMsgId;
    private Long fromUserId;
    private String msgType;
    /**
     * JSON 字符串（DB: json），服务层负责编解码为文本内容。
     */
    private String content;
    private Integer status;
    private Date createdAt;
}
