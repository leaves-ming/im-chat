package com.ming.imsocialservice.dao;

import lombok.Data;

import java.util.Date;

/**
 * 群资料 DO。
 */
@Data
public class SocialGroupDO {

    private Long id;
    private String groupNo;
    private Long ownerUserId;
    private String name;
    private String notice;
    private Integer status;
    private Integer memberLimit;
    private Date createdAt;
    private Date updatedAt;
}
