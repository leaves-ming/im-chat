package com.ming.imchatserver.netty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ming.imchatserver.application.facade.SocialFacade;
import com.ming.imchatserver.application.model.ContactOperationResult;
import com.ming.imchatserver.application.model.ContactPage;
import com.ming.imchatserver.config.NettyProperties;

/**
 * 联系人命令处理器。
 */
public class ContactCommandHandler implements WsCommandHandler {

    private static final int DEFAULT_CONTACT_LIST_LIMIT = 50;
    private static final int DEFAULT_PULL_MAX_LIMIT = 200;

    private final SocialFacade socialFacade;
    private final NettyProperties nettyProperties;
    private final WsProtocolSupport protocolSupport;

    public ContactCommandHandler(SocialFacade socialFacade,
                                 NettyProperties nettyProperties,
                                 WsProtocolSupport protocolSupport) {
        this.socialFacade = socialFacade;
        this.nettyProperties = nettyProperties;
        this.protocolSupport = protocolSupport;
    }

    @Override
    public boolean supports(String commandType) {
        return "CONTACT_ADD".equals(commandType)
                || "CONTACT_REMOVE".equals(commandType)
                || "CONTACT_LIST".equals(commandType);
    }

    @Override
    public void handle(WsCommandContext context) throws Exception {
        switch (context.commandType()) {
            case "CONTACT_ADD" -> handleContactAdd(context);
            case "CONTACT_REMOVE" -> handleContactRemove(context);
            case "CONTACT_LIST" -> handleContactList(context);
            default -> throw new IllegalArgumentException("unsupported command: " + context.commandType());
        }
    }

    private void handleContactAdd(WsCommandContext context) throws Exception {
        Long userId = requireUser(context);
        long peerUserId = context.payload().path("peerUserId").asLong(0L);
        if (!isValidContactPeer(userId, peerUserId)) {
            throw new IllegalArgumentException("peerUserId must be greater than 0 and different from self");
        }
        ContactOperationResult result = socialFacade.addContact(userId, peerUserId);
        ObjectNode resp = protocolSupport.mapper().createObjectNode();
        resp.put("type", "CONTACT_ADD_RESULT");
        resp.put("peerUserId", peerUserId);
        resp.put("success", result.success());
        resp.put("idempotent", result.idempotent());
        protocolSupport.sendJson(context.channel(), resp);
    }

    private void handleContactRemove(WsCommandContext context) throws Exception {
        Long userId = requireUser(context);
        long peerUserId = context.payload().path("peerUserId").asLong(0L);
        if (!isValidContactPeer(userId, peerUserId)) {
            throw new IllegalArgumentException("peerUserId must be greater than 0 and different from self");
        }
        ContactOperationResult result = socialFacade.removeContact(userId, peerUserId);
        ObjectNode resp = protocolSupport.mapper().createObjectNode();
        resp.put("type", "CONTACT_REMOVE_RESULT");
        resp.put("peerUserId", peerUserId);
        resp.put("success", result.success());
        resp.put("idempotent", result.idempotent());
        protocolSupport.sendJson(context.channel(), resp);
    }

    private void handleContactList(WsCommandContext context) throws Exception {
        Long userId = requireUser(context);
        JsonNode node = context.payload();
        int defaultLimit = DEFAULT_CONTACT_LIST_LIMIT;
        int maxLimit = nettyProperties.getOfflinePullMaxLimit() > 0 ? nettyProperties.getOfflinePullMaxLimit() : DEFAULT_PULL_MAX_LIMIT;
        int limit = node.has("limit") ? node.path("limit").asInt(defaultLimit) : defaultLimit;
        if (limit < 1 || limit > maxLimit) {
            throw new IllegalArgumentException("limit must be between 1 and " + maxLimit);
        }
        Long cursorPeerUserId = node.has("cursorPeerUserId") && !node.get("cursorPeerUserId").isNull()
                ? node.get("cursorPeerUserId").asLong()
                : null;
        if (cursorPeerUserId != null && cursorPeerUserId < 0L) {
            throw new IllegalArgumentException("cursorPeerUserId must be greater than or equal to 0");
        }
        ContactPage page = socialFacade.listContacts(userId, cursorPeerUserId, limit);
        ObjectNode resp = protocolSupport.mapper().createObjectNode();
        resp.put("type", "CONTACT_LIST_RESULT");
        resp.put("success", true);
        resp.put("hasMore", page.hasMore());
        if (page.nextCursor() == null) {
            resp.putNull("nextCursor");
        } else {
            resp.put("nextCursor", page.nextCursor());
        }
        protocolSupport.writeContactList(resp, page.items());
        protocolSupport.sendJson(context.channel(), resp);
    }

    private Long requireUser(WsCommandContext context) {
        if (context.userId() == null) {
            throw new UnauthorizedWsException("user not bound");
        }
        return context.userId();
    }

    private boolean isValidContactPeer(Long ownerUserId, long peerUserId) {
        return peerUserId > 0L && ownerUserId != null && !ownerUserId.equals(peerUserId);
    }
}
