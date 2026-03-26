package com.ming.imchatserver.dao;

import lombok.Data;

import java.util.Date;

/**
 * 群实体（映射 im_group）。
 */
@Data
public class GroupDO {
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
