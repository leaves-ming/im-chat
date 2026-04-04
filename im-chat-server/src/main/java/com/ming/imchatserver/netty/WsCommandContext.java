package com.ming.imchatserver.netty;

import com.fasterxml.jackson.databind.JsonNode;
import io.netty.channel.Channel;

/**
 * WebSocket 命令上下文。
 */
public class WsCommandContext {

    private final Channel channel;
    private final Long userId;
    private final String commandType;
    private final JsonNode payload;

    public WsCommandContext(Channel channel, Long userId, String commandType, JsonNode payload) {
        this.channel = channel;
        this.userId = userId;
        this.commandType = commandType;
        this.payload = payload;
    }

    public Channel channel() {
        return channel;
    }

    public Long userId() {
        return userId;
    }

    public String commandType() {
        return commandType;
    }

    public JsonNode payload() {
        return payload;
    }
}
