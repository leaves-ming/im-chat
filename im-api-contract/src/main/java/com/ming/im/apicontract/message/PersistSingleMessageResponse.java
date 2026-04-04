package com.ming.im.apicontract.message;

/**
 * 单聊消息持久化返回。
 */
public class PersistSingleMessageResponse {

    private String clientMsgId;
    private String serverMsgId;
    private boolean createdNew;

    public String getClientMsgId() {
        return clientMsgId;
    }

    public void setClientMsgId(String clientMsgId) {
        this.clientMsgId = clientMsgId;
    }

    public String getServerMsgId() {
        return serverMsgId;
    }

    public void setServerMsgId(String serverMsgId) {
        this.serverMsgId = serverMsgId;
    }

    public boolean isCreatedNew() {
        return createdNew;
    }

    public void setCreatedNew(boolean createdNew) {
        this.createdNew = createdNew;
    }
}
