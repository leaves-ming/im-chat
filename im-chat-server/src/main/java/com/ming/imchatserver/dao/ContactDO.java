package com.ming.imchatserver.dao;

import lombok.Data;

import java.util.Date;

/**
 * 联系人实体（映射 im_contact）。
 * @author ming
 */
@Data
public class ContactDO {
    /** 主键 ID。 */
    private Long id;
    /** 联系人归属用户 ID。 */
    private Long ownerUserId;
    /** 对端用户 ID。 */
    private Long peerUserId;
    /** 关系状态。 */
    private Integer relationStatus;
    /** 联系来源。 */
    private String source;
    /** 备注别名。 */
    private String alias;
    /** 创建时间。 */
    private Date createdAt;
    /** 更新时间。 */
    private Date updatedAt;
}
