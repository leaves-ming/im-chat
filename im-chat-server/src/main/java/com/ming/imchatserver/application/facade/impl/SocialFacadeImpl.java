package com.ming.imchatserver.application.facade.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ming.imchatserver.application.facade.SocialFacade;
import com.ming.imchatserver.config.NettyProperties;
import com.ming.imchatserver.config.RateLimitProperties;
import com.ming.imchatserver.config.RedisStateProperties;
import com.ming.imchatserver.dao.GroupMessageDO;
import com.ming.imchatserver.message.MessageContentCodec;
import com.ming.imchatserver.metrics.MetricsService;
import com.ming.imchatserver.netty.ChannelUserManager;
import com.ming.imchatserver.netty.GroupPushCoordinator;
import com.ming.imchatserver.service.ContactService;
import com.ming.imchatserver.service.GroupMessageService;
import com.ming.imchatserver.service.GroupService;
import com.ming.imchatserver.service.IdempotencyService;
import com.ming.imchatserver.service.RateLimitService;
import com.ming.imchatserver.service.SingleChatPermissionCapable;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 联系人与群组应用门面默认实现。
 */
@Component
public class SocialFacadeImpl implements SocialFacade {

    private static final Logger logger = LoggerFactory.getLogger(SocialFacadeImpl.class);
    private static final int DEFAULT_GROUP_PUSH_BATCH_SIZE = 200;

    private final ContactService contactService;
    private final GroupService groupService;
    private final GroupMessageService groupMessageService;
    private final ChannelUserManager channelUserManager;
    private final Executor groupPushExecutor;
    private final GroupPushCoordinator groupPushCoordinator;
    private final MetricsService metricsService;
    private final IdempotencyService idempotencyService;
    private final RateLimitService rateLimitService;
    private final RateLimitProperties rateLimitProperties;
    private final RedisStateProperties redisStateProperties;
    private final NettyProperties nettyProperties;
    private final ObjectMapper mapper = new ObjectMapper();

    public SocialFacadeImpl(ContactService contactService,
                            GroupService groupService,
                            GroupMessageService groupMessageService,
                            ChannelUserManager channelUserManager,
                            Executor groupPushExecutor,
                            GroupPushCoordinator groupPushCoordinator,
                            MetricsService metricsService,
                            IdempotencyService idempotencyService,
                            RateLimitService rateLimitService,
                            RateLimitProperties rateLimitProperties,
                            RedisStateProperties redisStateProperties,
                            NettyProperties nettyProperties) {
        this.contactService = contactService;
        this.groupService = groupService;
        this.groupMessageService = groupMessageService;
        this.channelUserManager = channelUserManager;
        this.groupPushExecutor = groupPushExecutor;
        this.groupPushCoordinator = groupPushCoordinator;
        this.metricsService = metricsService;
        this.idempotencyService = idempotencyService;
        this.rateLimitService = rateLimitService;
        this.rateLimitProperties = rateLimitProperties;
        this.redisStateProperties = redisStateProperties;
        this.nettyProperties = nettyProperties;
    }

    @Override
    public ContactService.Result addContact(Long ownerUserId, Long peerUserId) {
        return contactService.addOrActivateContact(ownerUserId, peerUserId);
    }

    @Override
    public ContactService.Result removeContact(Long ownerUserId, Long peerUserId) {
        return contactService.removeOrDeactivateContact(ownerUserId, peerUserId);
    }

    @Override
    public ContactService.ContactPageResult listContacts(Long ownerUserId, Long cursorPeerUserId, int limit) {
        return contactService.listActiveContacts(ownerUserId, cursorPeerUserId, limit);
    }

    @Override
    public com.ming.imchatserver.service.GroupService.JoinGroupResult joinGroup(Long groupId, Long userId) {
        return groupService.joinGroup(groupId, userId);
    }

    @Override
    public com.ming.imchatserver.service.GroupService.QuitGroupResult quitGroup(Long groupId, Long userId) {
        return groupService.quitGroup(groupId, userId);
    }

    @Override
    public com.ming.imchatserver.service.GroupService.MemberPageResult listMembers(Long groupId, Long cursorUserId, int limit) {
        return groupService.listMembers(groupId, cursorUserId, limit);
    }

