package com.ming.imapicontract.message;

import java.util.Date;

/**
 * 单聊同步游标。
 */
public record SyncCursorDTO(Date cursorCreatedAt, Long cursorId) {
}
