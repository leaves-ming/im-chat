package com.ming.imchatserver.event;

import io.netty.channel.Channel;
import org.springframework.context.ApplicationEvent;

/**
 * 消息持久化完成事件。
 * <p>
 * 在消息成功落库后发布，用于异步触发推送逻辑，实现“存储/推送”解耦。
 */
public class MessagePersistedEvent extends ApplicationEvent {

    private final Channel senderChannel;
    private final Long fromUserId;
    private final Long toUserId;
    private final String clientMsgId;
    private final String serverMsgId;
    private final String content;

    /**
     * @param source        事件源
     * @param senderChannel 发送端连接
     * @param fromUserId    发送用户 ID
     * @param toUserId      接收用户 ID
     * @param clientMsgId   客户端消息 ID
     * @param serverMsgId   服务端消息 ID
     * @param content       消息内容
     */
    public MessagePersistedEvent(Object source,
                                 Channel senderChannel,
                                 Long fromUserId,
                                 Long toUserId,
                                 String clientMsgId,
                                 String serverMsgId,
                                 String content) {
        super(source);
        this.senderChannel = senderChannel;
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.clientMsgId = clientMsgId;
        this.serverMsgId = serverMsgId;
        this.content = content;
    }

    public Channel getSenderChannel() {
        return senderChannel;
    }

    public Long getFromUserId() {
        return fromUserId;
    }

    public Long getToUserId() {
        return toUserId;
    }

    public String getClientMsgId() {
        return clientMsgId;
    }

    public String getServerMsgId() {
        return serverMsgId;
    }

    public String getContent() {
        return content;
    }
}
