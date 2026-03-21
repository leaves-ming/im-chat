package com.ming.imchatserver.netty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.UUID;

public class WebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketFrameHandler.class);

    private final ChannelUserManager channelUserManager;
    private final ObjectMapper mapper = new ObjectMapper();

    public WebSocketFrameHandler(ChannelUserManager channelUserManager) {
        this.channelUserManager = channelUserManager;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        logger.info("handlerAdded: {}", ctx.channel());
        super.handlerAdded(ctx);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        Long userId = ctx.channel().attr(NettyAttr.USER_ID).get();
        if (userId != null) {
            channelUserManager.unbindUser(ctx.channel(), userId);
            logger.info("channel removed and unbound user {}", userId);
        }
        super.handlerRemoved(ctx);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // 当 channel 激活时，尝试绑定 userId（如果已在 attr 中设置）
        Long userId = ctx.channel().attr(NettyAttr.USER_ID).get();
        if (userId != null) {
            channelUserManager.bindUser(ctx.channel(), userId);
            logger.info("channel active and bound user {}", userId);
        }
        super.channelActive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        Channel ch = ctx.channel();
        if (frame instanceof TextWebSocketFrame text) {
            String textMsg = text.text();
            Long fromUserId = ctx.channel().attr(NettyAttr.USER_ID).get();
            logger.debug("recv text from userId={} channel={} msg={}", fromUserId, ch.id(), textMsg);
            // 简单回显逻辑：如果消息是 JSON 且包含 targetUserId，尝试投递；否则回显
            try {
                JsonNode node = mapper.readTree(textMsg);
                if (node != null && node.has("type") && "CHAT".equals(node.get("type").asText())) {
                    long target = node.path("targetUserId").asLong(0L);
                    String content = node.path("content").asText("");
                    String clientMsgId = node.path("clientMsgId").asText("");
                    // delivery payload
                    ObjectNode deliver = mapper.createObjectNode();
                    deliver.put("type", "CHAT_DELIVER");
                    deliver.put("fromUserId", fromUserId == null ? 0L : fromUserId);
                    deliver.put("content", content);
                    deliver.put("clientMsgId", clientMsgId);
                    String serverMsgId = UUID.randomUUID().toString();
                    deliver.put("serverMsgId", serverMsgId);

                    Collection<io.netty.channel.Channel> targets = channelUserManager.getChannels(target);
                    if (targets.isEmpty()) {
                        // 目标不在线，告知发送者状态
                        ObjectNode ack = mapper.createObjectNode();
                        ack.put("type", "DELIVER_ACK");
                        ack.put("clientMsgId", clientMsgId);
                        ack.put("serverMsgId", serverMsgId);
                        ack.put("status", "TARGET_OFFLINE");
                        ch.writeAndFlush(new TextWebSocketFrame(mapper.writeValueAsString(ack)));
                        logger.info("deliver failed - target offline: from={} to={} clientMsgId={} serverMsgId={}", fromUserId, target, clientMsgId, serverMsgId);
                    } else {
                        for (io.netty.channel.Channel c : targets) {
                            c.writeAndFlush(new TextWebSocketFrame(mapper.writeValueAsString(deliver)));
                        }
                        ObjectNode ack = mapper.createObjectNode();
                        ack.put("type", "DELIVER_ACK");
                        ack.put("clientMsgId", clientMsgId);
                        ack.put("serverMsgId", serverMsgId);
                        ack.put("status", "DELIVERED");
                        ch.writeAndFlush(new TextWebSocketFrame(mapper.writeValueAsString(ack)));
                        logger.info("deliver success: from={} to={} clientMsgId={} serverMsgId={} channelsSent={}", fromUserId, target, clientMsgId, serverMsgId, targets.size());
                    }
                } else {
                    // 非 CHAT 类型，回显原文
                    ch.writeAndFlush(new TextWebSocketFrame(textMsg));
                }
            } catch (Exception ex) {
                logger.error("process text frame error", ex);
                ch.writeAndFlush(new TextWebSocketFrame("error"));
            }
        } else if (frame instanceof PingWebSocketFrame) {
            ctx.writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
        } else if (frame instanceof PongWebSocketFrame) {
            // 忽略
        } else if (frame instanceof CloseWebSocketFrame) {
            ctx.close();
        } else {
            // 其它帧类型暂不支持
            logger.warn("unsupported frame: {}", frame.getClass().getName());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("websocket handler exception", cause);
        Long userId = ctx.channel().attr(NettyAttr.USER_ID).get();
        logger.warn("exception on channel {} userId={}, closing", ctx.channel().id(), userId);
        channelUserManager.unbindByChannel(ctx.channel());
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Long userId = ctx.channel().attr(NettyAttr.USER_ID).get();
        logger.info("channel inactive {} userId={}", ctx.channel().id(), userId);
        channelUserManager.unbindByChannel(ctx.channel());
        super.channelInactive(ctx);
    }
}

