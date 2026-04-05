package com.ming.immessageservice.infrastructure.dao;

import lombok.Data;

import java.util.Date;

/**
 * 群离线游标实体。
 */
@Data
public class GroupCursorDO {

    private Long id;
    private Long groupId;
    private Long userId;
    private Long lastPullSeq;
    private Date createdAt;
    private Date updatedAt;
}
