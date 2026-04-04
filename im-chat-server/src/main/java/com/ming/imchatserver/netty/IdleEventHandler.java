package com.ming.imchatserver.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * 处理连接空闲与异常事件的 Handler。
 * <p>
 * 主要职责：
 * - 在读空闲/全空闲时主动关闭连接，避免僵尸连接长期占用资源；
 * - 在异常或断连时清理 {@link ChannelUserManager} 中的用户绑定关系。
 */

    public class IdleEventHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(IdleEventHandler.class);
    private final ChannelUserManager channelUserManager;
    /**
     * @param channelUserManager 在线连接映射管理器，用于断连清理
     */
    
    public IdleEventHandler(ChannelUserManager channelUserManager) {
        this.channelUserManager = channelUserManager;
    }

    @Override
    /**
     * 处理 Netty 触发的用户事件，重点处理空闲事件。
     */
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent e) {
            if (e.state() == IdleState.READER_IDLE) {
                Long userId = ctx.channel().attr(NettyAttr.USER_ID).get();
                logger.info("reader idle, closing channel {} userId={}", ctx.channel().id(), userId);
                channelUserManager.unbindByChannel(ctx.channel());
                ctx.close();
            } else if (e.state() == IdleState.ALL_IDLE) {
                Long userId = ctx.channel().attr(NettyAttr.USER_ID).get();
                logger.info("all idle, closing channel {} userId={}", ctx.channel().id(), userId);
                channelUserManager.unbindByChannel(ctx.channel());
                ctx.close();
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    /**
     * 发生异常时关闭连接并清理绑定关系。
     */
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        Long userId = ctx.channel().attr(NettyAttr.USER_ID).get();
        logger.error("exception on channel {} userId={}, closing", ctx.channel().id(), userId, cause);
        channelUserManager.unbindByChannel(ctx.channel());
        ctx.close();
    }

    @Override
    /**
     * 连接变为非激活状态时，确保用户与 Channel 映射被移除。
     */
    
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Long userId = ctx.channel().attr(NettyAttr.USER_ID).get();
        logger.info("channel inactive {} userId={}", ctx.channel().id(), userId);
        channelUserManager.unbindByChannel(ctx.channel());
        super.channelInactive(ctx);
    }
}
