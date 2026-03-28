# IM (Instant Messaging) 服务端

一个基于 `Spring Boot + Netty + WebSocket + MySQL + MyBatis` 的自建 IM 服务端项目。

当前仓库目标：
- 可运行的单聊主链路（鉴权、长连接、消息落库、在线推送、ACK、离线拉取）
- 联系人能力与单聊联系人门禁

## 1. 项目结构

```text
im/
├── im-chat-server/                 # 主服务（Netty + WebSocket + 业务处理）
├── im-tools/
│   ├── im-common-starter/          # 预留通用模块
│   └── im-frequency-control/       # 预留频控模块
├── docs/
│   ├── im_roadmap.md               # 路线图
│   └── sql/
│       ├── init_user_schema.sql    # 用户相关表
│       ├── init_message_schema.sql # 消息表
│       └── init_contact_schema.sql # 联系人表
└── pom.xml                         # 多模块父工程
```

## 2. 当前能力（已实现）

`im-chat-server` 已实现以下核心能力：

- 登录鉴权
- `POST /api/auth/login` 登录，校验用户名密码并签发 JWT。
- WebSocket 握手鉴权
- 支持从 `Authorization: Bearer <token>`、query `token`、`Sec-WebSocket-Protocol` 提取 token。
- 在线连接管理
- 基于 `ChannelUserManager` 维护 `userId <-> Channel` 双向映射，支持一人多端。
- 单聊消息主链路
- 客户端 `CHAT` -> 服务端校验并落库 -> 发布事件 -> 异步推送给目标在线端。
- 联系人能力
- 支持 `CONTACT_ADD / CONTACT_REMOVE / CONTACT_LIST`，维护单向联系人关系，分页返回 ACTIVE 联系人。
- 幂等与状态推进
- 通过 `(from_user_id, client_msg_id)` 唯一约束支持客户端重试幂等。
- 支持 `SENT -> DELIVERED -> ACKED` 状态推进和通知。
- 重连与离线同步
- 握手成功后自动发送 `SYNC_START/SYNC_BATCH/SYNC_END`。
- 支持 `PULL_OFFLINE` 按游标增量拉取历史消息。
- 心跳与连接清理
- 支持 Ping/Pong 与空闲检测，断连时清理映射。
- 敏感词过滤（MVP）
- 支持本地词库 + 内存 Trie 匹配，当前仅接入文本消息 `REJECT` 模式。
- 群推送优化
- `GROUP_CHAT` 采用预聚合在线 channel、分批异步推送与失败统计，降低大群推送对 I/O 线程的冲击。
- Outbox 稳定性
- backlog 口径包含 `NEW/FAILED/PROCESSING`，支持 `PROCESSING` 超时回收。

## 3. 单聊流程闭环（当前实现）

### 3.1 在线发送场景

1. A 登录获取 JWT。
2. A 通过 WebSocket 建连并通过握手鉴权。
3. A 发送 `CHAT`（包含 `targetUserId/content/clientMsgId`）。
4. 服务端先落库（生成 `serverMsgId`，幂等判断）。
5. 发布 `MessagePersistedEvent`，异步推送到 B 的在线连接。
6. A 收到 `DELIVER_ACK`（确认服务端已接收并处理）。
7. B 收到 `CHAT_DELIVER` 后可上报 `ACK_REPORT`，把消息状态推进到 `DELIVERED` 或 `ACKED`。
8. 服务端更新消息状态，并通知 A `MSG_STATUS_NOTIFY`。

### 3.2 离线场景

- 若 B 不在线：A 仍收到 `DELIVER_ACK`，附带 `info=TARGET_OFFLINE`。
- B 下次上线：
- 握手后自动收到最近一批 `SYNC_BATCH`。
- 或主动发送 `PULL_OFFLINE` 按游标拉取。

## 4. WebSocket 协议（当前版本）

消息格式为 JSON，关键 `type` 如下。

### 4.1 客户端 -> 服务端

`CHAT`
```json
{
  "type": "CHAT",
  "targetUserId": 2,
  "content": "hello",
  "clientMsgId": "c-10001"
}
```

`ACK_REPORT`
```json
{
  "type": "ACK_REPORT",
  "serverMsgId": "d0f7...",
  "status": "ACKED"
}
```

`PULL_OFFLINE`
```json
{
  "type": "PULL_OFFLINE",
  "limit": 50,
  "cursorCreatedAt": "2026-03-25T12:00:00Z",
  "cursorId": 12345
}
```

`CONTACT_ADD`
```json
{
  "type": "CONTACT_ADD",
  "peerUserId": 2
}
```

`CONTACT_REMOVE`
```json
{
  "type": "CONTACT_REMOVE",
  "peerUserId": 2
}
```

`CONTACT_LIST`
```json
{
  "type": "CONTACT_LIST",
  "limit": 50,
  "cursorPeerUserId": 0
}
```

