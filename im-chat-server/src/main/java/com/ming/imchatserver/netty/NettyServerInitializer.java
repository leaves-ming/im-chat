package com.ming.imchatserver.netty;

import com.ming.imchatserver.config.NettyProperties;
import com.ming.imchatserver.service.AuthService;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;

/**
 * Netty channel initializer: 支持 HTTP (REST) 与 WebSocket 单端口复用
 */
public class NettyServerInitializer extends ChannelInitializer<SocketChannel> {

	private final NettyProperties properties;
	private final AuthService authService;
	private final ChannelUserManager channelUserManager;

	public NettyServerInitializer(NettyProperties properties, AuthService authService, ChannelUserManager channelUserManager) {
		this.properties = properties;
		this.authService = authService;
		this.channelUserManager = channelUserManager;
	}

	@Override
	protected void initChannel(SocketChannel ch) throws Exception {
		// 空闲检测
		ch.pipeline().addLast(new IdleStateHandler(properties.getReaderIdleSeconds(), properties.getWriterIdleSeconds(), properties.getAllIdleSeconds()));
		// HTTP codec & aggregator
		ch.pipeline().addLast(new HttpServerCodec());
		ch.pipeline().addLast(new HttpObjectAggregator(properties.getMaxContentLength()));
		ch.pipeline().addLast(new ChunkedWriteHandler());

		// 先处理 REST 请求（如 /api/auth/login），非 REST 请求交由后续处理
		ch.pipeline().addLast(new HttpRequestHandler(properties, authService));

		// 握手认证必须在 WebSocketServerProtocolHandler 之前完成
		ch.pipeline().addLast(new WebSocketHandshakeAuthHandler(properties, authService, channelUserManager));
		ch.pipeline().addLast(new WebSocketServerProtocolHandler(properties.getWebsocketPath(), null, true, properties.getMaxContentLength()));
		ch.pipeline().addLast(new WebSocketFrameHandler(channelUserManager));
		ch.pipeline().addLast(new IdleEventHandler(channelUserManager));
	}
}


