package com.ming.imchatserver.service;

/**
 * 群服务业务异常。
 */
public class GroupBizException extends RuntimeException {

    private final GroupErrorCode code;

    public GroupBizException(GroupErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public GroupErrorCode getCode() {
        return code;
    }
}
