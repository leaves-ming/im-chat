package com.ming.imchatserver.netty;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ming.imchatserver.application.facade.MessageFacade;
import com.ming.imchatserver.application.facade.SocialFacade;
import com.ming.imchatserver.application.model.GroupMessageView;
import com.ming.imchatserver.application.model.SingleMessageView;
import com.ming.imchatserver.config.NettyProperties;
import com.ming.imchatserver.message.RecallProtocolSupport;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

/**
 * 撤回命令处理器。
 */
public class RecallCommandHandler implements WsCommandHandler {

    private static final int DEFAULT_MESSAGE_RECALL_WINDOW_SECONDS = 120;

    private final MessageFacade messageFacade;
    private final SocialFacade socialFacade;
    private final NettyProperties nettyProperties;
    private final WsProtocolSupport protocolSupport;
    private final ChannelUserManager channelUserManager;

    public RecallCommandHandler(MessageFacade messageFacade,
                                SocialFacade socialFacade,
                                NettyProperties nettyProperties,
                                WsProtocolSupport protocolSupport,
                                ChannelUserManager channelUserManager) {
        this.messageFacade = messageFacade;
        this.socialFacade = socialFacade;
        this.nettyProperties = nettyProperties;
        this.protocolSupport = protocolSupport;
        this.channelUserManager = channelUserManager;
    }

    @Override
    public boolean supports(String commandType) {
        return "MSG_RECALL".equals(commandType) || "GROUP_MSG_RECALL".equals(commandType);
    }

    @Override
    public void handle(WsCommandContext context) throws Exception {
        switch (context.commandType()) {
            case "MSG_RECALL" -> handleSingleRecall(context);
            case "GROUP_MSG_RECALL" -> handleGroupRecall(context);
            default -> throw new IllegalArgumentException("unsupported command: " + context.commandType());
        }
    }

    private void handleSingleRecall(WsCommandContext context) throws Exception {
        Long userId = requireUser(context);
        String serverMsgId = requireServerMsgId(context);
        SingleMessageView recalled = messageFacade.recallMessage(userId, serverMsgId, recallWindowSeconds());
        ObjectNode result = RecallProtocolSupport.buildSingleRecallNode(protocolSupport.mapper(), "MSG_RECALL_RESULT", recalled);
        protocolSupport.sendJson(context.channel(), result);
        notifySingleRecallParticipants(context.channel(), recalled);
    }

    private void handleGroupRecall(WsCommandContext context) throws Exception {
        Long userId = requireUser(context);
        String serverMsgId = requireServerMsgId(context);
        GroupMessageView recalled = messageFacade.recallGroupMessage(userId, serverMsgId, recallWindowSeconds());
        ObjectNode result = RecallProtocolSupport.buildGroupRecallNode(protocolSupport.mapper(), "GROUP_MSG_RECALL_RESULT", recalled);
        protocolSupport.sendJson(context.channel(), result);
        notifyGroupRecall(context.channel(), recalled);
    }

    private void notifySingleRecallParticipants(Channel requester, SingleMessageView message) throws Exception {
        if (message == null) {
            return;
        }
        String payload = protocolSupport.mapper().writeValueAsString(
                RecallProtocolSupport.buildSingleRecallNode(protocolSupport.mapper(), "MSG_RECALL_NOTIFY", message));
        for (Channel channel : channelUserManager.getChannels(message.fromUserId())) {
            if (channel == requester) {
                continue;
            }
            channel.writeAndFlush(new TextWebSocketFrame(payload));
        }
        for (Channel channel : channelUserManager.getChannels(message.toUserId())) {
            channel.writeAndFlush(new TextWebSocketFrame(payload));
        }
    }

    private void notifyGroupRecall(Channel requester, GroupMessageView message) throws Exception {
        String payload = protocolSupport.mapper().writeValueAsString(
                RecallProtocolSupport.buildGroupRecallNode(protocolSupport.mapper(), "GROUP_MSG_RECALL_NOTIFY", message));
        for (Long userId : socialFacade.listActiveMemberUserIds(message.groupId())) {
            for (Channel channel : channelUserManager.getChannels(userId)) {
                if (channel == requester) {
                    continue;
                }
                channel.writeAndFlush(new TextWebSocketFrame(payload));
            }
        }
    }

    private Long requireUser(WsCommandContext context) {
        if (context.userId() == null) {
            throw new UnauthorizedWsException("user not bound");
        }
        return context.userId();
    }

    private String requireServerMsgId(WsCommandContext context) {
        String serverMsgId = context.payload().path("serverMsgId").asText(null);
        if (serverMsgId == null || serverMsgId.isBlank()) {
            throw new IllegalArgumentException("serverMsgId required");
        }
        return serverMsgId;
    }

    private long recallWindowSeconds() {
        int configured = nettyProperties == null ? 0 : nettyProperties.getMessageRecallWindowSeconds();
        return configured > 0 ? configured : DEFAULT_MESSAGE_RECALL_WINDOW_SECONDS;
    }
}
