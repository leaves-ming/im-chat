package com.ming.imapicontract.social;

/**
 * 联系人操作响应。
 */
public record ContactOperateResponse(boolean success, boolean idempotent) {
}
