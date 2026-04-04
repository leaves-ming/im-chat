package com.ming.imsocialservice.dao;

import lombok.Data;

import java.util.Date;

/**
 * 群成员关系 DO。
 */
@Data
public class SocialGroupMemberDO {

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
