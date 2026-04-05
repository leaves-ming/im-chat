package com.ming.imapicontract.social;

import java.util.Date;

/**
 * 联系人项 DTO。
 */
public record ContactItemDTO(Long ownerUserId,
                             Long peerUserId,
                             Integer relationStatus,
                             String source,
                             String alias,
                             Date createdAt,
                             Date updatedAt) {
}
