# IM (Instant Messaging) 服务端

一个基于 `Spring Boot + Netty + WebSocket + MySQL + MyBatis` 的自建 IM 服务端项目。

当前仓库目标：
- 可运行的单聊主链路（鉴权、长连接、消息落库、在线推送、ACK、离线拉取）

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
│       └── init_message_schema.sql # 消息表
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
- 幂等与状态推进
- 通过 `(from_user_id, client_msg_id)` 唯一约束支持客户端重试幂等。
- 支持 `DELIVERED/ACKED` 状态推进和通知。
- 重连与离线同步
- 握手成功后自动发送 `SYNC_START/SYNC_BATCH/SYNC_END`。
- 支持 `PULL_OFFLINE` 按游标增量拉取历史消息。
- 心跳与连接清理
- 支持 Ping/Pong 与空闲检测，断连时清理映射。

## 3. 单聊流程闭环（当前实现）

### 3.1 在线发送场景

1. A 登录获取 JWT。
2. A 通过 WebSocket 建连并通过握手鉴权。
3. A 发送 `CHAT`（包含 `targetUserId/content/clientMsgId`）。
4. 服务端先落库（生成 `serverMsgId`，幂等判断）。
5. 发布 `MessagePersistedEvent`，异步推送到 B 的在线连接。
6. A 收到 `DELIVER_ACK`（确认服务端已接收并处理）。
7. B 收到 `CHAT_DELIVER` 后可上报 `DELIVER_ACK_REPORT/READ_ACK_REPORT`。
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

`DELIVER_ACK_REPORT / READ_ACK_REPORT`
```json
{
  "type": "READ_ACK_REPORT",
  "serverMsgId": "d0f7..."
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

3. 准备可登录用户：
- `user_core.password_hash` 需要是 BCrypt 哈希。
- 项目使用 `BCryptPasswordEncoder` 校验密码。

## 5.3 调整配置

编辑 `im-chat-server/src/main/resources/application.yml`：
- `spring.datasource.url/username/password`
- `im.auth.jwt.secret`（必须替换）
- `im.netty.port`（默认 `8080`）

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

说明：父工程默认 `skipTests=true`，单模块执行测试时建议显式指定命令。

## 7. 演进路线

推荐按以下顺序推进：

1. 先稳定单机单聊闭环（协议、幂等、ACK、离线补拉）。
2. 再做群聊、文件消息、敏感词等功能扩展。
3. 最后做分布式路由（Redis 在线状态 + MQ 解耦推送）。