    @Override
    public GroupMessageService.PersistResult sendGroupChat(Long groupId,
                                                           Long fromUserId,
                                                           String clientMsgId,
                                                           String msgType,
                                                           String content) {
        if (!groupService.isActiveMember(groupId, fromUserId)) {
            throw new SecurityException("sender is not active group member");
        }
        if (!consumeMessageRateLimit(fromUserId)) {
            throw new IllegalArgumentException("RATE_LIMITED:message send rate exceeded");
        }
        if (!claimClientMessageId(fromUserId, clientMsgId)) {
            throw new IllegalArgumentException("DUPLICATE_REQUEST:clientMsgId replay detected");
        }
        try {
            return groupMessageService.persistMessage(groupId, fromUserId, clientMsgId, msgType, content);
        } catch (RuntimeException ex) {
            releaseClientMessageId(fromUserId, clientMsgId);
            throw ex;
        }
    }

    @Override
    public GroupMessageService.PullResult pullGroupOffline(Long groupId, Long userId, Long cursorSeq, int limit) {
        if (!groupService.isActiveMember(groupId, userId)) {
            throw new SecurityException("user is not active group member");
        }
        return groupMessageService.pullOffline(groupId, userId, cursorSeq, limit);
    }

    @Override
    public GroupMessageDO recallGroupMessage(Long operatorUserId, String serverMsgId, long recallWindowSeconds) {
        GroupMessageDO existing = groupMessageService.findByServerMsgId(serverMsgId);
        if (existing == null) {
            throw new IllegalArgumentException("serverMsgId not found");
        }
        return groupMessageService.recallMessage(operatorUserId, serverMsgId, recallWindowSeconds);
    }

    @Override
    public void dispatchGroupPush(Long groupId, GroupMessageDO message) throws Exception {
        List<Long> memberUserIds = groupService.listActiveMemberUserIds(groupId);
        List<Channel> targetChannels = new ArrayList<>();
        for (Long userId : memberUserIds) {
            targetChannels.addAll(channelUserManager.getChannels(userId));
        }
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
            dispatchGroupPushInOrder(groupId, message.getServerMsgId(), targetChannels, payload, attemptedChannels, batchSize);
            return;
        }
        groupPushCoordinator.enqueue(groupId,
                () -> dispatchGroupPushInOrder(groupId, message.getServerMsgId(), targetChannels, payload, attemptedChannels, batchSize));
    }

    @Override
    public boolean isSingleChatAllowed(Long fromUserId, Long toUserId) {
        if (contactService == null) {
            return true;
        }
        if (contactService instanceof SingleChatPermissionCapable permissionCapable) {
            return permissionCapable.isSingleChatAllowed(fromUserId, toUserId);
        }
        return contactService.isActiveContact(fromUserId, toUserId)
                && contactService.isActiveContact(toUserId, fromUserId);
    }

    @Override
    public boolean isGroupMember(Long groupId, Long userId) {
        return groupService != null && groupService.isActiveMember(groupId, userId);
    }

    @Override
    public List<Long> listActiveMemberUserIds(Long groupId) {
        return groupService.listActiveMemberUserIds(groupId);
    }

    private String buildGroupPushPayload(GroupMessageDO message) throws Exception {
        ObjectNode push = mapper.createObjectNode();
        push.put("type", "GROUP_MSG_PUSH");
        push.put("groupId", message.getGroupId());
        push.put("seq", message.getSeq());
        push.put("serverMsgId", message.getServerMsgId());
        push.put("fromUserId", message.getFromUserId());
        push.put("msgType", MessageContentCodec.normalizeMsgType(message.getMsgType()));
        MessageContentCodec.writeProtocolContent(push, "content", message.getMsgType(), message.getContent());
        push.put("status", "SENT");
        push.put("createdAt", message.getCreatedAt().toInstant().toString());
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

    private boolean consumeMessageRateLimit(Long userId) {
        if (rateLimitService == null || rateLimitProperties == null || userId == null) {
            return true;
        }
        return rateLimitService.checkAndIncrement(
                "message_send",
                "userId",
                String.valueOf(userId),
                rateLimitProperties.getMessageSend().getLimit(),
                rateLimitProperties.getMessageSend().getWindowSeconds()).allowed();
    }

    private boolean claimClientMessageId(Long userId, String clientMsgId) {
        if (idempotencyService == null || redisStateProperties == null || clientMsgId == null || clientMsgId.isBlank() || userId == null) {
            return true;
        }
        return idempotencyService.claimClientMessage(userId, clientMsgId,
                Duration.ofSeconds(redisStateProperties.getClientMsgIdTtlSeconds()));
    }

    private void releaseClientMessageId(Long userId, String clientMsgId) {
        if (idempotencyService == null || clientMsgId == null || clientMsgId.isBlank() || userId == null) {
            return;
        }
        idempotencyService.releaseClientMessage(userId, clientMsgId);
    }
}
