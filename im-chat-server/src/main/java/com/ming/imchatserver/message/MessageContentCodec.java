package com.ming.imchatserver.message;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 消息内容编解码工具。
 *
 * <p>该类负责在三种表示之间做转换和校验：</p>
 * <p>1. 协议入参：客户端通过 WebSocket 上送的 {@link JsonNode} 内容。</p>
 * <p>2. 存储内容：消息落库时写入数据库的字符串。</p>
 * <p>3. 协议出参：服务端向客户端回包时写入 JSON 的内容节点。</p>
 *
 * <p>目前支持两种消息类型：</p>
 * <p>1. {@code TEXT}：协议层是字符串，存储层按 JSON 字符串编码。</p>
 * <p>2. {@code FILE}：协议层是仅包含 {@code uploadToken} 的对象，存储层直接保存该对象的 JSON。</p>
 * @author ming
 */
public final class MessageContentCodec {

    /**
     * 文本消息类型。
     */
    public static final String MSG_TYPE_TEXT = "TEXT";

    /**
     * 文件消息类型。
     */
    public static final String MSG_TYPE_FILE = "FILE";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MessageContentCodec() {
    }

    /**
     * 规范化消息类型。
     *
     * <p>当前仅显式识别 {@code FILE}，其余取值一律按 {@code TEXT} 处理。</p>
     *
     * @param msgType 原始消息类型
     * @return 规范化后的消息类型常量
     */
    public static String normalizeMsgType(String msgType) {
        if (MSG_TYPE_FILE.equalsIgnoreCase(msgType)) {
            return MSG_TYPE_FILE;
        }
        return MSG_TYPE_TEXT;
    }

    /**
     * 校验客户端上送的内容，并序列化为后续流程可复用的标准字符串。
     *
     * <p>{@code TEXT} 消息要求内容非空，返回纯文本字符串。</p>
     * <p>{@code FILE} 消息要求内容是仅包含 {@code uploadToken} 的对象，返回标准化后的 JSON 字符串。</p>
     *
     * @param msgType 原始消息类型
     * @param contentNode 客户端上送的内容节点
     * @return 校验后的标准内容字符串
     */
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

    /**
     * 将业务内容编码为数据库存储格式。
     *
     * <p>{@code TEXT} 会编码成 JSON 字符串，避免引号、换行等字符在存储和回放时语义不一致。</p>
     * <p>{@code FILE} 已经是标准 JSON，对象字符串直接落库。</p>
     *
     * @param msgType 消息类型
     * @param content 业务内容
     * @return 可直接落库的字符串
     */
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

    /**
     * 将数据库中的内容还原为业务层可使用的内容字符串。
     *
     * <p>{@code TEXT} 会从 JSON 字符串解码为普通文本；若历史数据格式不规范，则回退返回原值。</p>
     * <p>{@code FILE} 直接返回原始 JSON 字符串。</p>
     *
     * @param msgType 消息类型
     * @param storedContent 数据库存储的内容
     * @return 还原后的业务内容
     */
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

    /**
     * 将业务内容写入协议对象的指定字段。
     *
     * @param target 目标协议对象
     * @param fieldName 字段名
     * @param msgType 消息类型
     * @param content 业务内容
     */
    public static void writeProtocolContent(ObjectNode target, String fieldName, String msgType, String content) {
        JsonNode node = toProtocolContentNode(msgType, content);
        target.set(fieldName, node);
    }

    /**
     * 将业务内容转换为协议层的 JSON 节点。
     *
     * <p>{@code TEXT} 返回文本节点；{@code FILE} 返回对象节点。</p>
     *
     * @param msgType 消息类型
     * @param content 业务内容
     * @return 可直接写入协议对象的内容节点
     */
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

    /**
     * 从文件消息原始内容中提取上传令牌。
     *
     * <p>该方法用于只关心 {@code uploadToken} 的场景，会校验内容必须是仅包含该字段的对象。</p>
     *
     * @param rawContent 文件消息原始内容
     * @return 上传令牌
     */
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
