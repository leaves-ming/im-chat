package com.ming.imchatserver.dao;

import lombok.Data;

import java.util.Date;

/**
 * 上传凭证实体（映射 im_upload_token）。
 * @author ming
 */
@Data
public class UploadTokenDO {
    /** 主键 ID。 */
    private Long id;
    /** 上传令牌。 */
    private String uploadToken;
    /** 文件 ID。 */
    private String fileId;
    /** 所有者用户 ID。 */
    private Long ownerUserId;
    /** 令牌状态。 */
    private String status;
    /** 过期时间。 */
    private Date expireAt;
    /** 绑定时间。 */
    private Date boundAt;
    /** 创建时间。 */
    private Date createdAt;
}
