package com.ming.imchatserver.netty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ming.imchatserver.application.facade.AuthFacade;
import com.ming.imchatserver.application.facade.MessageFacade;
import com.ming.imchatserver.application.facade.SocialFacade;
import com.ming.imchatserver.application.facade.impl.AuthFacadeImpl;
import com.ming.imchatserver.application.facade.impl.MessageFacadeImpl;
import com.ming.imchatserver.application.facade.impl.SocialFacadeImpl;
import com.ming.imchatserver.config.NettyProperties;
import com.ming.imchatserver.config.RateLimitProperties;
import com.ming.imchatserver.config.RedisStateProperties;
import com.ming.imchatserver.config.WsRouteProperties;
import com.ming.imchatserver.dao.MessageDO;
import com.ming.imchatserver.mapper.DeliveryMapper;
import com.ming.imchatserver.metrics.MetricsService;
import com.ming.imchatserver.observability.TraceContextSupport;
import com.ming.imchatserver.service.ContactService;
import com.ming.imchatserver.service.FileTokenBizException;
import com.ming.imchatserver.service.GroupBizException;
import com.ming.imchatserver.service.GroupMessageService;
import com.ming.imchatserver.service.GroupService;
import com.ming.imchatserver.service.IdempotencyService;
import com.ming.imchatserver.service.MessageRecallException;
import com.ming.imchatserver.service.MessageService;
import com.ming.imchatserver.service.RateLimitService;
import com.ming.imchatserver.file.FileAccessDeniedException;
import com.ming.imchatserver.sensitive.SensitiveWordHitException;
import com.ming.imchatserver.sensitive.SensitiveWordUnavailableException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * WebSocket 网关入口处理器。
 * <p>
 * 仅负责协议解析、上下文提取、线程切换和命令路由。
 */
