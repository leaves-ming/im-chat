package com.ming.imchatserver.message;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 消息内容编解码工具。
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

    public static String validateAndSerializeIncomingContent(String msgType, JsonNode contentNode) {
        String normalizedType = normalizeMsgType(msgType);
        if (MSG_TYPE_FILE.equals(normalizedType)) {
            if (contentNode == null || !contentNode.isObject()) {
                throw new IllegalArgumentException("content must be object when msgType=FILE");
            }
            String uploadToken = requiredText(contentNode, "uploadToken");
            if (contentNode.size() != 1) {
                throw new IllegalArgumentException("content only supports uploadToken when msgType=FILE");
            }
            ObjectNode canonical = MAPPER.createObjectNode();
            canonical.put("uploadToken", uploadToken);
            try {
                return MAPPER.writeValueAsString(canonical);
            } catch (Exception ex) {
                throw new IllegalStateException("serialize file message content failed", ex);
            }
        }
        String text = contentNode == null || contentNode.isNull() ? "" : contentNode.asText("");
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        return text;
    }

    public static String encodeForStorage(String msgType, String content) {
        String normalizedType = normalizeMsgType(msgType);
        if (MSG_TYPE_FILE.equals(normalizedType)) {
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
        String normalizedType = normalizeMsgType(msgType);
        if (MSG_TYPE_FILE.equals(normalizedType)) {
            return storedContent;
        }
        try {
            return MAPPER.readValue(storedContent, String.class);
        } catch (Exception ex) {
            return storedContent;
        }
    }

    public static void writeProtocolContent(ObjectNode target, String fieldName, String msgType, String content) {
        JsonNode node = toProtocolContentNode(msgType, content);
        target.set(fieldName, node);
    }

    public static JsonNode toProtocolContentNode(String msgType, String content) {
        String normalizedType = normalizeMsgType(msgType);
        if (MSG_TYPE_FILE.equals(normalizedType)) {
            try {
                return content == null ? MAPPER.createObjectNode() : MAPPER.readTree(content);
            } catch (Exception ex) {
                throw new IllegalStateException("parse file message content failed", ex);
            }
        }
        return MAPPER.getNodeFactory().textNode(content == null ? "" : content);
    }

    private static String requiredText(JsonNode node, String fieldName) {
        String value = node.path(fieldName).asText(null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    public static String extractIncomingUploadToken(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        try {
            JsonNode node = MAPPER.readTree(rawContent);
            if (node == null || !node.isObject()) {
                throw new IllegalArgumentException("content must be object when msgType=FILE");
            }
            if (node.size() != 1) {
                throw new IllegalArgumentException("content only supports uploadToken when msgType=FILE");
            }
            return requiredText(node, "uploadToken");
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid file content");
        }
    }
}
