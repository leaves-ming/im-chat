package com.ming.imchatserver.dao;

import lombok.Data;

import java.util.Date;

/**
 * 文件元数据实体（映射 im_file_record）。
 * @author ming
 */
@Data
public class FileRecordDO {
    /** 主键 ID。 */
    private Long id;
    /** 文件 ID（业务唯一标识）。 */
    private String fileId;
    /** 文件所有者用户 ID。 */
    private Long ownerUserId;
    /** 文件内容类型（MIME）。 */
    private String contentType;
    /** 文件大小（字节）。 */
    private Long size;
    /** 存储键（对象存储路径）。 */
    private String storageKey;
    /** 原始文件名。 */
    private String originalFileName;
    /** 创建时间。 */
    private Date createdAt;
}
