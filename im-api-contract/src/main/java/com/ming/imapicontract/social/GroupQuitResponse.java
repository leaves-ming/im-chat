package com.ming.imapicontract.social;

/**
 * 退群响应。
 */
public record GroupQuitResponse(boolean quit, boolean idempotent) {
}
