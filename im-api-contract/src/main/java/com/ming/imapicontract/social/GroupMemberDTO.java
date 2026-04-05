package com.ming.imapicontract.social;

import java.util.Date;

/**
 * 群成员 DTO。
 */
public record GroupMemberDTO(Long groupId,
                             Long userId,
                             Integer role,
                             Integer memberStatus,
                             Date joinedAt,
                             Date mutedUntil,
                             Date createdAt,
                             Date updatedAt) {
}
