package com.ming.imchatserver.mapper;

import com.ming.imchatserver.dao.MessageDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 消息表（im_message）访问接口。
 * <p>
 * 提供消息写入、状态更新、幂等回查和离线消息查询能力。
 */
@Mapper
public interface MessageMapper {

    /**
     * 插入一条消息记录。
     *
     * @param msg 消息实体
     * @return 受影响行数
     */
    int insert(MessageDO msg);

    /**
     * 根据服务端消息 ID 查询消息。
     *
     * @param serverMsgId 服务端消息 ID
     * @return 命中返回消息；未命中返回 null
     */
    MessageDO findByServerMsgId(@Param("serverMsgId") String serverMsgId);

    /**
     * 按接收用户查询消息列表。
     *
     * @param toUserId 接收用户 ID
     * @return 消息列表（SQL 内定义排序）
     */
    List<MessageDO> findByToUserId(@Param("toUserId") Long toUserId);

    /**
     * 根据发送用户 + 客户端消息 ID 回查消息。
     * <p>
     * 用于处理客户端重试时的幂等冲突回查。
     *
     * @param fromUserId  发送用户 ID
     * @param clientMsgId 客户端消息 ID
     * @return 命中返回消息；未命中返回 null
     */
    MessageDO findByFromUserIdAndClientMsgId(@Param("fromUserId") Long fromUserId, @Param("clientMsgId") String clientMsgId);

    /**
     * 按 serverMsgId 更新消息状态与时间戳。
     *
     * @param serverMsgId 服务端消息 ID
     * @param status      目标状态
     * @param deliveredAt 送达时间（可空）
     * @param ackedAt     已读/确认时间（可空）
     * @return 更新行数
     */
    int updateStatusByServerMsgId(@Param("serverMsgId") String serverMsgId,
                                  @Param("status") String status,
                                  @Param("deliveredAt") Date deliveredAt,
                                  @Param("ackedAt") Date ackedAt);

    /**
     * 基于游标查询离线消息（升序）。
     * <p>
     * 排序语义：created_at ASC, id ASC。
     *
     * @param toUserId        接收用户 ID
     * @param cursorCreatedAt 游标时间
     * @param cursorId        游标主键
     * @return 从游标之后开始的消息列表
     */
    List<MessageDO> findByToUserIdAfterCursor(@Param("toUserId") Long toUserId,
                                              @Param("cursorCreatedAt") Date cursorCreatedAt,
                                              @Param("cursorId") Long cursorId);

    /**
     * 查询用户最近消息（倒序）。
     *
     * @param toUserId 接收用户 ID
     * @return 最近消息列表（created_at DESC, id DESC）
     */
    List<MessageDO> findRecentByToUserId(@Param("toUserId") Long toUserId);

    /**
     * 校验用户是否可访问某个单聊 FILE 消息。
     */
    Integer existsFileParticipant(@Param("fileId") String fileId, @Param("userId") Long userId);
}
