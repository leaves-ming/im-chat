package com.ming.imchatserver.netty;

/**
 * WebSocket 命令处理器。
 */
public interface WsCommandHandler {

    boolean supports(String commandType);

    void handle(WsCommandContext context) throws Exception;
}
