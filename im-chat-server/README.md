# im-chat-server

## Recall Protocol

- Single recall command:
  - `{"type":"MSG_RECALL","serverMsgId":"xxx"}`
- Group recall command:
  - `{"type":"GROUP_MSG_RECALL","serverMsgId":"xxx"}`

- Single recall result:
  - `MSG_RECALL_RESULT`
- Group recall result:
  - `GROUP_MSG_RECALL_RESULT`

- Single recall notify:
  - `MSG_RECALL_NOTIFY`
- Group recall notify:
  - `GROUP_MSG_RECALL_NOTIFY`

Retracted messages are not deleted. They are returned with:

- `status = RETRACTED`
- `content = null`
- `retractedAt`
- `retractedBy`
