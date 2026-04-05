package com.ming.imfileservice.message;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * FILE 消息协议编解码工具。
 */
public final class MessageContentCodec {

    public static final String MSG_TYPE_FILE = "FILE";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MessageContentCodec() {
    }

    public static String extractIncomingUploadToken(String rawIncomingContent) {
        try {
            JsonNode node = MAPPER.readTree(rawIncomingContent);
            if (node == null || !node.isObject()) {
                throw new IllegalArgumentException("content must be object when msgType=FILE");
            }
            if (node.size() != 1 || !node.has("uploadToken")) {
                throw new IllegalArgumentException("content only supports uploadToken when msgType=FILE");
            }
            String uploadToken = requiredText(node, "uploadToken");
            ObjectNode canonical = MAPPER.createObjectNode();
            canonical.put("uploadToken", uploadToken);
            return uploadToken;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid file content", ex);
        }
    }

    private static String requiredText(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException(fieldName + " required");
        }
        String text = value.asText(null);
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " required");
        }
        return text;
    }
}
