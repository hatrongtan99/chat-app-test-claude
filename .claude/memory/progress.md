# Tiến độ dự án

## Đã hoàn thành

### Authentication & Security
- [x] JWT register / login / refresh token rotation / logout
- [x] Refresh token lưu Redis với TTL, rotate mỗi lần dùng
- [x] RateLimitFilter (Bucket4j + Redis): 5 req/min/IP trên login/register
- [x] STOMP JWT auth qua ChannelInterceptor (WebSocketConfig)
- [x] Disconnect client ngay khi JWT sai/thiếu (throw MessageDeliveryException)

### Chat Rooms
- [x] CRUD phòng chat (PUBLIC / PRIVATE)
- [x] Join / leave room
- [x] Private room: chỉ ADMIN mới invite được (POST /api/rooms/{id}/invite)
- [x] Membership qua join table `chat_room_members`

### Realtime Messaging (WebSocket/STOMP + Redis Pub/Sub)
- [x] Room messages: /app/chat.send → MySQL → Redis `chat:messages` → /topic/rooms/{roomId}
- [x] Typing events qua cùng topic
- [x] Presence (online/offline) qua /topic/presence
- [x] Multi-instance safe (Redis fanout)

### Direct Messages
- [x] REST: POST /api/dm/{recipientId}, GET /api/dm/{partnerId}/messages
- [x] STOMP: /app/dm.send → MySQL → Redis `chat:dm` → /user/queue/direct-messages
- [x] Echo về cả sender (multi-tab support)

### Infrastructure
- [x] MySQL với Flyway migrations (Hibernate validate-only)
- [x] Redis (Pub/Sub + cache + rate-limit)
- [x] Docker Compose: mysql + redis + app
- [x] Frontend demo static (index.html + app.js, SockJS/STOMP từ CDN)

## Đang làm / Chưa làm

- [ ] (trống — chờ yêu cầu mới)

## 2026-05-20 — SQL Tracing AOP

### Đã thêm
- [x] `spring-boot-starter-aop` vào pom.xml
- [x] `SqlTraceAspect` — AOP `@Around` intercept toàn bộ public method trong package `service` và `repository`; đẩy tên caller (ClassName.methodName) vào MDC key `sqlCaller`. Giữ nguyên outermost caller khi service gọi sâu xuống repo.
- [x] `SqlTracingStatementInspector` — Hibernate `StatementInspector` đọc MDC và log `[SQL-TRACE] Caller → SQL` ở DEBUG level. Không thay đổi SQL.
- [x] Đăng ký inspector trong `application.yml` qua `hibernate.session_factory.statement_inspector`
- [x] Flag bật/tắt `app.sql-trace.enabled: true` — dùng `@ConditionalOnProperty`, tắt là zero overhead

### Log output mẫu
```
[SQL-TRACE] MessageService.findPagedMessages  →  SELECT m.id, m.content, ... FROM messages m WHERE m.room_id=? AND m.deleted_at IS NULL
```