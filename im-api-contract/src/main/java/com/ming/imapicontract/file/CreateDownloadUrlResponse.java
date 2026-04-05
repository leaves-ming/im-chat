package com.ming.imapicontract.file;

/**
 * 生成下载地址结果。
 */
public record CreateDownloadUrlResponse(String downloadUrl, long expireAt) {
}
