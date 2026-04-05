package com.ming.imchatserver.service;

/**
 * 支持直接判定单聊权限的能力接口。
 */
public interface SingleChatPermissionCapable {

    boolean isSingleChatAllowed(Long fromUserId, Long toUserId);
}
