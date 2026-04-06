package com.ming.imchatserver.netty;

import com.ming.imchatserver.application.facade.MessageFacade;
import com.ming.imchatserver.application.facade.SocialFacade;
import com.ming.imchatserver.config.InstanceProperties;
import com.ming.imchatserver.config.NettyProperties;
import com.ming.imchatserver.config.RateLimitProperties;
import com.ming.imchatserver.metrics.MetricsService;
import com.ming.imchatserver.observability.RuntimeObservabilitySettings;
import com.ming.imchatserver.service.AuthService;
import com.ming.imchatserver.service.RateLimitService;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;

import java.util.concurrent.Executor;
/**
 * Netty channel initializer: 支持 HTTP (REST) 与 WebSocket 单端口复用
 */

    public class NettyServerInitializer extends ChannelInitializer<SocketChannel> {

    private final NettyProperties properties;
    private final AuthService authService;
    private final ChannelUserManager channelUserManager;
    private final SocialFacade socialFacade;
    private final MetricsService metricsService;
    private final RateLimitService rateLimitService;
    private final RateLimitProperties rateLimitProperties;
    private final RuntimeObservabilitySettings runtimeObservabilitySettings;
    private final HealthEndpoint healthEndpoint;
    private final InstanceProperties instanceProperties;
    private final Executor wsBusinessExecutor;
    private final GroupPushDispatcher groupPushDispatcher;
    private final MessageFacade messageFacade;
    /**
     * 创建 Channel 初始化器，并注入各业务 Handler 所需依赖。
     */
    
    public NettyServerInitializer(NettyProperties properties,
                                  AuthService authService,
                                  ChannelUserManager channelUserManager,
                                  SocialFacade socialFacade,
                                  MetricsService metricsService,
                                  RateLimitService rateLimitService,
                                  RateLimitProperties rateLimitProperties,
                                  RuntimeObservabilitySettings runtimeObservabilitySettings,
                                  HealthEndpoint healthEndpoint,
                                  InstanceProperties instanceProperties,
                                  GroupPushDispatcher groupPushDispatcher,
                                  MessageFacade messageFacade,
                                  Executor wsBusinessExecutor) {
        this.properties = properties;
        this.authService = authService;
        this.channelUserManager = channelUserManager;
        this.socialFacade = socialFacade;
        this.metricsService = metricsService;
        this.rateLimitService = rateLimitService;
        this.rateLimitProperties = rateLimitProperties;
        this.runtimeObservabilitySettings = runtimeObservabilitySettings;
        this.healthEndpoint = healthEndpoint;
        this.instanceProperties = instanceProperties;
        this.groupPushDispatcher = groupPushDispatcher;
        this.messageFacade = messageFacade;
        this.wsBusinessExecutor = wsBusinessExecutor;
    }

    @Override
    /**
     * 为每个新连接构建处理链。
     * <p>
     * 链路顺序：空闲检测 -> HTTP 编解码 -> HTTP 辅助接口 -> WS 握手鉴权 -> WS 协议升级
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
        ch.pipeline().addLast(new HttpRequestHandler(properties, authService, metricsService,
                rateLimitService, rateLimitProperties,
                runtimeObservabilitySettings, healthEndpoint, instanceProperties));

        // 握手认证必须在 WebSocketServerProtocolHandler 之前完成。
        // gateway 转发的内部路径会在握手认证阶段重写回外部标准路径。
        ch.pipeline().addLast(new WebSocketHandshakeAuthHandler(properties, authService, channelUserManager, runtimeObservabilitySettings));
        ch.pipeline().addLast(new WebSocketServerProtocolHandler(properties.getWebsocketPath(), null, true, properties.getMaxContentLength()));
        // 业务帧鉴权（要求 channel 已绑定 userId）
        ch.pipeline().addLast(new WsBusinessAuthHandler(channelUserManager));
        ch.pipeline().addLast(new WebSocketFrameHandler(channelUserManager, socialFacade,
                properties, groupPushDispatcher, messageFacade,
                wsBusinessExecutor));
        ch.pipeline().addLast(new IdleEventHandler(channelUserManager));
    }
}
