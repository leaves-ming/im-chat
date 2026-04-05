package com.ming.imchatserver.application.model;

import java.util.Date;

public record SingleSyncCursor(Date cursorCreatedAt, Long cursorId) {
}
