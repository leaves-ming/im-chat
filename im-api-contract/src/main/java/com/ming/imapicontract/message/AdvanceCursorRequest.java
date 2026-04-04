package com.ming.imapicontract.message;

import java.util.Date;

/**
 * 单聊同步游标推进请求。
 */
public record AdvanceCursorRequest(Long userId,
                                   String deviceId,
                                   Date cursorCreatedAt,
                                   Long cursorId) {
}
