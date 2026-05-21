# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Rules

- **Chỉ thao tác file trong thư mục này** (`d:\AI\chat`). Không được đọc, sửa, xóa, hoặc tạo file ngoài thư mục project này.
- Không xóa file nào nếu không được user yêu cầu rõ ràng.

## Project

Production-ready real-time chat backend built with Java 21 + Spring Boot **3.5.x**. Uses WebSocket/STOMP for realtime messaging, Redis Pub/Sub for multi-instance message fanout, MySQL for persistence, and JWT for stateless authentication.

## Build & Run Commands

Maven chưa cài toàn cục — dùng Maven Wrapper (`mvnw.cmd` trên Windows):

```powershell
# Start infrastructure only (dev mode)
docker-compose up mysql redis -d

# Run application locally
.\mvnw.cmd spring-boot:run

# Build JAR (skip tests)
.\mvnw.cmd package -DskipTests

# Run all tests
.\mvnw.cmd test

# Run a single test class
.\mvnw.cmd test -Dtest="AuthServiceTest"

# Run a single test method
.\mvnw.cmd test -Dtest="AuthServiceTest#shouldRegisterUser"

# Full Docker stack
.\mvnw.cmd package -DskipTests; docker-compose up

# Check code compilation without running
.\mvnw.cmd compile
```

## Architecture

### Package Layout

```
com.chatapp
├── common/          # Shared: ErrorCode, AppException, ApiResponse<T>, PageResponse<T>, GlobalExceptionHandler
├── config/          # Spring beans: SecurityConfig, RedisConfig, WebSocketConfig, JacksonConfig
├── security/        # JWT: JwtProperties, JwtService, JwtAuthFilter, CustomUserDetailsService
├── domain/
│   ├── user/        # User entity (implements UserDetails), UserRepository, UserService, UserController
│   ├── auth/        # AuthService (register/login/refresh/logout), AuthController
│   ├── chat/        # ChatRoom entity, ChatRoomService, ChatRoomController
│   ├── room/        # ChatRoomMember entity + repository (membership join table)
│   ├── message/     # Message entity, MessageService, MessageController, WebSocketMessage DTO
│   └── directmessage/ # DirectMessage entity, DirectMessageService, DirectMessageController, DTOs, mapper
├── websocket/       # STOMP @MessageMapping handlers: ChatMessageController, DirectMessageWsController, WebSocketEventListener
├── infrastructure/  # RedisMessageSubscriber, RedisDmSubscriber (pub/sub listeners), PresenceService
└── application/     # Cross-domain use cases (e.g., CreateRoomWithMemberUseCase)
```

Each domain folder contains: `entity/`, `repository/`, `dto/`, `mapper/`, `service/`, `controller/`.

### Request Flow

**REST:** `JwtAuthFilter` → `SecurityConfig` filter chain → Controller → Service → Repository

**WebSocket send:**
1. Client sends STOMP frame to `/app/chat.send`
2. `ChatMessageController.handleChatMessage()` calls `MessageService.sendMessage()`
3. `MessageService` saves to MySQL, then publishes `WebSocketMessage` JSON to Redis channel `chat:room:{roomId}`
4. `RedisMessageSubscriber.onMessage()` receives from Redis, calls `messagingTemplate.convertAndSend("/topic/rooms/{roomId}", wsMessage)`
5. All STOMP subscribers on that topic receive the message (works across multiple app instances)

**WebSocket receive:** Client subscribes to `/topic/rooms/{roomId}`.

**Direct message send:**
1. Client sends STOMP frame to `/app/dm.send` with `{recipientId, content}` or calls `POST /api/dm/{recipientId}`
2. `DirectMessageWsController.handleDirectMessage()` (or `DirectMessageController.send()`) calls `DirectMessageService.send()`
3. `DirectMessageService` saves to MySQL (`direct_messages`), then publishes `DirectMessageEvent` JSON to Redis channel `chat:dm`
4. `RedisDmSubscriber.onMessage()` receives from Redis, calls `messagingTemplate.convertAndSendToUser()` for both recipient and sender (echo)
5. Each participant's STOMP session receives the event at `/user/queue/direct-messages` (only the instance where they are connected delivers it)

**Direct message receive:** Client subscribes to `/user/queue/direct-messages`.

### Key Design Decisions

**RedisTemplate beans** — Spring Boot auto-configures `StringRedisTemplate`; do NOT redeclare it. `RedisConfig` defines only one custom bean:
- `@Bean("pubSubRedisTemplate") RedisTemplate<String, Object>` with `GenericJackson2JsonRedisSerializer` — injected via `@Qualifier` into `MessageService` and `DirectMessageService` for pub/sub
- `AuthService`, `PresenceService`, `RateLimitFilter` inject `StringRedisTemplate` directly (auto-configured bean)
- Two Redis channels: `chat:messages` (room messages) and `chat:dm` (direct messages), each with its own `ChannelTopic` bean and `MessageListenerAdapter` registered in `RedisMessageListenerContainer`

**STOMP JWT auth** — HTTP `JwtAuthFilter` does not cover WebSocket frames. JWT is passed in the STOMP `CONNECT` frame's `Authorization` header, validated by a `ChannelInterceptor` in `WebSocketConfig.configureClientInboundChannel()`, which sets the `Principal` used in `@MessageMapping` methods.

