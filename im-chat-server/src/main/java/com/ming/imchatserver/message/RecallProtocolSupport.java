package com.ming.imchatserver.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ming.imapicontract.message.MessageContentCodec;
import com.ming.imchatserver.application.model.GroupMessageView;
import com.ming.imchatserver.application.model.SingleMessageView;
import com.ming.imchatserver.mq.DispatchMessagePayload;

/**
 * 统一构造撤回协议，避免本地路径和 MQ 路径字段漂移。
 */
public final class RecallProtocolSupport {

    public static final String STATUS_RETRACTED = "RETRACTED";

    private RecallProtocolSupport() {
    }

    public static ObjectNode buildSingleRecallNode(ObjectMapper mapper, String type, SingleMessageView message) {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", type);
        node.put("serverMsgId", message.serverMsgId());
        writeNullableText(node, "clientMsgId", message.clientMsgId());
        writeNullableLong(node, "fromUserId", message.fromUserId());
        writeNullableLong(node, "toUserId", message.toUserId());
        node.put("msgType", MessageContentCodec.normalizeMsgType(message.msgType()));
        node.putNull("content");
        node.put("status", STATUS_RETRACTED);
        writeNullableText(node, "createdAt", toInstantText(message.createdAt()));
        writeNullableText(node, "retractedAt", toInstantText(message.retractedAt()));
        writeNullableLong(node, "retractedBy", message.retractedBy());
        return node;
    }

    public static ObjectNode buildSingleRecallNode(ObjectMapper mapper, String type, DispatchMessagePayload payload) {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", type);
        node.put("serverMsgId", payload.getServerMsgId());
        writeNullableText(node, "clientMsgId", payload.getClientMsgId());
        writeNullableLong(node, "fromUserId", payload.getFromUserId());
        writeNullableLong(node, "toUserId", payload.getToUserId());
        node.put("msgType", MessageContentCodec.normalizeMsgType(payload.getMsgType()));
        node.putNull("content");
        node.put("status", normalizeStatus(payload.getStatus()));
        writeNullableText(node, "createdAt", payload.getCreatedAt());
        writeNullableText(node, "retractedAt", payload.getRetractedAt());
        writeNullableLong(node, "retractedBy", payload.getRetractedBy());
        return node;
    }

    public static ObjectNode buildGroupRecallNode(ObjectMapper mapper, String type, GroupMessageView message) {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", type);
        node.put("serverMsgId", message.serverMsgId());
        writeNullableText(node, "clientMsgId", message.clientMsgId());
        writeNullableLong(node, "fromUserId", message.fromUserId());
        writeNullableLong(node, "groupId", message.groupId());
        writeNullableLong(node, "seq", message.seq());
        node.put("msgType", MessageContentCodec.normalizeMsgType(message.msgType()));
        node.putNull("content");
        node.put("status", STATUS_RETRACTED);
        writeNullableText(node, "createdAt", toInstantText(message.createdAt()));
        writeNullableText(node, "retractedAt", toInstantText(message.retractedAt()));
        writeNullableLong(node, "retractedBy", message.retractedBy());
        return node;
    }

    public static ObjectNode buildGroupRecallNode(ObjectMapper mapper, String type, DispatchMessagePayload payload) {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", type);
        node.put("serverMsgId", payload.getServerMsgId());
        writeNullableText(node, "clientMsgId", payload.getClientMsgId());
        writeNullableLong(node, "fromUserId", payload.getFromUserId());
        writeNullableLong(node, "groupId", payload.getGroupId());
        writeNullableLong(node, "seq", payload.getSeq());
        node.put("msgType", MessageContentCodec.normalizeMsgType(payload.getMsgType()));
        node.putNull("content");
        node.put("status", normalizeStatus(payload.getStatus()));
        writeNullableText(node, "createdAt", payload.getCreatedAt());
        writeNullableText(node, "retractedAt", payload.getRetractedAt());
        writeNullableLong(node, "retractedBy", payload.getRetractedBy());
        return node;
    }

    private static String normalizeStatus(String status) {
        return status == null || status.isBlank() ? STATUS_RETRACTED : status;
    }

    private static String toInstantText(java.util.Date date) {
        return date == null ? null : date.toInstant().toString();
    }

    private static void writeNullableText(ObjectNode node, String field, String value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    private static void writeNullableLong(ObjectNode node, String field, Long value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }
}
