package com.ming.imchatserver.dao;

import lombok.Data;

import java.util.Date;

/**
 * 上传凭证实体（映射 im_upload_token）。
 */
@Data
public class UploadTokenDO {
    private Long id;
    private String uploadToken;
    private String fileId;
    private Long ownerUserId;
    private String status;
    private Date expireAt;
    private Date boundAt;
    private Date createdAt;
}

