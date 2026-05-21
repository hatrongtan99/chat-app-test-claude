# FLOW-MSG.md — Luồng gửi/nhận message qua Redis + WebSocket

Tài liệu mô tả chi tiết cách Redis Pub/Sub và WebSocket/STOMP được cấu hình, và toàn bộ
hành trình của một message từ client gửi đi đến khi tất cả client khác nhận được — kể cả
khi hệ thống chạy nhiều instance.

---

## 1. Tổng quan kiến trúc

```
                 ┌─────────────────────── Instance A ───────────────────────┐
 Client 1 ──WS──►│ ChatMessageController → MessageService → MySQL           │
   (gửi)         │                                  │                       │
                 │                                  └─publish─► Redis chan   │
                 └──────────────────────────────────────────────│──────────┘
                                                                 │ chat:messages
                 ┌─────────────────────── Instance B ────────────│──────────┐
 Client 2 ◄─WS───│ RedisMessageSubscriber ◄──subscribe──────────┘           │
   (nhận)        │   └─► messagingTemplate → /topic/rooms/{roomId}          │
                 └───────────────────────────────────────────────────────────┘
```

Nguyên tắc cốt lõi: **một message KHÔNG bao giờ được đẩy thẳng từ controller xuống STOMP
topic**. Nó luôn đi vòng qua Redis. Nhờ vậy mọi instance đang chạy đều nhận được bản sao
và đẩy xuống các client STOMP đang kết nối với chính instance đó → fanout đa instance.

---

## 2. Cấu hình Redis (`RedisConfig.java`)

### 2.1. Các bean được khai báo

| Bean | Kiểu | Mục đích |
|------|------|----------|
| `pubSubRedisTemplate` | `RedisTemplate<String,Object>` | Publish object `WebSocketMessage` (JSON) lên channel |
| `chatTopic` | `ChannelTopic("chat:messages")` | Định danh channel pub/sub |
| `messageListenerAdapter` | `MessageListenerAdapter` | Adapter gọi `RedisMessageSubscriber#onMessage` |
| `redisMessageListenerContainer` | `RedisMessageListenerContainer` | Container lắng nghe channel và route tới subscriber |

> `StringRedisTemplate` **không khai báo lại** — Spring Boot auto-config sẵn. `AuthService`,
> `PresenceService`, `RateLimitFilter` inject trực tiếp bean này.

### 2.2. Serializer

`pubSubRedisTemplate` dùng:
- Key serializer: `StringRedisSerializer` → key channel là chuỗi thuần.
- Value serializer: `GenericJackson2JsonRedisSerializer(objectMapper)` → object Java được
  serialize thành JSON, có lưu kèm type info để deserialize chính xác phía subscriber.

`ObjectMapper` được lấy từ `JacksonConfig` (đã đăng ký `JavaTimeModule`, tắt
`WRITE_DATES_AS_TIMESTAMPS`) nên field `LocalDateTime timestamp` trong `WebSocketMessage`
được serialize đúng dạng ISO chứ không lỗi.

### 2.3. Channel

Toàn hệ thống dùng **một channel duy nhất**: `chat:messages`
(hằng số `RedisConfig.CHAT_TOPIC`, lặp lại ở `MessageService.CHAT_CHANNEL` và
`ChatMessageController.CHAT_CHANNEL`). RoomId KHÔNG nằm trong tên channel — nó nằm trong
payload (`WebSocketMessage.roomId`). Subscriber sẽ dựa vào field này để dựng STOMP
destination `/topic/rooms/{roomId}`.

---

## 3. Cấu hình WebSocket / STOMP (`WebSocketConfig.java`)

### 3.1. Endpoint

```java
registry.addEndpoint("/ws/chat")
        .setAllowedOriginPatterns("*")   // CORS ở tầng WebSocket, KHÔNG qua SecurityConfig
        .withSockJS();                   // có fallback SockJS
```

### 3.2. Message broker

```java
registry.enableSimpleBroker("/topic", "/queue"); // broker in-memory
registry.setApplicationDestinationPrefixes("/app"); // frame client gửi vào @MessageMapping
registry.setUserDestinationPrefix("/user");
```

- Client **gửi** tới `/app/...` → route vào method `@MessageMapping` trong controller.
- Client **nhận** bằng cách subscribe `/topic/rooms/{roomId}` hoặc `/topic/presence`.

### 3.3. Xác thực JWT qua ChannelInterceptor

HTTP `JwtAuthFilter` KHÔNG bao phủ frame WebSocket, nên auth được làm trong
`configureClientInboundChannel`:

