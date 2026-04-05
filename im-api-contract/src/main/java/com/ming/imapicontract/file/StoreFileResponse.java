package com.ming.imapicontract.file;

/**
 * 文件上传结果。
 */
public record StoreFileResponse(String uploadToken,
                                String fileId,
                                String fileName,
                                String contentType,
                                long size,
                                String url) {
}
