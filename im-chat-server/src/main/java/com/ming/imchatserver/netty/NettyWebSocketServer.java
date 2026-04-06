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
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;

import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
/**
 * Netty WebSocket 服务启动器。
 * <p>
 * 在 Spring 容器启动完成后初始化 Netty 服务端，并在容器关闭时优雅释放资源。
 */
@Component
@ConditionalOnProperty(name = "im.netty.enabled", havingValue = "true", matchIfMissing = true)
public class NettyWebSocketServer {

    private static final Logger logger = LoggerFactory.getLogger(NettyWebSocketServer.class);

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

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    @Autowired
    /**
     * @param properties          Netty 相关配置
     * @param authService         认证服务（用于握手阶段）
     * @param channelUserManager  在线连接管理
     * @param messageFacade       消息域远程门面
     */
    
    public NettyWebSocketServer(NettyProperties properties,
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
                                @Qualifier("imWsBusinessExecutor") Executor wsBusinessExecutor) {
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

    @EventListener(ApplicationReadyEvent.class)    /**
     * Spring 应用就绪后启动 Netty 监听。
     */
    
    public void start() throws InterruptedException {
        // sanity check: Spring embedded server should be disabled (server.port=0 or web-application-type=none)
        logger.info("Starting Netty server (configured port={})", properties.getPort());
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .localAddress(new InetSocketAddress(properties.getPort()))
                .childHandler(new NettyServerInitializer(properties, authService, channelUserManager,
                        socialFacade, metricsService,
                        rateLimitService, rateLimitProperties,
                        runtimeObservabilitySettings, healthEndpoint, instanceProperties,
                        groupPushDispatcher,
                        messageFacade,
                        wsBusinessExecutor));
        serverChannel = b.bind().sync().channel();
        logger.info("Netty server started and listening on {}", properties.getPort());
    }

    @EventListener(ContextClosedEvent.class)    /**
     * Spring 容器关闭时停止 Netty 服务并释放线程池。
     */
    
    public void shutdown() {
        logger.info("shutting down netty server...");
        try {
            if (serverChannel != null) {
                serverChannel.close();
            }
            if (bossGroup != null) bossGroup.shutdownGracefully();
            if (workerGroup != null) workerGroup.shutdownGracefully();
        } catch (Exception ex) {
            logger.error("error shutting down netty", ex);
        }
    }
}
