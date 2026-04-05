package com.ming.imapicontract.file;

/**
 * 文件上传内部请求。
 */
public record StoreFileRequest(Long ownerUserId,
                               String fileName,
                               String contentType,
                               long size,
                               byte[] bytes) {
}