public class WebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketFrameHandler.class);

    private final ChannelUserManager channelUserManager;
    private final NettyProperties nettyProperties;
    private final Executor businessExecutor;
    private final WsProtocolSupport protocolSupport;
    private final WsCommandRouter commandRouter;
    private final AuthFacade authFacade;
    private final MessageFacade messageFacade;

    public WebSocketFrameHandler(ChannelUserManager channelUserManager,
                                 MessageService messageService,
                                 ContactService contactService,
                                 GroupService groupService,
                                 GroupMessageService groupMessageService,
                                 NettyProperties nettyProperties,
                                 DeliveryMapper deliveryMapper,
                                 MetricsService metricsService) {
        this(channelUserManager, messageService, contactService, groupService, groupMessageService,
                nettyProperties, deliveryMapper, metricsService, null, null, null, null, null, null, null, null);
    }

    public WebSocketFrameHandler(ChannelUserManager channelUserManager,
                                 MessageService messageService,
                                 ContactService contactService,
                                 GroupService groupService,
                                 GroupMessageService groupMessageService,
                                 NettyProperties nettyProperties,
                                 DeliveryMapper deliveryMapper,
                                 MetricsService metricsService,
                                 Executor groupPushExecutor) {
        this(channelUserManager, messageService, contactService, groupService, groupMessageService,
                nettyProperties, deliveryMapper, metricsService, groupPushExecutor, null, null, null, null, null, null, null);
    }

    public WebSocketFrameHandler(ChannelUserManager channelUserManager,
                                 MessageService messageService,
                                 ContactService contactService,
                                 GroupService groupService,
                                 GroupMessageService groupMessageService,
                                 NettyProperties nettyProperties,
                                 DeliveryMapper deliveryMapper,
                                 MetricsService metricsService,
                                 Executor groupPushExecutor,
                                 GroupPushCoordinator groupPushCoordinator) {
        this(channelUserManager, messageService, contactService, groupService, groupMessageService,
                nettyProperties, deliveryMapper, metricsService, groupPushExecutor, groupPushCoordinator,
                null, null, null, null, null, null);
    }

    public WebSocketFrameHandler(ChannelUserManager channelUserManager,
                                 MessageService messageService,
                                 ContactService contactService,
                                 GroupService groupService,
                                 GroupMessageService groupMessageService,
                                 NettyProperties nettyProperties,
                                 DeliveryMapper deliveryMapper,
                                 MetricsService metricsService,
                                 Executor groupPushExecutor,
                                 GroupPushCoordinator groupPushCoordinator,
                                 IdempotencyService idempotencyService,
                                 RateLimitService rateLimitService,
                                 RateLimitProperties rateLimitProperties,
                                 RedisStateProperties redisStateProperties) {
        this(channelUserManager, messageService, contactService, groupService, groupMessageService,
                nettyProperties, deliveryMapper, metricsService, groupPushExecutor, groupPushCoordinator,
                idempotencyService, rateLimitService, rateLimitProperties, redisStateProperties,
                null, null, null, null);
    }

    public WebSocketFrameHandler(ChannelUserManager channelUserManager,
                                 MessageService messageService,
                                 ContactService contactService,
                                 GroupService groupService,
                                 GroupMessageService groupMessageService,
                                 NettyProperties nettyProperties,
                                 DeliveryMapper deliveryMapper,
                                 MetricsService metricsService,
                                 Executor groupPushExecutor,
                                 GroupPushCoordinator groupPushCoordinator,
                                 IdempotencyService idempotencyService,
                                 RateLimitService rateLimitService,
                                 RateLimitProperties rateLimitProperties,
                                 RedisStateProperties redisStateProperties,
                                 Executor businessExecutor,
                                 WsRouteProperties routeProperties) {
        this(channelUserManager, messageService, contactService, groupService, groupMessageService,
                nettyProperties, deliveryMapper, metricsService, groupPushExecutor, groupPushCoordinator,
                idempotencyService, rateLimitService, rateLimitProperties, redisStateProperties,
                null, null, businessExecutor, routeProperties);
    }

    public WebSocketFrameHandler(ChannelUserManager channelUserManager,
                                 MessageService messageService,
                                 ContactService contactService,
                                 GroupService groupService,
                                 GroupMessageService groupMessageService,
                                 NettyProperties nettyProperties,
                                 DeliveryMapper deliveryMapper,
                                 MetricsService metricsService,
                                 Executor groupPushExecutor,
                                 GroupPushCoordinator groupPushCoordinator,
                                 IdempotencyService idempotencyService,
                                 RateLimitService rateLimitService,
                                 RateLimitProperties rateLimitProperties,
                                 RedisStateProperties redisStateProperties,
                                 MessageFacade injectedMessageFacade,
                                 AuthFacade injectedAuthFacade,
                                 Executor businessExecutor,
                                 WsRouteProperties routeProperties) {
        this.channelUserManager = channelUserManager;
        this.nettyProperties = nettyProperties;
        this.businessExecutor = businessExecutor == null ? Runnable::run : businessExecutor;
        ObjectMapper mapper = new ObjectMapper();
        this.protocolSupport = new WsProtocolSupport(mapper);
        this.messageFacade = injectedMessageFacade != null
                ? injectedMessageFacade
                : new MessageFacadeImpl(messageService, deliveryMapper, metricsService,
                idempotencyService, rateLimitService, rateLimitProperties, redisStateProperties, nettyProperties);
        SocialFacade socialFacade = new SocialFacadeImpl(contactService, groupService, groupMessageService,
                channelUserManager, groupPushExecutor, groupPushCoordinator, metricsService,
                idempotencyService, rateLimitService, rateLimitProperties, redisStateProperties, nettyProperties);
        this.authFacade = injectedAuthFacade != null ? injectedAuthFacade : new AuthFacadeImpl(messageService);
        WsRouteProperties effectiveRouteProperties = routeProperties == null ? new WsRouteProperties() : routeProperties;
        this.commandRouter = new WsCommandRouter(
                effectiveRouteProperties,
                new ChatCommandHandler(this.messageFacade, socialFacade, nettyProperties, protocolSupport, channelUserManager),
                new GroupCommandHandler(socialFacade, nettyProperties, protocolSupport),
                new ContactCommandHandler(socialFacade, nettyProperties, protocolSupport),
                new RecallCommandHandler(this.messageFacade, socialFacade, nettyProperties, protocolSupport, channelUserManager)
        );
    }

    public WebSocketFrameHandler(ChannelUserManager channelUserManager,
                                 MessageService messageService,
                                 NettyProperties nettyProperties,
                                 DeliveryMapper deliveryMapper) {
        this(channelUserManager, messageService, null, null, null, nettyProperties, deliveryMapper, null,
                null, null, null, null, null, null, null, null, null, null);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        Long userId = ctx.channel().attr(NettyAttr.USER_ID).get();
        if (userId != null) {
            businessExecutor.execute(() -> channelUserManager.unbindUser(ctx.channel(), userId));
        }
        super.handlerRemoved(ctx);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler.HandshakeComplete) {
            Long userId = ctx.channel().attr(NettyAttr.USER_ID).get();
            Boolean auth = ctx.channel().attr(NettyAttr.AUTH_OK).get();
            if (Boolean.TRUE.equals(auth) && userId != null) {
                Boolean bound = ctx.channel().attr(NettyAttr.BOUND).get();
                if (!Boolean.TRUE.equals(bound)) {
                    ctx.channel().attr(NettyAttr.BOUND).set(Boolean.TRUE);
                    businessExecutor.execute(() -> {
                        channelUserManager.bindUser(ctx.channel(), userId);
                        try {
                            triggerSyncAfterHandshake(ctx, userId);
                        } catch (Exception ex) {
                            logger.error("trigger sync after handshake failed", ex);
                        }
                    });
                }
            } else {
                ctx.close();
            }
            return;
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        if (frame instanceof TextWebSocketFrame text) {
            businessExecutor.execute(() -> channelUserManager.refreshHeartbeat(ctx.channel()));
            processTextFrame(ctx, text.text());
            return;
        }
        if (frame instanceof PingWebSocketFrame) {
            businessExecutor.execute(() -> channelUserManager.refreshHeartbeat(ctx.channel()));
            ctx.writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
            return;
        }
        if (frame instanceof PongWebSocketFrame) {
            return;
        }
        if (frame instanceof CloseWebSocketFrame) {
            ctx.close();
        }
    }

    private void processTextFrame(ChannelHandlerContext ctx, String textMsg) {
        Long userId = ctx.channel().attr(NettyAttr.USER_ID).get();
        String traceId = TraceContextSupport.currentTraceId(ctx.channel());
        if (traceId == null) {
            traceId = UUID.randomUUID().toString().replace("-", "");
            ctx.channel().attr(NettyAttr.TRACE_ID).set(traceId);
        }
        String finalTraceId = traceId;
        businessExecutor.execute(() -> {
            TraceContextSupport.putMdc(finalTraceId);
            try {
                JsonNode node = protocolSupport.mapper().readTree(textMsg);
                if (node == null || !node.has("type")) {
                    protocolSupport.sendError(ctx.channel(), finalTraceId, "INVALID_PARAM", "missing type field");
                    return;
                }
                String commandType = node.path("type").asText();
                commandRouter.route(new WsCommandContext(ctx.channel(), userId, commandType, node));
            } catch (GroupBizException ex) {
                protocolSupport.sendError(ctx.channel(), finalTraceId, ex.getCode().name(), ex.getMessage());
            } catch (FileTokenBizException ex) {
                protocolSupport.sendError(ctx.channel(), finalTraceId, ex.getCode(), ex.getMessage());
            } catch (MessageRecallException ex) {
                protocolSupport.sendError(ctx.channel(), finalTraceId, ex.getCode(), ex.getMessage());
            } catch (FileAccessDeniedException ex) {
                protocolSupport.sendError(ctx.channel(), finalTraceId, "FORBIDDEN", ex.getMessage());
            } catch (UnauthorizedWsException ex) {
                protocolSupport.sendError(ctx.channel(), finalTraceId, "UNAUTHORIZED", ex.getMessage());
            } catch (SecurityException ex) {
                protocolSupport.sendError(ctx.channel(), finalTraceId, "FORBIDDEN", ex.getMessage());
            } catch (IllegalArgumentException ex) {
                String message = ex.getMessage();
                if (message != null && message.startsWith("RATE_LIMITED:")) {
                    protocolSupport.sendError(ctx.channel(), finalTraceId, "RATE_LIMITED", message.substring("RATE_LIMITED:".length()));
                } else if (message != null && message.startsWith("DUPLICATE_REQUEST:")) {
                    protocolSupport.sendError(ctx.channel(), finalTraceId, "DUPLICATE_REQUEST", message.substring("DUPLICATE_REQUEST:".length()));
                } else {
                    protocolSupport.sendError(ctx.channel(), finalTraceId, "INVALID_PARAM", ex.getMessage());
                }
            } catch (SensitiveWordHitException ex) {
                protocolSupport.sendError(ctx.channel(), finalTraceId, ex.getCode(), ex.getMessage());
            } catch (SensitiveWordUnavailableException ex) {
                protocolSupport.sendError(ctx.channel(), finalTraceId, ex.getCode(), ex.getMessage());
            } catch (Exception ex) {
                logger.error("process text frame error", ex);
                protocolSupport.sendError(ctx.channel(), finalTraceId, "INTERNAL_ERROR", "internal error");
            } finally {
                TraceContextSupport.clearMdc();
            }
        });
    }

    private void triggerSyncAfterHandshake(ChannelHandlerContext ctx, Long userId) throws Exception {
        ObjectNode start = protocolSupport.mapper().createObjectNode();
        start.put("type", "SYNC_START");
        start.put("userId", userId);
        protocolSupport.sendJson(ctx.channel(), start);

        String deviceId = protocolSupport.currentDeviceId(ctx.channel());
        int batchSize = nettyProperties.getSyncBatchSize();
        var pageResult = authFacade.loadInitialSync(userId, deviceId, batchSize);
        ObjectNode batchNode = protocolSupport.mapper().createObjectNode();
        batchNode.put("type", "SYNC_BATCH");
        ArrayNode messages = protocolSupport.mapper().createArrayNode();
        for (MessageDO message : pageResult.getMessages()) {
            ObjectNode item = protocolSupport.mapper().createObjectNode();
            protocolSupport.writeSingleMessageNode(item, message);
            messages.add(item);
        }
        batchNode.set("messages", messages);
        protocolSupport.sendJson(ctx.channel(), batchNode);

        ObjectNode end = protocolSupport.mapper().createObjectNode();
        end.put("type", "SYNC_END");
        end.put("hasMore", pageResult.isHasMore());
        protocolSupport.writeSingleSyncProgress(end, deviceId, null, pageResult.getNextCursorCreatedAt(), pageResult.getNextCursorId());
        ctx.channel().writeAndFlush(new TextWebSocketFrame(protocolSupport.mapper().writeValueAsString(end)))
                .addListener(future -> {
                    if (future.isSuccess()) {
                        messageFacade.advanceSyncCursor(userId, deviceId, pageResult);
                    }
                });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("websocket handler exception", cause);
        businessExecutor.execute(() -> channelUserManager.unbindByChannel(ctx.channel()));
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        businessExecutor.execute(() -> channelUserManager.unbindByChannel(ctx.channel()));
        super.channelInactive(ctx);
    }
}
