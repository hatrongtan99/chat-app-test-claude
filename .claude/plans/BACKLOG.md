# Backlog — Chat App

Context nhanh cho session sau: đây là các tính năng chưa implement trong V1, xác nhận bởi user 2026-05-19.

## Quyết định đã xác nhận (V1)

| Câu hỏi | Quyết định |
|---------|-----------|
| Base package | `com.chatapp` |
| Private room | Invite-only: ADMIN gọi `POST /api/rooms/{id}/invite` body `{userId}` → add MEMBER trực tiếp |
| Message order (API) | Newest first (`ORDER BY created_at DESC`), FE tự reverse khi render |
| Rate limiting | Pure Redis code (INCR + EXPIRE), áp cho `/api/auth/login` + `/api/auth/register`, 5 req/phút/IP |
| Build tool | Maven (`pom.xml`) |

---

## Deferred Features (sau V1)

### 1. Read Receipts
Đánh dấu user đã đọc tin nhắn đến message nào trong room.

**Schema cần thêm:**
```sql
CREATE TABLE message_reads (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id BIGINT NOT NULL,
    user_id    BIGINT NOT NULL,
    read_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_msg_user (message_id, user_id),
    FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id)    REFERENCES users(id)    ON DELETE CASCADE
);
```

**API cần thêm:**
- `POST /api/rooms/{roomId}/messages/{messageId}/read` — đánh dấu đã đọc
- `GET /api/rooms/{roomId}/messages/{messageId}/reads` — danh sách ai đã đọc

**WebSocket event:**
- Khi user đọc, broadcast `READ_RECEIPT` event tới `/topic/rooms/{roomId}` với `{userId, messageId, readAt}`

**Lưu ý:** tránh N+1 khi load read-count cho danh sách message — dùng subquery COUNT hoặc cache Redis `Set` per message.

---

### 2. Message Reactions (Emoji)
User react emoji (❤️, 👍, 😂...) vào bất kỳ message nào trong room.

**Schema cần thêm:**
```sql
CREATE TABLE message_reactions (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id BIGINT       NOT NULL,
    user_id    BIGINT       NOT NULL,
    emoji      VARCHAR(10)  NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_msg_user_emoji (message_id, user_id, emoji),
    FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id)    REFERENCES users(id)    ON DELETE CASCADE
);
```

**API cần thêm:**
- `POST /api/messages/{id}/reactions` body `{emoji}` — thêm/toggle reaction
- `DELETE /api/messages/{id}/reactions/{emoji}` — xoá reaction
- `GET /api/messages/{id}/reactions` — group by emoji, trả `[{emoji, count, reactedByMe}]`

**WebSocket event:**
- Broadcast `REACTION` event tới `/topic/rooms/{roomId}` với `{messageId, emoji, userId, action: "ADD"|"REMOVE"}`

**Lưu ý:** giới hạn số emoji unique per message (ví dụ 20) để tránh spam. Index trên `(message_id, emoji)` cho aggregate query.

---

## Có thể cân nhắc thêm (không được hỏi, ghi phòng sau)

- **File/image upload:** tích hợp S3/MinIO; `MessageType.IMAGE` đã có trong schema, chỉ cần thêm upload endpoint + presigned URL
- **Message edit:** thêm `edited_at` vào bảng `messages`, audit history (table `message_edits`)
- **Notification:** push notification khi offline (FCM/APNs) — cần lưu device token
- **Search messages:** full-text search trên MySQL (`FULLTEXT INDEX` + `MATCH AGAINST`) hoặc tích hợp Elasticsearch sau
- **Cursor-based pagination:** thay offset-pagination bằng `created_at < cursor` cho infinite scroll đúng chuẩn