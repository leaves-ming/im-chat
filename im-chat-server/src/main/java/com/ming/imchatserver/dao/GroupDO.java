package com.ming.imchatserver.dao;

import lombok.Data;

import java.util.Date;

/**
 * 群实体（映射 im_group）。
 * @author ming
 */
@Data
public class GroupDO {
    /** 主键 ID。 */
    private Long id;
    /** 群编号（对外标识）。 */
    private String groupNo;
    /** 群主用户 ID。 */
    private Long ownerUserId;
    /** 群名称。 */
    private String name;
    /** 群公告。 */
    private String notice;
    /** 群状态。 */
    private Integer status;
    /** 成员上限。 */
    private Integer memberLimit;
    /** 创建时间。 */
    private Date createdAt;
    /** 更新时间。 */
    private Date updatedAt;
}
