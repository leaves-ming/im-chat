package com.ming.imchatserver.application.model;

import java.util.List;

public record ContactPage(List<ContactView> items, Long nextCursor, boolean hasMore) {
}
