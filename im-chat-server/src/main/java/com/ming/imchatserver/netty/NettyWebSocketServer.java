package com.ming.imchatserver.netty;

import com.ming.imchatserver.config.FileStorageProperties;
import com.ming.imchatserver.config.NettyProperties;
import com.ming.imchatserver.mapper.DeliveryMapper;
import com.ming.imchatserver.metrics.MetricsService;
import com.ming.imchatserver.service.AuthService;
import com.ming.imchatserver.service.ContactService;
import com.ming.imchatserver.service.FileService;
import com.ming.imchatserver.service.GroupMessageService;
import com.ming.imchatserver.service.GroupService;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
/**
 * Netty WebSocket 服务启动器。
 * <p>
 * 在 Spring 容器启动完成后初始化 Netty 服务端，并在容器关闭时优雅释放资源。
 */
@Component
public class NettyWebSocketServer {

    private static final Logger logger = LoggerFactory.getLogger(NettyWebSocketServer.class);

    private final NettyProperties properties;
    private final AuthService authService;
    private final ChannelUserManager channelUserManager;
    private final com.ming.imchatserver.service.MessageService messageService;
    private final ContactService contactService;
    private final GroupService groupService;
    private final GroupMessageService groupMessageService;
    private final DeliveryMapper deliveryMapper;
    private final MetricsService metricsService;
    private final FileService fileService;
    private final FileStorageProperties fileStorageProperties;
    private final Executor groupPushExecutor;
    private final GroupPushCoordinator groupPushCoordinator;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    @Autowired
    /**
     * @param properties          Netty 相关配置
     * @param authService         认证服务（用于握手阶段）
     * @param channelUserManager  在线连接管理
     * @param messageService      消息服务
     * @param deliveryMapper      ACK 持久化组件
     */
    
    public NettyWebSocketServer(NettyProperties properties,
                                AuthService authService,
                                ChannelUserManager channelUserManager,
                                com.ming.imchatserver.service.MessageService messageService,
                                ContactService contactService,
                                GroupService groupService,
                                GroupMessageService groupMessageService,
                                DeliveryMapper deliveryMapper,
                                MetricsService metricsService,
                                FileService fileService,
                                FileStorageProperties fileStorageProperties,
                                @Qualifier("groupPushExecutor") Executor groupPushExecutor,
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
        this.fileService = fileService;
        this.fileStorageProperties = fileStorageProperties;
        this.groupPushExecutor = groupPushExecutor;
        this.groupPushCoordinator = groupPushCoordinator;
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
                .childHandler(new NettyServerInitializer(properties, authService, channelUserManager, messageService, contactService, groupService, groupMessageService, deliveryMapper, metricsService, fileService, fileStorageProperties, groupPushExecutor, groupPushCoordinator));
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
