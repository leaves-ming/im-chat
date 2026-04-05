package com.ming.imapicontract.social;

import java.util.List;

/**
 * 群成员列表响应。
 */
public record GroupMemberListResponse(List<GroupMemberDTO> items, Long nextCursor, boolean hasMore) {
}