**Direct messaging** — `directmessage` domain stores messages in `direct_messages` table (no soft-delete in V1). `setUserDestinationPrefix("/user")` and `enableSimpleBroker("/topic", "/queue")` are required for `convertAndSendToUser()` to route to `/user/{username}/queue/direct-messages`. The subscriber echoes the event to both sender and recipient so multi-tab senders also see their own messages in real time.

**Rate limiting** — `RateLimitFilter` (before `JwtAuthFilter`) applies token-bucket via `bucket4j-spring-boot-starter` backed by Redis. Enforced on `/api/auth/login` and `/api/auth/register` (e.g. 5 req/min/IP). Returns `429 Too Many Requests` when exceeded. Redis backend ensures limits are shared across instances.

**Private room invite** — `joinRoom()` throws `FORBIDDEN` if `type=PRIVATE`. To add a member, room ADMIN calls `POST /api/rooms/{id}/invite` with `{userId}`. `ChatRoomService.inviteUser()` verifies caller is ADMIN, then inserts directly into `chat_room_members`. No separate invite/notification table in V1.

**`DaoAuthenticationProvider`** — do NOT declare an explicit `AuthenticationProvider` bean. Spring Boot auto-configures it when `UserDetailsService` + `PasswordEncoder` beans are present. Only expose `AuthenticationManager` via `AuthenticationConfiguration.getAuthenticationManager()`.

**Refresh token rotation** — Refresh tokens stored in Redis as `refresh_token:{userId}:{tokenId}` with TTL. On each `/api/auth/refresh`, the old key is deleted and a new token+key pair is created. Logout deletes the key immediately.

**Soft delete for messages** — `Message.deletedAt` field; queries always filter `m.deletedAt IS NULL`. Do NOT use Hibernate `@Where` (deprecated in Hibernate 6) — use explicit JPQL conditions.

**Flyway owns the schema** — `spring.jpa.hibernate.ddl-auto: validate`. Hibernate validates only; never generates DDL. All schema changes go through `src/main/resources/db/migration/V{n}__description.sql`.

### API Surface

| Method | Path | Auth |
|--------|------|------|
| POST | `/api/auth/register` | Public |
| POST | `/api/auth/login` | Public |
| POST | `/api/auth/refresh` | Public |
| POST | `/api/auth/logout` | Bearer |
| GET/POST | `/api/rooms` | Bearer |
| GET | `/api/rooms/my` | Bearer |
| GET | `/api/rooms/{id}` | Bearer |
| POST | `/api/rooms/{id}/join` | Bearer |
| DELETE | `/api/rooms/{id}/leave` | Bearer |
| GET | `/api/rooms/{id}/members` | Bearer |
| POST | `/api/rooms/{id}/invite` | Bearer (ADMIN only) |
| GET/POST | `/api/rooms/{id}/messages` | Bearer |
| DELETE | `/api/rooms/{id}/messages/{msgId}` | Bearer |
| GET/PUT | `/api/users/me` | Bearer |
| GET | `/api/users/{id}/online` | Bearer |
| GET | `/api/dm/{partnerId}/messages` | Bearer |
| POST | `/api/dm/{recipientId}` | Bearer |
| WS | `/ws/chat` (SockJS) | STOMP CONNECT header |

STOMP topics: `/topic/rooms/{roomId}` (messages + typing events), `/topic/presence` (online/offline).
STOMP user queue: `/user/queue/direct-messages` (direct messages — both sender and recipient).
STOMP DM send: `/app/dm.send` with payload `{recipientId, content}`.

## Critical Build Gotchas

- **`flyway-mysql`** must be declared alongside `flyway-core` in `pom.xml`. Flyway 9+ requires it for MySQL; omitting causes startup failure.
- **Annotation processor order** in `maven-compiler-plugin` `annotationProcessorPaths` matters for MapStruct + Lombok: `lombok` → `mapstruct-processor` → `lombok-mapstruct-binding`. Wrong order breaks MapStruct code generation.
- **jjwt 0.13.0 API**: use `Jwts.parser().verifyWith(key).build()` — the old `setSigningKey()` does not exist in this version. Three artifacts: `jjwt-api` (impl), `jjwt-impl` + `jjwt-jackson` (runtimeOnly).
- **`JacksonConfig`** must register `JavaTimeModule` and disable `WRITE_DATES_AS_TIMESTAMPS`. Without it, `LocalDateTime` fields in `WebSocketMessage` throw serialization errors.
- **SockJS CORS**: `SecurityConfig` permits `/ws/**`; WebSocket-level CORS is configured separately in `WebSocketConfig.registerStompEndpoints()` via `.setAllowedOriginPatterns("*")`.

## Infrastructure

Docker Compose services (see `docker-compose.yml`):
- `mysql:8.0` on port 3306 — healthcheck via `mysqladmin ping`
- `redis:7-alpine` on port 6379 — healthcheck via `redis-cli ping`
- `app` — depends on both services being healthy before starting

The `application-docker.yml` profile overrides datasource URL hostname to `mysql` and Redis host to `redis` for container networking.

## Frontend Demo

Static files at `src/main/resources/static/` served by Spring Boot:
- `index.html` — login/register form + chat UI
- `app.js` — SockJS + STOMP client, JWT management, auto-refresh on 401

SockJS and STOMP.js loaded from **CDN** in `index.html` (per original requirement). Webjars are NOT used for frontend assets.
