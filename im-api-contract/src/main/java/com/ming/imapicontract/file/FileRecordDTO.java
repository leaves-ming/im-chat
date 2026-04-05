package com.ming.imapicontract.file;

import java.util.Date;

/**
 * 文件元数据 DTO。
 */
public record FileRecordDTO(Long id,
                            String fileId,
                            Long ownerUserId,
                            String contentType,
                            Long size,
                            String storageKey,
                            String originalFileName,
                            Date createdAt) {
}
