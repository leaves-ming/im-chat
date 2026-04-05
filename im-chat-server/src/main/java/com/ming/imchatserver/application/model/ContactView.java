package com.ming.imchatserver.application.model;

import java.util.Date;

public record ContactView(Long ownerUserId,
                          Long peerUserId,
                          Integer relationStatus,
                          String source,
                          String alias,
                          Date createdAt,
                          Date updatedAt) {
}
