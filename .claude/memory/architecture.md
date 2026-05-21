# Kiến trúc dự án

## Stack
- Java 21 + Spring Boot 3.5.x
- WebSocket/STOMP (SockJS fallback)
- Redis Pub/Sub (fanout multi-instance) + Redis cache/rate-limit
- MySQL 8.0 + Flyway migrations
- JWT stateless auth (jjwt 0.13.0)
- MapStruct + Lombok
- Docker Compose

## Package Layout
```
com.chatapp
├── common/          # ErrorCode, AppException, ApiResponse<T>, PageResponse<T>, GlobalExceptionHandler
├── config/          # SecurityConfig, RedisConfig, WebSocketConfig, JacksonConfig
├── security/        # JwtProperties, JwtService, JwtAuthFilter, RateLimitFilter, CustomUserDetailsService
├── domain/
│   ├── user/        # User entity, UserRepository, UserService, UserController
│   ├── auth/        # AuthService (register/login/refresh/logout), AuthController
│   ├── chat/        # ChatRoom entity, ChatRoomService, ChatRoomController
│   ├── room/        # ChatRoomMember entity + repository
│   ├── message/     # Message entity, MessageService, MessageController, WebSocketMessage DTO
│   └── directmessage/ # DirectMessage entity, service, controller, DTOs, mapper
├── websocket/       # ChatMessageController, DirectMessageWsController, WebSocketEventListener
├── infrastructure/  # RedisMessageSubscriber, RedisDmSubscriber, PresenceService
└── application/     # CreateRoomWithMemberUseCase
```

## Request Flow

### REST
`JwtAuthFilter` → `SecurityConfig` filter chain → Controller → Service → Repository

### WebSocket room message
1. Client → STOMP `/app/chat.send`
2. `ChatMessageController` → `MessageService.sendMessage()`
3. Save MySQL → publish JSON to Redis `chat:messages`
4. `RedisMessageSubscriber.onMessage()` → `messagingTemplate.convertAndSend("/topic/rooms/{roomId}")`
5. Tất cả subscriber nhận (works across instances)

### WebSocket direct message
1. Client → STOMP `/app/dm.send` với `{recipientId, content}`
2. `DirectMessageWsController` → `DirectMessageService.send()`
3. Save MySQL → publish `DirectMessageEvent` to Redis `chat:dm`
4. `RedisDmSubscriber.onMessage()` → `convertAndSendToUser()` cho cả sender + recipient
5. Nhận tại `/user/queue/direct-messages`

## Key Design Decisions

### Redis beans
- Spring Boot auto-configures `StringRedisTemplate` — KHÔNG redeclare
- `RedisConfig` chỉ định nghĩa `@Bean("pubSubRedisTemplate")` với `GenericJackson2JsonRedisSerializer`
- Hai channel: `chat:messages` (room) và `chat:dm` (DM)

### STOMP destinations
- `/topic/*` — broadcast (room messages, presence)
- `/queue/*` — broker-managed (dùng với convertAndSendToUser)
- `/user` prefix — user-scoped, Spring tự map thành `/queue/...-user{sessionId}`
- `/app` prefix — route vào @MessageMapping handlers

### STOMP JWT auth
- HTTP JwtAuthFilter KHÔNG cover WebSocket frames
- JWT validate trong `ChannelInterceptor.preSend()` trên CONNECT frame
- JWT sai/thiếu → throw `MessageDeliveryException` → Spring gửi STOMP ERROR + đóng connection

### Soft delete
- `Message.deletedAt` — filter `m.deletedAt IS NULL` trong JPQL
- KHÔNG dùng Hibernate `@Where` (deprecated Hibernate 6)

### Schema
- Flyway owns DDL, `spring.jpa.hibernate.ddl-auto: validate`

## Database Schema (main tables)
- `users` — id, username, email, password, avatar_url, created_at, updated_at
- `chat_rooms` — id, name, type (PUBLIC/PRIVATE), created_by, created_at
- `chat_room_members` — room_id, user_id, role (ADMIN/MEMBER), joined_at
- `messages` — id, room_id, sender_id, content, type, created_at, deleted_at
- `direct_messages` — id, sender_id, recipient_id, content, created_at