package com.ming.imchatserver.application.model;

import java.util.Date;
import java.util.List;

public record SingleMessagePage(List<SingleMessageView> messages,
                                boolean hasMore,
                                Date nextCursorCreatedAt,
                                Long nextCursorId) {
}
