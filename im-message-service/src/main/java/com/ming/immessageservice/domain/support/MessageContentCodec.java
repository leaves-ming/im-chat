package com.ming.immessageservice.domain.support;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 消息内容存储编解码。
 */
public final class MessageContentCodec {

    public static final String MSG_TYPE_TEXT = "TEXT";
    public static final String MSG_TYPE_FILE = "FILE";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MessageContentCodec() {
    }

    public static String normalizeMsgType(String msgType) {
        if (MSG_TYPE_FILE.equalsIgnoreCase(msgType)) {
            return MSG_TYPE_FILE;
        }
        return MSG_TYPE_TEXT;
    }

    public static String encodeForStorage(String msgType, String content) {
        if (MSG_TYPE_FILE.equalsIgnoreCase(normalizeMsgType(msgType))) {
            return content;
        }
        try {
            return MAPPER.writeValueAsString(content == null ? "" : content);
        } catch (Exception ex) {
            throw new IllegalStateException("encode text content failed", ex);
        }
    }

    public static String decodeFromStorage(String msgType, String storedContent) {
        if (storedContent == null) {
            return null;
        }
        if (MSG_TYPE_FILE.equalsIgnoreCase(normalizeMsgType(msgType))) {
            return storedContent;
        }
        try {
            return MAPPER.readValue(storedContent, String.class);
        } catch (Exception ex) {
            return storedContent;
        }
    }
}
