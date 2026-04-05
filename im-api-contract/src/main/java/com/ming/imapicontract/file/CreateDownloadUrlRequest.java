package com.ming.imapicontract.file;

/**
 * 生成下载地址请求。
 */
public record CreateDownloadUrlRequest(Long requesterUserId, String fileId) {
}
