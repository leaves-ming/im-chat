package com.ming.im.apicontract.message;

/**
 * 单聊撤回返回。
 */
public class RecallSingleMessageResponse {

    private MessageDTO message;

    public MessageDTO getMessage() {
        return message;
    }

    public void setMessage(MessageDTO message) {
        this.message = message;
    }
}
