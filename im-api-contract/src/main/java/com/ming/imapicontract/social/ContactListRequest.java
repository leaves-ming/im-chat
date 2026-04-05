package com.ming.imapicontract.social;

/**
 * 联系人列表请求。
 */
public record ContactListRequest(Long ownerUserId, Long cursorPeerUserId, Integer limit) {
}
