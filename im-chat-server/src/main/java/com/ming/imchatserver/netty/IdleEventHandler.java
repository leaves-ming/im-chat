package com.ming.imchatserver.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IdleEventHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(IdleEventHandler.class);
    private final ChannelUserManager channelUserManager;

    public IdleEventHandler(ChannelUserManager channelUserManager) {
        this.channelUserManager = channelUserManager;
    }

    @Override
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
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        Long userId = ctx.channel().attr(NettyAttr.USER_ID).get();
        logger.error("exception on channel {} userId={}, closing", ctx.channel().id(), userId, cause);
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

