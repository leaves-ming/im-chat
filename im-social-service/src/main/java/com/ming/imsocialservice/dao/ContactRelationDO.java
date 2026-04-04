package com.ming.imsocialservice.dao;

import lombok.Data;

import java.util.Date;

/**
 * 联系人关系 DO。
 */
@Data
public class ContactRelationDO {

    private Long id;
    private Long ownerUserId;
    private Long peerUserId;
    private Integer relationStatus;
    private String source;
    private String alias;
    private Date createdAt;
    private Date updatedAt;
}
