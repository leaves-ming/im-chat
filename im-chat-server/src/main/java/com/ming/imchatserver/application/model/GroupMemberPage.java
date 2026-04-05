package com.ming.imchatserver.application.model;

import java.util.List;

public record GroupMemberPage(List<GroupMemberView> items, Long nextCursor, boolean hasMore) {
}
