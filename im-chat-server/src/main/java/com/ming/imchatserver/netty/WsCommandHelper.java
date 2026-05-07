package com.ming.imchatserver.netty;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * WebSocket命令处理公共工具类，提取各CommandHandler重复逻辑
 */
public class WsCommandHelper {

    /**
     * 校验用户是否已绑定，未绑定抛出未授权异常
     */
    public static Long requireUser(WsCommandContext context) {
        if (context.userId() == null) {
            throw new UnauthorizedWsException("user not bound");
        }
        return context.userId();
    }

    /**
     * 从payload中提取正整数groupId，非法值抛出参数异常
     */
    public static long positiveGroupId(JsonNode payload) {
        long groupId = payload.path("groupId").asLong(0L);
        if (groupId <= 0) {
            throw new IllegalArgumentException("groupId must be greater than 0");
        }
        return groupId;
    }

    /**
     * 从payload中提取正整数targetUserId，非法值抛出参数异常
     */
    public static long positiveTargetUserId(JsonNode payload) {
        long targetUserId = payload.path("targetUserId").asLong(0L);
        if (targetUserId <= 0) {
            throw new IllegalArgumentException("targetUserId must be greater than 0");
        }
        return targetUserId;
    }

    /**
     * 获取JsonNode的文本值，空/空白返回null
     */
    public static String textValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * 获取JsonNode的Long值，空返回null
     */
    public static Long longValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asLong();
    }

    /**
     * 标准化clientMsgId，空/空白返回null
     */
    public static String normalizeClientMsgId(String clientMsgId) {
        if (clientMsgId == null) {
            return null;
        }
        String trimmed = clientMsgId.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 校验limit参数范围，非法值抛出参数异常
     */
    public static int validateLimit(int limit, int min, int max) {
        if (limit < min || limit > max) {
            throw new IllegalArgumentException("limit must be between " + min + " and " + max);
        }
        return limit;
    }
}
