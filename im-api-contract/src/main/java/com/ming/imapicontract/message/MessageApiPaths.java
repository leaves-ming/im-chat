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

    private MessageApiPaths() {
    }
}
