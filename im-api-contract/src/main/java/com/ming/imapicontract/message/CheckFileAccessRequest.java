package com.ming.imapicontract.message;

/**
 * 文件消息访问校验请求。
 */
public record CheckFileAccessRequest(String fileId, Long userId) {
}
