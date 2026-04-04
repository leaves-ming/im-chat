package com.ming.imchatserver.netty;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ming.imchatserver.dao.ContactDO;
import com.ming.imchatserver.dao.GroupMemberDO;
import com.ming.imchatserver.dao.GroupMessageDO;
import com.ming.imchatserver.dao.MessageDO;
import com.ming.imchatserver.message.MessageContentCodec;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import java.time.Instant;
import java.util.Date;

/**
 * WebSocket 协议编解码辅助。
 */
public class WsProtocolSupport {

    private static final String STATUS_RETRACTED = "RETRACTED";
    private static final int GROUP_STATUS_RETRACTED = 2;

    private final ObjectMapper mapper;

    public WsProtocolSupport(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    public void sendJson(Channel channel, ObjectNode payload) throws Exception {
        channel.writeAndFlush(new TextWebSocketFrame(mapper.writeValueAsString(payload)));
    }

    public void sendError(Channel channel, String traceId, String code, String msg) {
        try {
            ObjectNode err = mapper.createObjectNode();
            err.put("type", "ERROR");
            err.put("code", code);
            err.put("msg", msg);
            if (traceId != null) {
                err.put("traceId", traceId);
            }
            sendJson(channel, err);
        } catch (Exception ex) {
            channel.writeAndFlush(new TextWebSocketFrame("{\"type\":\"ERROR\",\"code\":\"INTERNAL_ERROR\",\"msg\":\"internal error\"}"));
        }
    }

    public String currentDeviceId(Channel channel) {
        String deviceId = channel == null ? null : channel.attr(NettyAttr.DEVICE_ID).get();
        return deviceId == null || deviceId.isBlank() ? "default" : deviceId;
    }

    public String formatAsInstant(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant().toString();
    }

    public void writeSingleMessageNode(ObjectNode target, MessageDO message) {
        target.put("serverMsgId", message.getServerMsgId());
        if (message.getClientMsgId() == null) {
            target.putNull("clientMsgId");
        } else {
            target.put("clientMsgId", message.getClientMsgId());
        }
        target.put("fromUserId", message.getFromUserId());
        target.put("toUserId", message.getToUserId());
        target.put("msgType", MessageContentCodec.normalizeMsgType(message.getMsgType()));
        writeMessageContent(target, message.getMsgType(), message.getContent(), isSingleRetracted(message));
        target.put("status", isSingleRetracted(message) ? STATUS_RETRACTED : message.getStatus());
        target.put("createdAt", formatAsInstant(message.getCreatedAt()));
        writeRetractionMeta(target, message.getRetractedAt(), message.getRetractedBy());
    }

    public void writeGroupMessageNode(ObjectNode target, GroupMessageDO message) {
        target.put("groupId", message.getGroupId());
        target.put("seq", message.getSeq());
        target.put("serverMsgId", message.getServerMsgId());
        target.put("fromUserId", message.getFromUserId());
        target.put("msgType", MessageContentCodec.normalizeMsgType(message.getMsgType()));
        writeMessageContent(target, message.getMsgType(), message.getContent(), isGroupRetracted(message));
        target.put("status", isGroupRetracted(message) ? STATUS_RETRACTED : "SENT");
        target.put("createdAt", formatAsInstant(message.getCreatedAt()));
        writeRetractionMeta(target, message.getRetractedAt(), message.getRetractedBy());
    }

    public void writeContactList(ObjectNode resp, Iterable<ContactDO> contacts) {
        ArrayNode items = mapper.createArrayNode();
        for (ContactDO contact : contacts) {
            ObjectNode item = mapper.createObjectNode();
            item.put("peerUserId", contact.getPeerUserId());
            item.put("relationStatus", contact.getRelationStatus());
            item.put("createdAt", formatAsInstant(contact.getCreatedAt()));
            item.put("updatedAt", formatAsInstant(contact.getUpdatedAt()));
            items.add(item);
        }
        resp.set("items", items);
    }

    public void writeMemberList(ObjectNode resp, Iterable<GroupMemberDO> members) {
        ArrayNode items = mapper.createArrayNode();
        for (GroupMemberDO member : members) {
            ObjectNode item = mapper.createObjectNode();
            item.put("userId", member.getUserId());
            item.put("role", member.getRole());
            item.put("memberStatus", member.getMemberStatus());
            items.add(item);
        }
        resp.set("items", items);
    }

    public void writeSingleSyncProgress(ObjectNode target,
                                        String deviceId,
                                        MessageDO ignored,
                                        Date nextCursorCreatedAt,
                                        Long nextCursorId) {
        if (nextCursorCreatedAt == null) {
            target.putNull("nextCursorCreatedAt");
        } else {
            target.put("nextCursorCreatedAt", formatAsInstant(nextCursorCreatedAt));
        }
        if (nextCursorId == null) {
            target.putNull("nextCursorId");
        } else {
            target.put("nextCursorId", nextCursorId);
        }
        target.set("nextSyncToken", buildSingleSyncToken(deviceId, nextCursorCreatedAt, nextCursorId));
    }

    public void writeGroupSyncProgress(ObjectNode target, Long groupId, Long nextCursorSeq) {
        if (nextCursorSeq == null) {
            return;
        }
        target.put("nextCursorSeq", nextCursorSeq);
        target.set("nextSyncToken", buildGroupSyncToken(groupId, nextCursorSeq));
    }

    public ObjectNode buildSingleSyncToken(String deviceId, Date cursorCreatedAt, Long cursorId) {
        ObjectNode token = mapper.createObjectNode();
        token.put("chatType", "SINGLE");
        token.put("deviceId", deviceId == null ? "default" : deviceId);
        if (cursorCreatedAt == null) {
            token.putNull("cursorCreatedAt");
        } else {
            token.put("cursorCreatedAt", formatAsInstant(cursorCreatedAt));
        }
        token.put("cursorId", cursorId == null ? 0L : cursorId);
        return token;
    }

    public ObjectNode buildGroupSyncToken(Long groupId, Long cursorSeq) {
        if (groupId == null || cursorSeq == null) {
            return null;
        }
        ObjectNode token = mapper.createObjectNode();
        token.put("chatType", "GROUP");
        token.put("groupId", groupId);
        token.put("cursorSeq", cursorSeq);
        return token;
    }

    public Long parseLong(ObjectNode ignored, com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asLong();
    }

    public Instant parseInstant(String raw) {
        return raw == null || raw.isBlank() ? null : Instant.parse(raw);
    }

    private void writeMessageContent(ObjectNode target, String msgType, String content, boolean retracted) {
        if (retracted) {
            target.putNull("content");
            return;
        }
        MessageContentCodec.writeProtocolContent(target, "content", msgType, content);
    }

    private void writeRetractionMeta(ObjectNode target, Date retractedAt, Long retractedBy) {
        if (retractedAt == null) {
            target.putNull("retractedAt");
        } else {
            target.put("retractedAt", formatAsInstant(retractedAt));
        }
        if (retractedBy == null) {
            target.putNull("retractedBy");
        } else {
            target.put("retractedBy", retractedBy);
        }
    }

    private boolean isSingleRetracted(MessageDO message) {
        return message != null && (STATUS_RETRACTED.equalsIgnoreCase(message.getStatus()) || message.getRetractedAt() != null);
    }

    private boolean isGroupRetracted(GroupMessageDO message) {
        return message != null && (Integer.valueOf(GROUP_STATUS_RETRACTED).equals(message.getStatus()) || message.getRetractedAt() != null);
    }
}
