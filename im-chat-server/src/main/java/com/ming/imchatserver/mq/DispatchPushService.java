package com.ming.imchatserver.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ming.imchatserver.message.MessageContentCodec;
import com.ming.imchatserver.netty.ChannelUserManager;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * 消息分发落地服务：把 MQ 消息推送到在线用户。
 */
@Component
public class DispatchPushService {

    private static final Logger logger = LoggerFactory.getLogger(DispatchPushService.class);

    private final ChannelUserManager channelUserManager;
    private final ObjectMapper mapper = new ObjectMapper();

    public DispatchPushService(ChannelUserManager channelUserManager) {
        this.channelUserManager = channelUserManager;
    }

    public void dispatchSingle(DispatchMessagePayload payload) throws Exception {
        Collection<Channel> targets = channelUserManager.getChannels(payload.getToUserId());
        if (targets.isEmpty()) {
            logger.info("mq dispatch target offline, serverMsgId={} toUserId={}",
                    payload.getServerMsgId(), payload.getToUserId());
            return;
        }

        ObjectNode deliver = mapper.createObjectNode();
        deliver.put("type", "CHAT_DELIVER");
        deliver.put("fromUserId", payload.getFromUserId());
        deliver.put("toUserId", payload.getToUserId());
        deliver.put("clientMsgId", payload.getClientMsgId());
        deliver.put("serverMsgId", payload.getServerMsgId());
        String msgType = MessageContentCodec.normalizeMsgType(payload.getMsgType());
        deliver.put("msgType", msgType);
        MessageContentCodec.writeProtocolContent(deliver, "content", msgType, payload.getContent());

        String text = mapper.writeValueAsString(deliver);
        for (Channel channel : targets) {
            channel.writeAndFlush(new TextWebSocketFrame(text));
        }

        logger.info("mq dispatch delivered serverMsgId={} toUserId={} channels={}",
                payload.getServerMsgId(), payload.getToUserId(), targets.size());
    }
}
