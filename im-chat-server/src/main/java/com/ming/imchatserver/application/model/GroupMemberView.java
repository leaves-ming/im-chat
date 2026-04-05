package com.ming.imchatserver.application.model;

import java.util.Date;

public record GroupMemberView(Long groupId,
                              Long userId,
                              Integer role,
                              Integer memberStatus,
                              Date joinedAt,
                              Date mutedUntil,
                              Date createdAt,
                              Date updatedAt) {
}
