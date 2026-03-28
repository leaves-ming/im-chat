package com.ming.imchatserver.dao;

import lombok.Data;

import java.util.Date;

/**
 * 联系人实体（映射 im_contact）。
 */
@Data
public class ContactDO {
    private Long id;
    private Long ownerUserId;
    private Long peerUserId;
    private Integer relationStatus;
    private String source;
    private String alias;
    private Date createdAt;
    private Date updatedAt;
}
