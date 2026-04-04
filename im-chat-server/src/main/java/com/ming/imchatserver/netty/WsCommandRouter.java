package com.ming.imchatserver.netty;

import com.ming.imchatserver.config.WsRouteProperties;

/**
 * WebSocket 命令路由器。
 */
public class WsCommandRouter {

    private final WsRouteProperties routeProperties;
    private final WsCommandHandler chatHandler;
    private final WsCommandHandler groupHandler;
    private final WsCommandHandler contactHandler;
    private final WsCommandHandler recallHandler;

    public WsCommandRouter(WsRouteProperties routeProperties,
                           WsCommandHandler chatHandler,
                           WsCommandHandler groupHandler,
                           WsCommandHandler contactHandler,
                           WsCommandHandler recallHandler) {
        this.routeProperties = routeProperties;
        this.chatHandler = chatHandler;
        this.groupHandler = groupHandler;
        this.contactHandler = contactHandler;
        this.recallHandler = recallHandler;
    }

    public void route(WsCommandContext context) throws Exception {
        String commandType = context.commandType();
        WsCommandHandler handler = switch (commandType) {
            case "CHAT", "ACK_REPORT", "PULL_OFFLINE" -> selectChatHandler(commandType);
            case "GROUP_JOIN", "GROUP_QUIT", "GROUP_MEMBER_LIST", "GROUP_CHAT", "GROUP_PULL_OFFLINE" -> selectGroupHandler(commandType);
            case "CONTACT_ADD", "CONTACT_REMOVE", "CONTACT_LIST" -> selectContactHandler(commandType);
            case "MSG_RECALL", "GROUP_MSG_RECALL" -> selectRecallHandler(commandType);
            default -> null;
        };
        if (handler == null) {
            throw new IllegalArgumentException("unsupported command: " + commandType);
        }
        handler.handle(context);
    }

    private WsCommandHandler selectChatHandler(String commandType) {
        if ("CHAT".equals(commandType) && !routeProperties.chatV2Enabled()) {
            return chatHandler;
        }
        if ("PULL_OFFLINE".equals(commandType) && !routeProperties.chatV2Enabled()) {
            return chatHandler;
        }
        if ("ACK_REPORT".equals(commandType) && !routeProperties.chatV2Enabled()) {
            return chatHandler;
        }
        return chatHandler;
    }

    private WsCommandHandler selectGroupHandler(String commandType) {
        if ("GROUP_CHAT".equals(commandType) && !routeProperties.groupV2Enabled()) {
            return groupHandler;
        }
        return groupHandler;
    }

    private WsCommandHandler selectContactHandler(String commandType) {
        if (!routeProperties.contactV2Enabled()) {
            return contactHandler;
        }
        return contactHandler;
    }

    private WsCommandHandler selectRecallHandler(String commandType) {
        if (("MSG_RECALL".equals(commandType) || "GROUP_MSG_RECALL".equals(commandType)) && !routeProperties.chatV2Enabled()) {
            return recallHandler;
        }
        return recallHandler;
    }
}
