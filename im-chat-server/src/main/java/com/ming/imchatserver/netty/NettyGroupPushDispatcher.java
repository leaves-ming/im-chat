package com.ming.imchatserver.netty;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ming.imchatserver.application.model.GroupMessageView;
import com.ming.imchatserver.config.NettyProperties;
import com.ming.imchatserver.message.MessageContentCodec;
import com.ming.imchatserver.message.RecallProtocolSupport;
import com.ming.imchatserver.metrics.MetricsService;
import com.ming.imchatserver.service.remote.RemoteGroupService;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 基于 Netty 的群推送分发器。
 */
@Component
public class NettyGroupPushDispatcher implements GroupPushDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(NettyGroupPushDispatcher.class);
    private static final int DEFAULT_GROUP_PUSH_BATCH_SIZE = 200;

    private final RemoteGroupService groupService;
    private final ChannelUserManager channelUserManager;
    private final Executor groupPushExecutor;
    private final GroupPushCoordinator groupPushCoordinator;
    private final MetricsService metricsService;
    private final NettyProperties nettyProperties;
    private final ObjectMapper mapper = new ObjectMapper();

    public NettyGroupPushDispatcher(RemoteGroupService groupService,
                                    ChannelUserManager channelUserManager,
                                    @Qualifier("groupPushExecutor") Executor groupPushExecutor,
                                    GroupPushCoordinator groupPushCoordinator,
                                    MetricsService metricsService,
                                    NettyProperties nettyProperties) {
        this.groupService = groupService;
        this.channelUserManager = channelUserManager;
        this.groupPushExecutor = groupPushExecutor;
        this.groupPushCoordinator = groupPushCoordinator;
        this.metricsService = metricsService;
        this.nettyProperties = nettyProperties;
    }

    @Override
    public void dispatchGroupPush(Long groupId, GroupMessageView message) throws Exception {
        List<Channel> targetChannels = collectMemberChannels(groupId);
        String payload = buildGroupPushPayload(message);
        int attemptedChannels = targetChannels.size();
        if (metricsService != null) {
            metricsService.incrementGroupPushAttempt(attemptedChannels);
        }
        if (attemptedChannels == 0) {
            return;
        }
        int batchSize = nettyProperties != null && nettyProperties.getGroupPushBatchSize() > 0
                ? nettyProperties.getGroupPushBatchSize()
                : DEFAULT_GROUP_PUSH_BATCH_SIZE;
        if (groupPushCoordinator == null) {
            dispatchGroupPushInOrder(groupId, message.serverMsgId(), targetChannels, payload, attemptedChannels, batchSize);
            return;
        }
        groupPushCoordinator.enqueue(groupId,
                () -> dispatchGroupPushInOrder(groupId, message.serverMsgId(), targetChannels, payload, attemptedChannels, batchSize));
    }

    @Override
    public void notifyGroupRecall(Channel requester, GroupMessageView message) throws Exception {
        if (message == null) {
            return;
        }
        String payload = mapper.writeValueAsString(
                RecallProtocolSupport.buildGroupRecallNode(mapper, "GROUP_MSG_RECALL_NOTIFY", message));
        for (Channel channel : collectMemberChannels(message.groupId())) {
            if (channel == requester) {
                continue;
            }
            channel.writeAndFlush(new TextWebSocketFrame(payload));
        }
    }

    private List<Channel> collectMemberChannels(Long groupId) {
        List<Channel> targetChannels = new ArrayList<>();
        for (Long userId : groupService.listActiveMemberUserIds(groupId)) {
            targetChannels.addAll(channelUserManager.getChannels(userId));
        }
        return targetChannels;
    }

    private String buildGroupPushPayload(GroupMessageView message) throws Exception {
        ObjectNode push = mapper.createObjectNode();
        push.put("type", "GROUP_MSG_PUSH");
        push.put("groupId", message.groupId());
        push.put("seq", message.seq());
        push.put("serverMsgId", message.serverMsgId());
        push.put("fromUserId", message.fromUserId());
        push.put("msgType", MessageContentCodec.normalizeMsgType(message.msgType()));
        MessageContentCodec.writeProtocolContent(push, "content", message.msgType(), message.content());
        push.put("status", "SENT");
        push.put("createdAt", message.createdAt().toInstant().toString());
        return mapper.writeValueAsString(push);
    }

    private CompletableFuture<Void> dispatchGroupPushInOrder(Long groupId,
                                                             String serverMsgId,
                                                             List<Channel> targetChannels,
                                                             String pushPayload,
                                                             int attemptedChannels,
                                                             int batchSize) {
        if (groupPushExecutor == null) {
            pushGroupMessageInBatches(groupId, serverMsgId, targetChannels, pushPayload, batchSize);
            return CompletableFuture.completedFuture(null);
        }
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        int batchNo = 0;
        for (int start = 0; start < attemptedChannels; start += batchSize) {
            int end = Math.min(start + batchSize, attemptedChannels);
            int currentBatchNo = ++batchNo;
            List<Channel> batchChannels = new ArrayList<>(targetChannels.subList(start, end));
            CompletableFuture<Void> future = new CompletableFuture<>();
            try {
                groupPushExecutor.execute(() -> {
                    try {
                        pushGroupMessageBatch(groupId, serverMsgId, currentBatchNo, batchChannels, pushPayload);
                    } finally {
                        future.complete(null);
                    }
                });
                futures.add(future);
            } catch (RejectedExecutionException ex) {
                if (metricsService != null) {
                    metricsService.incrementGroupPushReject();
                }
                logger.warn("group push executor rejected groupId={} serverMsgId={} batchNo={}", groupId, serverMsgId, currentBatchNo, ex);
            }
        }
        return futures.isEmpty() ? CompletableFuture.completedFuture(null)
                : CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    private void pushGroupMessageInBatches(Long groupId,
                                           String serverMsgId,
                                           List<Channel> channels,
                                           String payload,
                                           int batchSize) {
        int batchNo = 0;
        for (int start = 0; start < channels.size(); start += batchSize) {
            int end = Math.min(start + batchSize, channels.size());
            pushGroupMessageBatch(groupId, serverMsgId, ++batchNo, new ArrayList<>(channels.subList(start, end)), payload);
        }
    }

    private void pushGroupMessageBatch(Long groupId,
                                       String serverMsgId,
                                       int batchNo,
                                       List<Channel> batchChannels,
                                       String payload) {
        for (Channel targetChannel : batchChannels) {
            try {
                targetChannel.writeAndFlush(new TextWebSocketFrame(payload)).addListener(future -> {
                    if (!future.isSuccess() && metricsService != null) {
                        metricsService.incrementGroupPushFail();
                    }
                });
            } catch (Exception ex) {
                if (metricsService != null) {
                    metricsService.incrementGroupPushFail();
                }
                logger.warn("group push failed groupId={} serverMsgId={} batchNo={}", groupId, serverMsgId, batchNo, ex);
            }
        }
    }
}
