package com.ming.imapicontract.social;

/**
 * 群成员列表请求。
 */
public record GroupMemberListRequest(Long groupId, Long cursorUserId, Integer limit) {
}
