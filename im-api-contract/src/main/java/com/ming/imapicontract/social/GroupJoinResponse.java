package com.ming.imapicontract.social;

/**
 * 入群响应。
 */
public record GroupJoinResponse(boolean joined, boolean idempotent) {
}
