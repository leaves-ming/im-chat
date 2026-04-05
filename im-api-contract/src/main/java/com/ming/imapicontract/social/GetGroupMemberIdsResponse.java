package com.ming.imapicontract.social;

import java.util.List;

/**
 * 获取群成员 ID 列表响应。
 */
public record GetGroupMemberIdsResponse(List<Long> memberUserIds) {
}
