package com.ming.imchatserver.netty;

/**
 * WebSocket 命令路由器。
 */
public class WsCommandRouter {

    private final WsCommandHandler chatHandler;
    private final WsCommandHandler groupHandler;
    private final WsCommandHandler contactHandler;
    private final WsCommandHandler recallHandler;

    public WsCommandRouter(WsCommandHandler chatHandler,
                           WsCommandHandler groupHandler,
                           WsCommandHandler contactHandler,
                           WsCommandHandler recallHandler) {
        this.chatHandler = chatHandler;
        this.groupHandler = groupHandler;
        this.contactHandler = contactHandler;
        this.recallHandler = recallHandler;
    }

    public void route(WsCommandContext context) throws Exception {
        WsCommandHandler handler = switch (context.commandType()) {
            case "CHAT", "ACK_REPORT", "PULL_OFFLINE" -> chatHandler;
            case "GROUP_JOIN", "GROUP_QUIT", "GROUP_MEMBER_LIST", "GROUP_CHAT", "GROUP_PULL_OFFLINE" -> groupHandler;
            case "CONTACT_ADD", "CONTACT_REMOVE", "CONTACT_LIST" -> contactHandler;
            case "MSG_RECALL", "GROUP_MSG_RECALL" -> recallHandler;
            default -> null;
        };
        if (handler == null) {
            throw new IllegalArgumentException("unsupported command: " + context.commandType());
        }
        handler.handle(context);
    }
}
