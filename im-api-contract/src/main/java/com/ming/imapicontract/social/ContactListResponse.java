package com.ming.imapicontract.social;

import java.util.List;

/**
 * 联系人列表响应。
 */
public record ContactListResponse(List<ContactItemDTO> items, Long nextCursor, boolean hasMore) {
}
