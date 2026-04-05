package com.ming.imchatserver.application.model;

import java.util.List;

public record GroupMessagePage(List<GroupMessageView> messages, boolean hasMore, Long nextCursorSeq) {
}