1. **Frame `CONNECT`**: đọc header `Authorization: Bearer <token>` → `jwtService.extractUsername`
   → `userDetailsService.loadUserByUsername` → `isTokenValid` → set
   `UsernamePasswordAuthenticationToken` làm `Principal` của session. Token chỉ kiểm 1 lần
   lúc CONNECT, các frame sau tái dùng `Principal` đã thiết lập.
2. **Frame khác CONNECT** (SEND/SUBSCRIBE…): nếu có user → gọi
   `presenceService.refresh(userId)` để gia hạn TTL key online (heartbeat ngầm, xử lý
   ngắt kết nối đột ngột).

---

## 4. FLOW GỬI MESSAGE (client → server → mọi client)

Ví dụ: Client 1 gõ "hello" trong room 42.

### Bước 1 — Client gửi STOMP frame (`app.js`)

```js
stompClient.send('/app/chat.send', {},
    JSON.stringify({ roomId: 42, content: 'hello', type: 'TEXT' }));
```

Frame đi qua inbound channel → ChannelInterceptor `preSend`:
- Không phải CONNECT → refresh presence TTL của người gửi.

### Bước 2 — `ChatMessageController.handleChatMessage()`

```java
@MessageMapping("/chat.send")
public void handleChatMessage(@Payload SendMessageRequest request, Principal principal) {
    if (principal == null) return;                       // không JWT hợp lệ → bỏ lặng
    User user = (User) ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
    messageService.sendMessage(request.getRoomId(), user.getId(), request);
}
```

- `principal == null` → frame bị drop âm thầm (client connect không có JWT hợp lệ).
- Lấy `userId` từ Principal (không tin `senderId` do client gửi → chống giả mạo).

### Bước 3 — `MessageService.sendMessage()` (có `@Transactional`)

1. Kiểm tra người gửi là thành viên room: `memberRepository.existsByRoomIdAndUserId`
   → nếu không → `AppException(ROOM_NOT_MEMBER)`.
2. Load `ChatRoom` và `User` (không có → `ROOM_NOT_FOUND` / `USER_NOT_FOUND`).
3. Build `Message` (mặc định `MessageType.TEXT` nếu null) → `messageRepository.save()`
   → **persist xuống MySQL**.
4. Map sang `MessageResponse`.
5. Gọi `publish(WebSocketMessage)` với `eventType = "CHAT_MESSAGE"`.

### Bước 4 — Publish lên Redis

```java
private void publish(WebSocketMessage wsMessage) {
    pubSubRedisTemplate.convertAndSend("chat:messages", wsMessage);
}
```

`WebSocketMessage` được serialize JSON và `PUBLISH` lên channel `chat:messages`.

> **Lưu ý transaction:** lệnh publish nằm NGOÀI transaction MySQL (thực tế gọi cuối method
> nhưng commit DB và publish là hai hệ thống tách biệt). Nếu Redis lỗi sau khi DB đã commit
> → message đã lưu nhưng KHÔNG fanout realtime; client phục hồi bằng REST polling
> (`GET /api/rooms/{id}/messages`).

### Bước 5 — `RedisMessageSubscriber.onMessage()` (chạy trên MỌI instance)

`RedisMessageListenerContainer` của mỗi instance đang subscribe `chat:messages`, nên
mọi instance đều nhận được bản sao:

```java
WebSocketMessage wsMsg = objectMapper.readValue(message.getBody(), WebSocketMessage.class);
messagingTemplate.convertAndSend("/topic/rooms/" + wsMsg.getRoomId(), wsMsg);
```

- Deserialize JSON → `WebSocketMessage`.
- Đẩy vào STOMP destination `/topic/rooms/42` trên broker in-memory của instance đó.
- Lỗi được log và nuốt để listener không chết, vẫn xử lý message kế tiếp.

### Bước 6 — Broker đẩy tới client subscribe

Mọi client đã `subscribe('/topic/rooms/42')` (kể cả Client 1 — người gửi) đều nhận frame.

### Bước 7 — Client xử lý (`app.js handleIncomingMessage`)

```js
case 'CHAT_MESSAGE':
case 'SYSTEM_MESSAGE':
    if (wsMsg.roomId === currentRoomId) { appendMessage(wsMsg.message); scrollToBottom(); }
    break;
```

Chỉ render nếu message thuộc room đang mở.

### Sơ đồ tuần tự

