package com.ming.immessageservice.infrastructure.mapper;

import com.ming.immessageservice.infrastructure.dao.MessageDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 单聊消息 Mapper。
 */
@Mapper
public interface MessageMapper {

    int insert(MessageDO msg);

    MessageDO findByServerMsgId(@Param("serverMsgId") String serverMsgId);

    MessageDO findByFromUserIdAndClientMsgId(@Param("fromUserId") Long fromUserId,
                                             @Param("clientMsgId") String clientMsgId);

    int updateStatusByServerMsgId(@Param("serverMsgId") String serverMsgId,
                                  @Param("status") String status,
                                  @Param("deliveredAt") Date deliveredAt,
                                  @Param("ackedAt") Date ackedAt);

    int updateRetractionByServerMsgId(@Param("serverMsgId") String serverMsgId,
                                      @Param("status") String status,
                                      @Param("retractedAt") Date retractedAt,
                                      @Param("retractedBy") Long retractedBy);

    List<MessageDO> findByToUserIdAfterCursor(@Param("toUserId") Long toUserId,
                                              @Param("cursorCreatedAt") Date cursorCreatedAt,
                                              @Param("cursorId") Long cursorId);

    List<MessageDO> findRecentByToUserId(@Param("toUserId") Long toUserId);
}
