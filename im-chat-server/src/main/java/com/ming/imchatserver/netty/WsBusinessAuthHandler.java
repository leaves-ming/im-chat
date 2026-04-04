package com.ming.imchatserver.netty;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ming.imchatserver.observability.TraceContextSupport;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * WsBusinessAuthHandler - 在 WebSocket 业务帧进入前进行鉴权拦截
 */

    public class WsBusinessAuthHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(WsBusinessAuthHandler.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final ChannelUserManager channelUserManager;
    /**
     * @param channelUserManager 在线连接映射管理器，用于异常日志补充上下文
     */
    
    public WsBusinessAuthHandler(ChannelUserManager channelUserManager) {
        this.channelUserManager = channelUserManager;
    }

    @Override
    /**
     * 在业务帧进入业务处理器前进行绑定状态校验。
     * <p>
     * 仅拦截文本业务帧；Ping/Pong/Close 等系统帧直接放行。
     */
    
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof WebSocketFrame frame) {
            // allow system frames
            if (frame instanceof PingWebSocketFrame || frame instanceof PongWebSocketFrame || frame instanceof CloseWebSocketFrame) {
                super.channelRead(ctx, msg);
                return;
            }
            if (frame instanceof TextWebSocketFrame) {
                Long userId = ctx.channel().attr(NettyAttr.USER_ID).get();
                Boolean bound = ctx.channel().attr(NettyAttr.BOUND).get();
                if (userId == null || bound == null || !bound) {
                    // return error frame
                    ObjectNode err = mapper.createObjectNode();
                    err.put("type", "ERROR");
                    err.put("code", "UNAUTHORIZED");
                    err.put("msg", "channel not authenticated or bound");
                    String traceId = TraceContextSupport.currentTraceId(ctx.channel());
                    if (traceId == null) {
                        traceId = java.util.UUID.randomUUID().toString().replace("-", "");
                        ctx.channel().attr(NettyAttr.TRACE_ID).set(traceId);
                    }
                    err.put("traceId", traceId);
                    // attempt to fetch reverse mapping for more context
                    Long mapped = channelUserManager.getUserIdByChannelId(ctx.channel().id().asLongText());
                    logger.warn("unauthorized business frame from channel {} mappedUser={} traceId={}", ctx.channel().id(), mapped, traceId);
                    ctx.writeAndFlush(new TextWebSocketFrame(mapper.writeValueAsString(err))).addListener(f -> ctx.close());
                    return;
                }
            }
        }
        super.channelRead(ctx, msg);
    }
}
