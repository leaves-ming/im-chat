package com.ming.immessageservice.infrastructure.dao;

import lombok.Data;

import java.util.Date;

/**
 * 群消息实体。
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
    private String content;
    private Integer status;
    private Date createdAt;
    private Date retractedAt;
    private Long retractedBy;
}
