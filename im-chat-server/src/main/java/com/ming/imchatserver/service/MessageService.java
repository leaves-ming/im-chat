package com.ming.imchatserver.service;

import com.ming.imchatserver.dao.MessageDO;

import java.util.Date;
import java.util.List;

/**
 * 消息领域服务接口。
 * <p>
 * 负责消息持久化、状态推进和离线消息分页拉取，是 Netty 业务层与数据层的桥梁。
 */
public interface MessageService {

    /**
     * 持久化一条消息，并返回持久化结果。
     *
     * @param msg 消息实体
     * @return 持久化结果（serverMsgId、是否新写入）
     */
    PersistResult persistMessage(MessageDO msg);

    /**
     * 按 serverMsgId 推进消息状态（SENT -> DELIVERED -> ACKED）。
     *
     * @param serverMsgId 服务端消息 ID
     * @param status      目标状态
     * @return 实际更新行数
     */
    int updateStatusByServerMsgId(String serverMsgId, String status);

    /**
     * 撤回单聊消息。
     */
    MessageDO recallMessage(Long operatorUserId, String serverMsgId, long recallWindowSeconds);

    /**
     * 根据 serverMsgId 查询消息。
     *
     * @param serverMsgId 服务端消息 ID
     * @return 命中返回消息，否则返回 null
     */
    MessageDO findByServerMsgId(String serverMsgId);

    /**
     * 发送方状态通知进入分布式 dispatch 链路。
     */
    boolean enqueueStatusNotify(MessageDO message, String status);

    /**
     * 查询当前用户的单聊同步游标。
     * 当前按 userId + deviceId 维度存储，多设备独立 checkpoint。
     */
    SyncCursor getSyncCursor(Long toUserId, String deviceId);

    /**
     * 推进单聊同步游标。
     * 当前按 userId + deviceId 维度存储，多设备独立 checkpoint。
     */
    void advanceSyncCursor(Long toUserId, String deviceId, SyncCursor syncCursor);

    /**
     * 基于统一同步游标拉取单聊离线消息。
     */
    CursorPageResult pullOffline(Long toUserId, String deviceId, SyncCursor syncCursor, int limit);

    /**
     * 优先基于服务端 checkpoint 增量拉取；无 checkpoint 时退化为 recent。
     */
    CursorPageResult pullOfflineFromCheckpoint(Long toUserId, String deviceId, int limit);

    /**
     * 基于游标拉取离线消息（升序）。
     *
     * @param toUserId        接收用户 ID
     * @param cursorCreatedAt 游标时间
     * @param cursorId        游标主键
     * @param limit           本次返回条数上限
     * @return 游标分页结果
     */
    CursorPageResult pullOfflineByCursor(Long toUserId, Date cursorCreatedAt, Long cursorId, int limit);

    /**
     * 拉取最近 N 条消息（内部可按需重排为升序返回）。
     *
     * @param toUserId 接收用户 ID
     * @param limit    本次返回条数上限
     * @return 游标分页结果
     */
    CursorPageResult pullRecent(Long toUserId, int limit);

    /** 统一同步游标。 */
    class SyncCursor {
        private final Date cursorCreatedAt;
        private final Long cursorId;

        public SyncCursor(Date cursorCreatedAt, Long cursorId) {
            this.cursorCreatedAt = cursorCreatedAt;
            this.cursorId = cursorId;
        }

        public Date getCursorCreatedAt() {
            return cursorCreatedAt;
        }

        public Long getCursorId() {
            return cursorId;
        }

        public boolean isComplete() {
            return cursorCreatedAt != null && cursorId != null;
        }
    }

    /** 持久化结果模型。 */
    class PersistResult {
        private final String serverMsgId;
        private final boolean createdNew;

        public PersistResult(String serverMsgId, boolean createdNew) {
            this.serverMsgId = serverMsgId;
            this.createdNew = createdNew;
        }

        public String getServerMsgId() {
            return serverMsgId;
        }

        public boolean isCreatedNew() {
            return createdNew;
        }
    }

    /** 游标分页结果模型。 */
    class CursorPageResult {
        private final List<MessageDO> messages;
        private final boolean hasMore;
        private final Date nextCursorCreatedAt;
        private final Long nextCursorId;

        public CursorPageResult(List<MessageDO> messages, boolean hasMore, Date nextCursorCreatedAt, Long nextCursorId) {
            this.messages = messages;
            this.hasMore = hasMore;
            this.nextCursorCreatedAt = nextCursorCreatedAt;
            this.nextCursorId = nextCursorId;
        }

        public List<MessageDO> getMessages() {
            return messages;
        }

        public boolean isHasMore() {
            return hasMore;
        }

        public Date getNextCursorCreatedAt() {
            return nextCursorCreatedAt;
        }

        public Long getNextCursorId() {
            return nextCursorId;
        }
    }
}