```
Client1   Controller   MessageService   MySQL   Redis(chat:messages)   Subscriber(*)   Broker   Client2
  │  send /app/chat.send  │                │            │                    │            │         │
  ├──────────────────────►│                │            │                    │            │         │
  │                       ├─sendMessage───►│            │                    │            │         │
  │                       │                ├─save──────►│                    │            │         │
  │                       │                ├─publish───────────────────────►│            │         │
  │                       │                │            │   (mọi instance)   ├─onMessage─►│         │
  │                       │                │            │                    │  convertAndSend       │
  │                       │                │            │                    │            ├────────►│ (Client2)
  │◄───────────────────────────────────────────────────────────────────────────────────┤────────┤ (cả Client1)
```

---

## 5. FLOW TYPING (không lưu DB)

Typing đi tắt — KHÔNG qua `MessageService`, KHÔNG ghi MySQL:

```java
@MessageMapping("/chat.typing")
public void handleTyping(@Payload TypingEvent event, Principal principal) {
    ...
    WebSocketMessage wsMsg = WebSocketMessage.builder()
        .eventType("TYPING").roomId(event.getRoomId())
        .userId(user.getId()).username(user.getUsername())
        .extra(event.isTyping()).build();
    pubSubRedisTemplate.convertAndSend("chat:messages", wsMsg); // publish thẳng
}
```

Vẫn đi qua Redis (đa instance), subscriber đẩy lên `/topic/rooms/{roomId}`. Client hiển thị
"X is typing…" và tự ẩn sau 3s; bỏ qua event của chính mình (`wsMsg.userId !== currentUser.id`).

---

## 6. FLOW PRESENCE (online/offline)

`WebSocketEventListener` lắng nghe sự kiện vòng đời session STOMP:

- **`SessionConnectedEvent`** → `presenceService.setOnline(userId)` (set key
  `user:online:{userId}` TTL 60s trong Redis) → broadcast `PRESENCE` (extra=true) tới
  `/topic/presence`.
- **`SessionDisconnectEvent`** → `presenceService.setOffline(userId)` (xóa key) →
  broadcast `PRESENCE` (extra=false).

> Khác với chat/typing, broadcast presence gọi `messagingTemplate.convertAndSend` **trực
> tiếp** (không qua Redis). Trong môi trường đa instance, presence chỉ tới client của
> instance đó — chấp nhận được vì đã có TTL key Redis làm nguồn sự thật cho
> `GET /api/users/{id}/online`.

**Heartbeat chống ngắt đột ngột:** mỗi frame inbound ≠ CONNECT → `presenceService.refresh()`
gia hạn TTL 60s. Nếu client chết không gửi DISCONNECT, sau 60s key tự hết hạn → coi offline.
TTL (60s) phải lớn hơn chu kỳ heartbeat STOMP để tránh báo offline nhầm.

---

## 7. Vòng đời kết nối phía client (`app.js`)

```js
function connectWebSocket() {
    const socket = new SockJS('/ws/chat');
    stompClient = Stomp.over(socket);
    stompClient.connect(
        { Authorization: 'Bearer ' + token },   // JWT trong CONNECT header
        () => { if (currentRoomId) subscribeToRoom(currentRoomId); },
        () => { setTimeout(connectWebSocket, 5000); }  // tự reconnect sau 5s
    );
}

function subscribeToRoom(roomId) {
    if (currentSubscription) currentSubscription.unsubscribe(); // huỷ sub room cũ
    currentSubscription = stompClient.subscribe('/topic/rooms/' + roomId,
        frame => handleIncomingMessage(JSON.parse(frame.body)));
}
```

- Mỗi lần đổi room → unsubscribe topic cũ, subscribe topic mới (chỉ giữ 1 subscription).
- Mất kết nối → auto retry mỗi 5s.
- Lịch sử message load qua REST (`GET /api/rooms/{id}/messages`), realtime mới qua WS.

---

## 8. Tóm tắt các điểm mấu chốt

| Vấn đề | Giải pháp trong code |
|--------|----------------------|
| Fanout đa instance | Mọi instance subscribe chung channel Redis `chat:messages` |
| Phân biệt room | `roomId` nằm trong payload, không trong tên channel; subscriber map sang `/topic/rooms/{id}` |
| Auth WebSocket | JWT trong frame CONNECT, validate ở `ChannelInterceptor`, set `Principal` |
| Chống giả mạo người gửi | Lấy `userId` từ `Principal`, bỏ qua `senderId` client gửi |
| Typing nhẹ | Đi tắt qua Redis, không ghi DB |
| Presence | TTL key Redis + heartbeat mỗi frame; broadcast presence cục bộ instance |
| Redis lỗi sau commit DB | Message vẫn lưu MySQL; client phục hồi qua REST polling |
| Serialize `LocalDateTime` | `GenericJackson2JsonRedisSerializer` + `ObjectMapper` từ `JacksonConfig` |
```

