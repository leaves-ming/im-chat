package com.ming.imchatserver.netty;

import com.ming.imchatserver.config.NettyProperties;
import com.ming.imchatserver.service.AuthService;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import java.net.InetSocketAddress;

@Component
public class NettyWebSocketServer {

    private static final Logger logger = LoggerFactory.getLogger(NettyWebSocketServer.class);

    private final NettyProperties properties;
    private final AuthService authService;
    private final ChannelUserManager channelUserManager;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    @Autowired
    public NettyWebSocketServer(NettyProperties properties, AuthService authService, ChannelUserManager channelUserManager) {
        this.properties = properties;
        this.authService = authService;
        this.channelUserManager = channelUserManager;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() throws InterruptedException {
        // sanity check: Spring embedded server should be disabled (server.port=0 or web-application-type=none)
        logger.info("Starting Netty server (configured port={})", properties.getPort());
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .localAddress(new InetSocketAddress(properties.getPort()))
                .childHandler(new NettyServerInitializer(properties, authService, channelUserManager));
        serverChannel = b.bind().sync().channel();
        logger.info("Netty server started and listening on {}", properties.getPort());
    }

    @EventListener(ContextClosedEvent.class)
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