命中敏感词的错误响应：
```json
{
  "type": "ERROR",
  "code": "SENSITIVE_WORD_HIT",
  "msg": "message contains sensitive words"
}
```

### 4.2 服务端 -> 客户端

`CHAT_DELIVER`
```json
{
  "type": "CHAT_DELIVER",
  "fromUserId": 1,
  "content": "hello",
  "clientMsgId": "c-10001",
  "serverMsgId": "d0f7..."
}
```

`DELIVER_ACK`
```json
{
  "type": "DELIVER_ACK",
  "clientMsgId": "c-10001",
  "serverMsgId": "d0f7...",
  "status": "SENT"
}
```

`PULL_OFFLINE_RESULT`
```json
{
  "type": "PULL_OFFLINE_RESULT",
  "hasMore": false,
  "nextCursorCreatedAt": "2026-03-25T12:00:10Z",
  "nextCursorId": 23456,
  "messages": []
}
```

`CONTACT_ADD_RESULT`
```json
{
  "type": "CONTACT_ADD_RESULT",
  "peerUserId": 2,
  "success": true,
  "idempotent": false
}
```

`CONTACT_REMOVE_RESULT`
```json
{
  "type": "CONTACT_REMOVE_RESULT",
  "peerUserId": 2,
  "success": true,
  "idempotent": true
}
```

`CONTACT_LIST_RESULT`
```json
{
  "type": "CONTACT_LIST_RESULT",
  "success": true,
  "hasMore": false,
  "nextCursor": 2,
  "items": [
    {
      "peerUserId": 2,
      "relationStatus": 1,
      "createdAt": "2026-03-25T12:00:00Z",
      "updatedAt": "2026-03-25T12:00:00Z"
    }
  ]
}
```

`ACK_REPORT_RESULT`
```json
{
  "type": "ACK_REPORT_RESULT",
  "serverMsgId": "d0f7...",
  "status": "ACKED",
  "updated": 1
}
```

`MSG_STATUS_NOTIFY`
```json
{
  "type": "MSG_STATUS_NOTIFY",
  "serverMsgId": "d0f7...",
  "status": "ACKED",
  "toUserId": 2
}
```

`ERROR`
```json
{
  "type": "ERROR",
  "code": "INVALID_PARAM",
  "msg": "peerUserId must be greater than 0 and different from self"
}
```

## 4.3 联系人与单聊门禁规则

- `CONTACT_ADD` 首次添加成功返回 `CONTACT_ADD_RESULT(success=true, idempotent=false)`；重复添加返回 `idempotent=true`。
- `CONTACT_REMOVE` 首次删除成功返回 `CONTACT_REMOVE_RESULT(success=true, idempotent=false)`；重复删除或本就不存在返回 `idempotent=true`。
- `CONTACT_LIST` 仅返回 `ACTIVE` 联系人，按 `peerUserId` 游标分页，字段至少包含 `items / hasMore / nextCursor`。
- 未登录调用联系人指令统一返回 `ERROR(code=UNAUTHORIZED)`。
- `peerUserId <= 0`、自己加自己、非法 `limit`、非法 `cursorPeerUserId` 统一返回 `ERROR(code=INVALID_PARAM)`。
- 保留配置项 `im.netty.single-chat-require-active-contact`。
- 当配置为 `false` 时，`CHAT` 继续保持兼容，可直接发送。
- 当配置为 `true` 时，`CHAT` 发送前要求 `A -> B` 与 `B -> A` 两条联系人关系都为 `ACTIVE`，任一方向不满足则返回 `ERROR(code=FORBIDDEN)`，且不进入消息落库。

## 4.4 状态机与 ACK 协议

- 单聊消息状态机统一为 `SENT -> DELIVERED -> ACKED`。
- 只允许单步前进，不允许回退、重复推进或从 `SENT` 直接跳到 `ACKED`。
- 客户端 ACK 上报统一使用 `ACK_REPORT`，`status` 只允许 `DELIVERED` 或 `ACKED`。
- 服务端返回 `ACK_REPORT_RESULT`，其中 `status` 与请求语义保持一致。
- 服务端通知发送方使用 `MSG_STATUS_NOTIFY`，其中 `status` 只可能是 `DELIVERED` 或 `ACKED`。
- 不再使用 `READ`、`READ_ACK_REPORT`、`DELIVER_ACK_REPORT` 这些旧命名。

## 4.5 敏感词过滤

- 接入范围：`CHAT` 与 `GROUP_CHAT` 的文本消息。
- 过滤时机：消息入库前。
- 当前模式：`REJECT`。
- 命中后行为：
  - 返回 `ERROR(code=SENSITIVE_WORD_HIT, msg=\"message contains sensitive words\")`
  - 不落库
  - 不推送
  - 不进入离线补拉
