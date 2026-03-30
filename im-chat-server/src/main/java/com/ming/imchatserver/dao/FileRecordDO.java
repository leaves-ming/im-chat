package com.ming.imchatserver.dao;

import lombok.Data;

import java.util.Date;

/**
 * 文件元数据实体（映射 im_file_record）。
 */
@Data
public class FileRecordDO {
    private Long id;
    private String fileId;
    private Long ownerUserId;
    private String contentType;
    private Long size;
    private String storageKey;
    private String originalFileName;
    private Date createdAt;
}
