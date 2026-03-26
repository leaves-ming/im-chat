package com.ming.imchatserver.service;

/**
 * 群服务业务错误码。
 */
public enum GroupErrorCode {
    INVALID_PARAM,
    GROUP_NOT_FOUND,
    GROUP_NOT_ACTIVE,
    GROUP_FULL,
    OWNER_CANNOT_QUIT
}