- 实现方式：
  - 词库来源：本地文件
  - 默认位置：`im-chat-server/src/main/resources/sensitive_words.txt`
  - 匹配方式：内存 Trie / DFA 前缀树

## 5. 快速启动

## 5.1 环境要求

- JDK 21
- Maven 3.9+
- MySQL 8+
- Redis 6+（当前核心链路不是强依赖，但配置中已预留）

## 5.2 初始化数据库

1. 创建数据库（示例）：
```sql
CREATE DATABASE IF NOT EXISTS im_chat DEFAULT CHARACTER SET utf8mb4;
```

2. 执行建表脚本：
- `docs/sql/init_user_schema.sql`
- `docs/sql/init_message_schema.sql`
- `docs/sql/init_contact_schema.sql`

3. 准备可登录用户：
- `user_core.password_hash` 需要是 BCrypt 哈希。
- 项目使用 `BCryptPasswordEncoder` 校验密码。

## 5.3 调整配置

编辑 `im-chat-server/src/main/resources/application.yml`：
- `spring.datasource.url/username/password`
- `im.auth.jwt.secret`（必须替换）
- `im.netty.port`（默认 `8080`）
- `im.netty.single-chat-require-active-contact`（默认 `false`，开启后要求双方 ACTIVE 联系关系）
- `im.netty.group-push-batch-size`（默认 `200`，群推送单批 channel 数）
- `im.netty.group-push-parallelism`（默认 `4`，群推送调度线程并行度）
- `im.netty.group-push-queue-capacity`（默认 `1000`，群推送调度队列容量）
- `im.reliability.processing-timeout-ms`（默认 `30000`，PROCESSING 超时回收阈值）
- `im.sensitive.enabled`（默认 `false`，是否启用敏感词过滤）
- `im.sensitive.mode`（默认 `REJECT`，MVP 仅支持 `REJECT`）
- `im.sensitive.word-source`（默认 `classpath:sensitive_words.txt`）

## 5.4 启动服务

在仓库根目录执行：

```bash
mvn -pl im-chat-server -am spring-boot:run
```

## 5.5 调试示例

1. 登录获取 token：
```bash
curl -X POST 'http://127.0.0.1:8080/api/auth/login' \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"你的明文密码"}'
```

2. 用 WebSocket 客户端连接（例如 `wscat`）：
```bash
wscat -c 'ws://127.0.0.1:8080/ws' \
  -H 'Authorization: Bearer <TOKEN>'
```

3. 发送一条单聊消息：
```json
{"type":"CHAT","targetUserId":2,"content":"hello","clientMsgId":"c-10001"}
```

## 6. 测试

执行 `im-chat-server` 测试：

```bash
mvn -pl im-chat-server test
```

## 7. 群推送与 Outbox 口径

- 群推送采用“预聚合在线 channel -> 按 batch 切分 -> 每个 batch 独立提交到专用线程池”的单机优化模型。
- 同一 `groupId` 的多条消息按入队顺序串行 fanout，保证实时推送顺序不乱；不同群之间允许并行。
- `group-push-batch-size` 控制单批 channel 数；`group-push-parallelism` 控制 batch 级并行调度线程数；`group-push-queue-capacity` 控制待调度队列容量。
- 单个 channel 或单批失败只计失败统计并记录日志，不会中断整次群推送。
- 若群推送线程池拒绝某个 batch 任务，则快速降级：记录 rejected 指标并跳过该 batch 的实时 fanout，其余已接收 batch 继续执行；被跳过的用户依赖已落库消息和 `GROUP_PULL_OFFLINE` 兜底。
- 群推送指标包含：
  - `im_group_push_attempt_total`
  - `im_group_push_fail_total`
  - `im_group_push_reject_total`
- Outbox backlog 最终口径：
  - `im_outbox_backlog = NEW + FAILED + PROCESSING`
  - `im_outbox_processing_backlog = PROCESSING`
- Reclaim 机制：
  - relay claim 后状态进入 `PROCESSING`
  - 若超过 `im.reliability.processing-timeout-ms` 未完成，则定时任务回收为 `FAILED`
  - 若历史脏数据里 `status=PROCESSING` 但 `processing_at` 为空，则按 `updated_at` 超时回收
  - 回收时会设置 `next_retry_at=NOW()`，重新纳入 backlog

## 8. 敏感词示例

请求：
```json
{"type":"CHAT","targetUserId":2,"content":"this contains badword","clientMsgId":"c-20001"}
```

响应：
```json
{"type":"ERROR","code":"SENSITIVE_WORD_HIT","msg":"message contains sensitive words"}
```

## 9. 演进路线

推荐按以下顺序推进：

1. 先稳定单机单聊闭环（协议、幂等、ACK、离线补拉）。
2. 再做群聊、文件消息、敏感词等功能扩展。
3. 最后做分布式路由（Redis 在线状态 + MQ 解耦推送）。
