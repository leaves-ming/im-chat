package com.ming.imchatserver.netty;

import com.ming.imchatserver.config.NettyProperties;
import com.ming.imchatserver.mapper.DeliveryMapper;
import com.ming.imchatserver.metrics.MetricsService;
import com.ming.imchatserver.service.AuthService;
import com.ming.imchatserver.service.ContactService;
import com.ming.imchatserver.service.GroupMessageService;
import com.ming.imchatserver.service.GroupService;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.concurrent.Executor;
/**
 * Netty channel initializer: 支持 HTTP (REST) 与 WebSocket 单端口复用
 */

    public class NettyServerInitializer extends ChannelInitializer<SocketChannel> {

    private final NettyProperties properties;
    private final AuthService authService;
    private final ChannelUserManager channelUserManager;
    private final com.ming.imchatserver.service.MessageService messageService;
    private final ContactService contactService;
    private final GroupService groupService;
    private final GroupMessageService groupMessageService;
    private final DeliveryMapper deliveryMapper;
    private final MetricsService metricsService;
    private final Executor groupPushExecutor;
    private final GroupPushCoordinator groupPushCoordinator;
    /**
     * 创建 Channel 初始化器，并注入各业务 Handler 所需依赖。
     */
    
    public NettyServerInitializer(NettyProperties properties,
                                  AuthService authService,
                                  ChannelUserManager channelUserManager,
                                  com.ming.imchatserver.service.MessageService messageService,
                                  ContactService contactService,
                                  GroupService groupService,
                                  GroupMessageService groupMessageService,
                                  DeliveryMapper deliveryMapper,
                                  MetricsService metricsService,
                                  Executor groupPushExecutor,
                                  GroupPushCoordinator groupPushCoordinator) {
        this.properties = properties;
        this.authService = authService;
        this.channelUserManager = channelUserManager;
        this.messageService = messageService;
        this.contactService = contactService;
        this.groupService = groupService;
        this.groupMessageService = groupMessageService;
        this.deliveryMapper = deliveryMapper;
        this.metricsService = metricsService;
        this.groupPushExecutor = groupPushExecutor;
        this.groupPushCoordinator = groupPushCoordinator;
    }

    @Override
    /**
     * 为每个新连接构建处理链。
     * <p>
     * 链路顺序：空闲检测 -> HTTP 编解码 -> 登录接口 -> WS 握手鉴权 -> WS 协议升级
     * -> 业务帧鉴权 -> 业务处理 -> 空闲/异常兜底处理。
     */
    
    protected void initChannel(SocketChannel ch) throws Exception {
        // 空闲检测
        ch.pipeline().addLast(new IdleStateHandler(properties.getReaderIdleSeconds(), properties.getWriterIdleSeconds(), properties.getAllIdleSeconds()));
        // HTTP codec & aggregator
        ch.pipeline().addLast(new HttpServerCodec());
        ch.pipeline().addLast(new HttpObjectAggregator(properties.getMaxContentLength()));
        ch.pipeline().addLast(new ChunkedWriteHandler());

        // 先处理 REST 请求（如 /api/auth/login），非 REST 请求交由后续处理
        ch.pipeline().addLast(new HttpRequestHandler(properties, authService, metricsService));

        // 握手认证必须在 WebSocketServerProtocolHandler 之前完成
        ch.pipeline().addLast(new WebSocketHandshakeAuthHandler(properties, authService, channelUserManager));
        ch.pipeline().addLast(new WebSocketServerProtocolHandler(properties.getWebsocketPath(), null, true, properties.getMaxContentLength()));
        // 业务帧鉴权（要求 channel 已绑定 userId）
        ch.pipeline().addLast(new WsBusinessAuthHandler(channelUserManager));
        ch.pipeline().addLast(new WebSocketFrameHandler(channelUserManager, messageService, contactService, groupService, groupMessageService, properties, deliveryMapper, metricsService, groupPushExecutor, groupPushCoordinator));
        ch.pipeline().addLast(new IdleEventHandler(channelUserManager));
    }
}
