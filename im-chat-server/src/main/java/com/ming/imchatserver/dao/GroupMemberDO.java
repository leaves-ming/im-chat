package com.ming.imchatserver.dao;

import lombok.Data;

import java.util.Date;

/**
 * 群成员实体（映射 im_group_member）。
 */
@Data
public class GroupMemberDO {
    private Long id;
    private Long groupId;
    private Long userId;
    private Integer role;
    private Integer memberStatus;
    private Date joinedAt;
    private Date mutedUntil;
    private Date createdAt;
    private Date updatedAt;
}
