package com.ming.imapicontract.file;

/**
 * 文件服务 HTTP 契约路径常量。
 */
public final class FileApiPaths {

    public static final String BASE = "/api/file";
    public static final String INTERNAL_BASE = "/internal";
    public static final String INTERNAL_UPLOAD = INTERNAL_BASE + "/upload";
    public static final String INTERNAL_CONSUME_UPLOAD_TOKEN = INTERNAL_BASE + "/upload-token/consume";
    public static final String INTERNAL_DOWNLOAD_URL = INTERNAL_BASE + "/download-url";
    public static final String INTERNAL_METADATA_GET = INTERNAL_BASE + "/meta/get";
    public static final String SIGNED_DOWNLOAD = "/files/download";

    private FileApiPaths() {
    }
}
