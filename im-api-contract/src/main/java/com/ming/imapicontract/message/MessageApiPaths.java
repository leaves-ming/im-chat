package com.ming.imapicontract.message;

/**
 * 消息服务 HTTP 契约路径常量。
 */
public final class MessageApiPaths {

    public static final String BASE = "/api/message";
    public static final String SINGLE_PERSIST = "/single/persist";
    public static final String ACK = "/single/ack";
    public static final String PULL_OFFLINE = "/single/offline/pull";
    public static final String ADVANCE_CURSOR = "/single/offline/cursor/advance";
    public static final String RECALL = "/single/recall";
    public static final String GROUP_PERSIST = "/group/persist";
    public static final String GROUP_PULL_OFFLINE = "/group/offline/pull";
    public static final String GROUP_RECALL = "/group/recall";
    public static final String GROUP_GET = "/group/get";
    public static final String FILE_ACCESS_CHECK = "/file/access/check";

    private MessageApiPaths() {
    }
}
