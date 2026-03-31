# Day17 消息撤回

## 范围

- 支持单聊撤回和群聊撤回
- 撤回后不删除消息，只更新为撤回态
- 在线用户收到撤回通知
- 离线拉取和握手同步返回撤回后的消息视图

## 数据变更

### `im_message`

- 新增 `retracted_at`
- 新增 `retracted_by`
- `status` 支持 `RETRACTED`

### `im_group_message`

- 新增 `retracted_at`
- 新增 `retracted_by`
- `status` 约定：
  - `1` = `SENT`
  - `2` = `RETRACTED`

## WebSocket

### Client -> Server

单聊：

```json
{"type":"MSG_RECALL","serverMsgId":"xxx"}
```

群聊：

```json
{"type":"GROUP_MSG_RECALL","serverMsgId":"xxx"}
```

### Server -> Client

- `MSG_RECALL_RESULT`
- `GROUP_MSG_RECALL_RESULT`
- `MSG_RECALL_NOTIFY`
- `GROUP_MSG_RECALL_NOTIFY`

撤回消息在协议层统一表现为：

- `status = RETRACTED`
- `content = null`
- 追加 `retractedAt`
- 追加 `retractedBy`

## 规则

- 单聊只有发送者可以撤回
- 群聊发送者可以撤回自己的消息
- 群聊高权限可撤回低权限消息：`OWNER > ADMIN > MEMBER`
- 群聊操作者必须仍为 active member
- 撤回窗口默认 120 秒，对应配置 `im.netty.message-recall-window-seconds`
- 已撤回消息不可再次撤回
