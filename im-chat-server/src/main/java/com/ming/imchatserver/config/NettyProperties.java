package com.ming.imchatserver.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Netty 运行参数配置。
 * <p>
 * 对应配置前缀：{@code im.netty}。
 */
@Component
@ConfigurationProperties(prefix = "im.netty")
@Getter
@Setter
public class NettyProperties {
    /** Netty 监听端口。 */
    private int port = 8080;
    /** WebSocket 路径。 */
    private String websocketPath = "/ws";
    /** 读空闲阈值（秒）。 */
    private int readerIdleSeconds = 60;
    /** 写空闲阈值（秒）。 */
    private int writerIdleSeconds = 0;
    /** 总空闲阈值（秒）。 */
    private int allIdleSeconds = 120;
    /** HTTP 聚合器最大内容长度。 */
    private int maxContentLength = 10 * 1024 * 1024;
    /** JWT token 过期时间（秒）。 */
    private long tokenExpireSeconds = 3600;

    /** 是否开启 Origin 白名单校验。 */
    private boolean originCheckEnabled = false;
    /** 允许的 Origin 列表。 */
    private List<String> originWhitelist = new ArrayList<>();

    /** 同步批大小（用于重连后自动同步）。 */
    private int syncBatchSize = 50;
    /** 离线拉取单次最大条数。 */
    private int offlinePullMaxLimit = 200;
    /** 单聊是否要求双方存在 ACTIVE 联系关系（默认关闭，兼容旧逻辑）。 */
    private boolean singleChatRequireActiveContact = false;
    /** 群推送单批最大 channel 数，避免单次大群写满调度线程。 */
    private int groupPushBatchSize = 200;
    /** 群推送调度并行度（线程池 core/max）。 */
    private int groupPushParallelism = 4;
    /** 群推送调度队列容量。 */
    private int groupPushQueueCapacity = 1000;
}
