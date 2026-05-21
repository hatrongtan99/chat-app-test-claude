# Plan: Production-Ready Backend Chat Application

## Context

Backend chat application với Java 21 + Spring Boot 3.5.x, WebSocket/STOMP realtime, Redis Pub/Sub multi-instance fanout, MySQL persistence, JWT authentication. Clean Architecture / DDD domain-based. Working directory `d:\AI\chat`.

---

## Project Structure

```
d:\AI\chat\
├── pom.xml
├── Dockerfile
├── docker-compose.yml
├── .gitignore
├── README.md
└── src/
    ├── main/
    │   ├── resources/
    │   │   ├── application.yml
    │   │   ├── application-docker.yml
    │   │   ├── db/migration/V1__init_schema.sql
    │   │   └── static/
    │   │       ├── index.html
    │   │       └── app.js
    │   └── java/com/chatapp/
    │       ├── ChatApplication.java
    │       ├── common/
    │       │   ├── entity/BaseEntity.java          (@MappedSuperclass auditing)
    │       │   ├── exception/AppException.java
    │       │   ├── exception/ErrorCode.java
    │       │   ├── exception/GlobalExceptionHandler.java
    │       │   ├── response/ApiResponse.java
    │       │   └── response/PageResponse.java
    │       ├── config/
    │       │   ├── SecurityConfig.java
    │       │   ├── RedisConfig.java
    │       │   ├── WebSocketConfig.java
    │       │   └── JacksonConfig.java
    │       ├── security/
    │       │   ├── JwtProperties.java
    │       │   ├── JwtService.java
    │       │   ├── CustomUserDetailsService.java
    │       │   ├── JwtAuthFilter.java
    │       │   └── RateLimitFilter.java            (pure Redis sliding window)
    │       ├── domain/
    │       │   ├── user/
    │       │   │   ├── entity/User.java
    │       │   │   ├── repository/UserRepository.java
    │       │   │   ├── dto/UserResponse.java
    │       │   │   ├── dto/UpdateProfileRequest.java
    │       │   │   ├── mapper/UserMapper.java
    │       │   │   ├── service/UserService.java
    │       │   │   └── controller/UserController.java
    │       │   ├── auth/
    │       │   │   ├── dto/RegisterRequest.java
    │       │   │   ├── dto/LoginRequest.java
    │       │   │   ├── dto/AuthResponse.java
    │       │   │   ├── dto/RefreshRequest.java
    │       │   │   ├── service/AuthService.java
    │       │   │   └── controller/AuthController.java
    │       │   ├── chat/
    │       │   │   ├── entity/ChatRoom.java
    │       │   │   ├── entity/RoomType.java
    │       │   │   ├── repository/ChatRoomRepository.java
    │       │   │   ├── dto/CreateRoomRequest.java
    │       │   │   ├── dto/ChatRoomResponse.java
    │       │   │   ├── dto/InviteRequest.java
    │       │   │   ├── mapper/ChatRoomMapper.java
    │       │   │   ├── service/ChatRoomService.java
    │       │   │   └── controller/ChatRoomController.java
    │       │   ├── room/
    │       │   │   ├── entity/ChatRoomMember.java
    │       │   │   ├── entity/MemberRole.java
    │       │   │   └── repository/ChatRoomMemberRepository.java
    │       │   └── message/
    │       │       ├── entity/Message.java
    │       │       ├── entity/MessageType.java
    │       │       ├── repository/MessageRepository.java
    │       │       ├── dto/SendMessageRequest.java
    │       │       ├── dto/MessageResponse.java
    │       │       ├── dto/WebSocketMessage.java
    │       │       ├── dto/TypingEvent.java
    │       │       ├── mapper/MessageMapper.java
    │       │       ├── service/MessageService.java
    │       │       └── controller/MessageController.java
    │       ├── websocket/
    │       │   ├── ChatMessageController.java
    │       │   └── WebSocketEventListener.java
    │       ├── infrastructure/
    │       │   └── redis/
    │       │       ├── RedisMessageSubscriber.java
    │       │       └── PresenceService.java
    │       └── application/
    │           └── CreateRoomWithMemberUseCase.java
    └── test/java/com/chatapp/
        ├── ChatApplicationTests.java
        ├── domain/auth/AuthServiceTest.java
        ├── domain/chat/ChatRoomServiceTest.java
        └── security/JwtServiceTest.java
```

---

## Implementation Steps

### Step 1 — Build System (`pom.xml`)

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0" ...>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.0</version>
  </parent>

  <groupId>com.chatapp</groupId>
  <artifactId>chat-app</artifactId>
  <version>0.0.1-SNAPSHOT</version>
  <packaging>jar</packaging>

  <properties>
    <java.version>21</java.version>
    <mapstruct.version>1.5.5.Final</mapstruct.version>
    <jjwt.version>0.13.0</jjwt.version>
    <testcontainers.version>1.19.8</testcontainers.version>
  </properties>

  <dependencies>
    <!-- Spring Boot starters -->
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-websocket</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-redis</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>

    <!-- MySQL + Flyway (flyway-mysql PHẢI có cùng flyway-core cho Flyway 9+) -->
    <dependency><groupId>com.mysql</groupId><artifactId>mysql-connector-j</artifactId><scope>runtime</scope></dependency>
    <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency>
    <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-mysql</artifactId></dependency>

    <!-- JWT (3 artifacts: api + impl + jackson) -->
    <dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-api</artifactId><version>${jjwt.version}</version></dependency>
    <dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-impl</artifactId><version>${jjwt.version}</version><scope>runtime</scope></dependency>
    <dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-jackson</artifactId><version>${jjwt.version}</version><scope>runtime</scope></dependency>

    <!-- MapStruct -->
    <dependency><groupId>org.mapstruct</groupId><artifactId>mapstruct</artifactId><version>${mapstruct.version}</version></dependency>

    <!-- Lombok -->
    <dependency><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId><optional>true</optional></dependency>

    <!-- Test -->
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.springframework.security</groupId><artifactId>spring-security-test</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.testcontainers</groupId><artifactId>junit-jupiter</artifactId><version>${testcontainers.version}</version><scope>test</scope></dependency>
    <dependency><groupId>org.testcontainers</groupId><artifactId>mysql</artifactId><version>${testcontainers.version}</version><scope>test</scope></dependency>
    <dependency><groupId>com.redis</groupId><artifactId>testcontainers-redis</artifactId><version>2.2.2</version><scope>test</scope></dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
        <configuration>
          <excludes>
            <exclude><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId></exclude>
          </excludes>
        </configuration>
      </plugin>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <configuration>
          <annotationProcessorPaths>
            <!-- ORDER MATTERS: lombok trước mapstruct-processor -->
            <path><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId></path>
            <path><groupId>org.mapstruct</groupId><artifactId>mapstruct-processor</artifactId><version>${mapstruct.version}</version></path>
            <path><groupId>org.projectlombok</groupId><artifactId>lombok-mapstruct-binding</artifactId><version>0.2.0</version></path>
          </annotationProcessorPaths>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

### Step 2 — Docker & Config

**`docker-compose.yml`**: MySQL 8.0 + Redis 7-alpine. Named volumes, healthchecks. App service với `SPRING_PROFILES_ACTIVE: docker` và `depends_on: {condition: service_healthy}`.

**`application.yml`**:
```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/chatapp?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
    username: chatuser
    password: chatpass
  jpa:
    hibernate:
      ddl-auto: validate        # Flyway owns schema — Hibernate chỉ validate
    show-sql: false
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
  data:
    redis:
      host: localhost
      port: 6379

jwt:
  secret: "your-256-bit-secret-minimum-32-chars-changeme"
  access-expiration: 900000     # 15 phút (ms)
  refresh-expiration: 604800000 # 7 ngày (ms)

app:
  cors:
    allowed-origins: "http://localhost:3000,http://localhost:8080"
  rate-limit:
    auth-max-requests: 5        # per minute per IP
    auth-window-seconds: 60

logging:
  level:
    com.chatapp: DEBUG
```

**`application-docker.yml`**: override datasource URL dùng `mysql:3306`, redis host dùng `redis`.

### Step 3 — Database Migration (`V1__init_schema.sql`)

```sql
CREATE TABLE users (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    username     VARCHAR(50)  NOT NULL UNIQUE,
    email        VARCHAR(255) NOT NULL UNIQUE,
    password     VARCHAR(255) NOT NULL,
    display_name VARCHAR(100),
    avatar_url   VARCHAR(500),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE chat_rooms (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    type        ENUM('PUBLIC','PRIVATE') NOT NULL DEFAULT 'PUBLIC',
    created_by  BIGINT NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES users(id)
);

CREATE TABLE chat_room_members (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id   BIGINT NOT NULL,
    user_id   BIGINT NOT NULL,
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    role      ENUM('ADMIN','MEMBER') NOT NULL DEFAULT 'MEMBER',
    UNIQUE KEY uq_room_user (room_id, user_id),
    FOREIGN KEY (room_id) REFERENCES chat_rooms(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id)      ON DELETE CASCADE
);

CREATE TABLE messages (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id    BIGINT NOT NULL,
    sender_id  BIGINT NOT NULL,
    content    TEXT   NOT NULL,
    type       ENUM('TEXT','IMAGE','SYSTEM') NOT NULL DEFAULT 'TEXT',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL DEFAULT NULL,
    FOREIGN KEY (room_id)   REFERENCES chat_rooms(id) ON DELETE CASCADE,
    FOREIGN KEY (sender_id) REFERENCES users(id)
);

CREATE INDEX idx_messages_room_created ON messages(room_id, created_at DESC);
CREATE INDEX idx_members_user          ON chat_room_members(user_id);
```

### Step 4 — Common Layer

**`BaseEntity`** (`common/entity/`):
```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class BaseEntity {
    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
```
Bật `@EnableJpaAuditing` trên `ChatApplication`. Mọi entity extends `BaseEntity` thay vì tự viết `@PrePersist`.

**`ErrorCode`** enum: `USER_NOT_FOUND(404)`, `ROOM_NOT_FOUND(404)`, `UNAUTHORIZED(401)`, `FORBIDDEN(403)`, `DUPLICATE_USERNAME(409)`, `DUPLICATE_EMAIL(409)`, `INVALID_TOKEN(401)`, `INVALID_CREDENTIALS(401)`, `ROOM_NOT_MEMBER(403)`, `ALREADY_MEMBER(409)`, `RATE_LIMIT_EXCEEDED(429)`.

**`AppException(ErrorCode)`**: RuntimeException mang error code.

**`ApiResponse<T>`**: `{success, message, data, timestamp}` với static factories `success(data)`, `success(msg, data)`, `error(msg)`.

**`PageResponse<T>`**: `{content, page, size, totalElements, totalPages, last}` với `from(Page<T>)`.

**`GlobalExceptionHandler`** (`@RestControllerAdvice`): xử lý `AppException`, `MethodArgumentNotValidException`, `AccessDeniedException`.

### Step 5 — Security Layer

**`JwtProperties`** (`@ConfigurationProperties(prefix="jwt")`): `secret`, `accessExpiration`, `refreshExpiration`.

**`JwtService`**:
- `getSigningKey()`: `Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))`
- `generateAccessToken(UserDetails)`: claims `{sub=username, type=ACCESS}`, exp = now + accessExpiration
- `generateRefreshToken(Long userId)`: claims `{sub=userId, type=REFRESH, tokenId=UUID.randomUUID()}`, exp = now + refreshExpiration
- Parser (jjwt 0.13.0): `Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload()`
- `extractUsername(token)`, `extractUserId(token)`, `extractTokenId(token)`, `isTokenValid(token, UserDetails)`, `isTokenExpired(token)`

**`CustomUserDetailsService`**: implements `UserDetailsService`, load by `username` từ `UserRepository`.

**`JwtAuthFilter`** extends `OncePerRequestFilter`:
1. Extract `Authorization: Bearer <token>`
2. `jwtService.extractUsername(token)`
3. `userDetailsService.loadUserByUsername(username)`
4. `jwtService.isTokenValid(token, userDetails)` → set `SecurityContextHolder`

**`RateLimitFilter`** extends `OncePerRequestFilter` — **pure code, không dùng thư viện ngoài**:
```java
// Sliding fixed-window counter dùng Redis INCR + EXPIRE
// Chỉ áp dụng cho: POST /api/auth/login, POST /api/auth/register

String ip = request.getRemoteAddr();
String minute = String.valueOf(System.currentTimeMillis() / 60_000);
String key = "rate_limit:" + ip + ":" + endpointGroup + ":" + minute;

Long count = stringRedisTemplate.opsForValue().increment(key);
if (count == 1) {
    stringRedisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
}
if (count > maxRequests) {
    response.setStatus(429);
    response.setContentType("application/json");
    response.getWriter().write("{\"success\":false,\"message\":\"Too many requests\"}");
    return;
}
filterChain.doFilter(request, response);
```
`endpointGroup` = path prefix (e.g., `"auth"`) để nhóm login + register chung 1 bucket nếu muốn. Config (`maxRequests`, `windowSeconds`) đọc từ `application.yml` qua `@Value`.

### Step 6 — Config Layer

**`SecurityConfig`**:
- CSRF disabled, CORS qua `CorsConfigurationSource` bean — `allowedOrigins` đọc từ `app.cors.allowed-origins`
- `SessionCreationPolicy.STATELESS`
- permitAll: `/api/auth/**`, `/ws/**`, `/v3/api-docs/**`
- Custom 401 `AuthenticationEntryPoint` trả JSON
- Filter order: `RateLimitFilter` → `JwtAuthFilter` → `UsernamePasswordAuthenticationFilter`
- **Không khai báo `AuthenticationProvider` bean thủ công** — Spring Boot auto-configure khi có sẵn `UserDetailsService` + `PasswordEncoder`
- Expose `AuthenticationManager` qua `AuthenticationConfiguration.getAuthenticationManager()`
- `BCryptPasswordEncoder` bean

**`RedisConfig`**:
- **Không khai báo `StringRedisTemplate`** — Spring Boot đã auto-configure sẵn, inject trực tiếp
- `@Bean("pubSubRedisTemplate") RedisTemplate<String, Object>`: `GenericJackson2JsonRedisSerializer` cho value — inject qua `@Qualifier` trong `MessageService`
- `RedisMessageListenerContainer`: subscribe `ChannelTopic("chat:messages")` (1 channel cố định, roomId nằm trong payload — tránh `PSUBSCRIBE` overhead)
- `MessageListenerAdapter`: wrap `RedisMessageSubscriber.onMessage()`

**`WebSocketConfig`** (`@EnableWebSocketMessageBroker`):
- Endpoint: `/ws/chat` với `.withSockJS()`, `setAllowedOriginPatterns("*")`
- Broker: `enableSimpleBroker("/topic", "/queue")`, prefix `/app`, user prefix `/user`
- `configureClientInboundChannel()`: `ChannelInterceptor.preSend()`:
  - Nếu `StompCommand.CONNECT`: validate JWT từ header `Authorization` → set `Principal`
  - Các command khác: refresh presence TTL (`presenceService.refresh(userId)`) để tránh TTL expire khi user vẫn online

**`JacksonConfig`**: `ObjectMapper` với `JavaTimeModule`, `WRITE_DATES_AS_TIMESTAMPS=false`.

### Step 7 — User Domain

**`User`** entity extends `BaseEntity`, implements `UserDetails`:
- Fields: `id`, `username`, `email`, `password`, `displayName`, `avatarUrl`
- UserDetails defaults: `getAuthorities()` trả `List.of()`, các boolean trả `true`

**`UserRepository`**: `findByUsername`, `findByEmail`, `existsByUsername`, `existsByEmail`

**`UserMapper`** (MapStruct `componentModel="spring"`): `toResponse(User) → UserResponse`

**`UserService`**: `getProfile(userId)`, `updateProfile(userId, request)`, `searchUsers(query)`

**`UserController`** `/api/users`: `GET /me`, `PUT /me`, `GET /search?q=`, `GET /{id}/online`

### Step 8 — Auth Domain

**`RegisterRequest`**: `@NotBlank @Size(min=3,max=50) @Pattern(regexp="^[a-zA-Z0-9_]+$")` username; `@Email` email; `@Size(min=8)` password

**`AuthService`**:
- `register()`: check duplicate → save với BCrypt password → generate tokens → lưu refresh token Redis key `refresh_token:{userId}:{tokenId}` với TTL
- `login()`: `authenticationManager.authenticate()` → generate tokens → lưu Redis
- `refresh(refreshToken)`:
  1. Parse token, extract `userId` + `tokenId`
  2. Kiểm tra key tồn tại trong Redis
  3. Nếu key **không tồn tại** nhưng token **hợp lệ + chưa hết hạn** → token bị replay/đánh cắp → xoá toàn bộ key `refresh_token:{userId}:*` của user (revoke all sessions)
  4. Nếu key tồn tại → xoá key cũ → generate tokens mới → lưu key mới (rotation)
- `logout(refreshToken)`: parse → xoá Redis key

Redis key helper: `"refresh_token:" + userId + ":" + tokenId`

**`AuthController`** `/api/auth`: POST `/register`, `/login`, `/refresh`, `/logout`

### Step 9 — Chat Room Domain

**`ChatRoom`** extends `BaseEntity`: `id`, `name`, `description`, `type (RoomType enum)`, `@ManyToOne(LAZY) createdBy`

**`ChatRoomMember`**: `id`, `@ManyToOne(LAZY) room`, `@ManyToOne(LAZY) user`, `joinedAt`, `role (MemberRole enum)`. Unique constraint: `(room_id, user_id)`.

**`ChatRoomRepository`**: `findByType(RoomType)`, JPQL `findRoomsByUserId(userId)` (JOIN members), `findByTypeAndNameContainingIgnoreCase(type, name, Pageable)`

**`ChatRoomMemberRepository`**: `findByRoomIdAndUserId`, `existsByRoomIdAndUserId`, `countByRoomId`, `deleteByRoomIdAndUserId`

**`ChatRoomService`**:
- `createRoom(creatorId, request)`: save room → auto-add creator as ADMIN member
- `getPublicRooms(currentUserId)`: PUBLIC rooms + memberCount + isMember flag
- `joinRoom(roomId, userId)`: type=PRIVATE → throw FORBIDDEN; already member → ALREADY_MEMBER; else add MEMBER
- `leaveRoom(roomId, userId)`: not member → ROOM_NOT_MEMBER; else delete membership
- `inviteUser(roomId, inviterUserId, targetUserId)`: inviter phải là ADMIN → target chưa là member → add MEMBER trực tiếp (không cần notification table)

**`ChatRoomController`** `/api/rooms`: `GET /`, `POST /`, `GET /{id}`, `POST /{id}/join`, `DELETE /{id}/leave`, `GET /{id}/members`, `GET /my`, `POST /{id}/invite`

### Step 10 — Message Domain

**`Message`** extends `BaseEntity`: `id`, `@ManyToOne(LAZY) room`, `@ManyToOne(LAZY) sender`, `content (TEXT)`, `type (MessageType)`, `deletedAt (nullable)`. Soft delete: set `deletedAt`, không xoá row.

**`MessageRepository`** JPQL:
```java
@Query("""
    SELECT m FROM Message m JOIN FETCH m.sender
    WHERE m.room.id = :roomId AND m.deletedAt IS NULL
    ORDER BY m.createdAt DESC
""")
Page<Message> findByRoomId(@Param("roomId") Long roomId, Pageable pageable);
```
Không dùng `@Where` (deprecated Hibernate 6).

**`WebSocketMessage` DTO**: `{eventType, roomId, message, userId, username, timestamp}` — publish lên Redis, route xuống STOMP clients.

**`MessageService`**:
- `getRoomMessages(roomId, userId, page, size)`: verify membership → `PageRequest.of(page, size)` → `PageResponse`
- `sendMessage(roomId, senderId, request)`: verify membership → save Message → build WebSocketMessage → `pubSubRedisTemplate.convertAndSend("chat:messages", wsMessage)`
- `sendSystemMessage(roomId, content)`: tạo Message type=SYSTEM + publish

**`MessageController`** `/api/rooms/{roomId}/messages`: `GET /?page=0&size=20`, `POST /`, `DELETE /{messageId}`

### Step 11 — WebSocket Layer

**`ChatMessageController`** (`@Controller`):
- `@MessageMapping("/chat.send")`: extract userId từ `Principal` → `messageService.sendMessage()` (publish to Redis bên trong)
- `@MessageMapping("/chat.typing")`: build TypingEvent WebSocketMessage → `pubSubRedisTemplate.convertAndSend("chat:messages", wsMessage)`

**`WebSocketEventListener`** (`@EventListener`):
- `SessionConnectedEvent`: extract userId → `presenceService.setOnline(userId)` → broadcast PRESENCE event
- `SessionDisconnectEvent`: `presenceService.setOffline(userId)` → broadcast PRESENCE event

### Step 12 — Infrastructure Layer

**`RedisMessageSubscriber`** implements `MessageListener`:
```java
@Override
public void onMessage(Message message, byte[] pattern) {
    WebSocketMessage wsMsg = objectMapper.readValue(message.getBody(), WebSocketMessage.class);
    // Route tới đúng room topic dựa vào roomId trong payload
    messagingTemplate.convertAndSend("/topic/rooms/" + wsMsg.getRoomId(), wsMsg);
}
```
Dùng `ChannelTopic("chat:messages")` thay vì `PatternTopic("chat:room:*")` — tránh PSUBSCRIBE overhead.

**`PresenceService`** (inject `StringRedisTemplate` auto-configured):
```java
// TTL = 2× STOMP heartbeat interval (e.g., 60s nếu heartbeat 30s)
// Refresh được gọi từ WebSocketConfig ChannelInterceptor mỗi inbound frame
void setOnline(Long userId)  → SET user:online:{userId} "1" EX 60
void setOffline(Long userId) → DEL user:online:{userId}
void refresh(Long userId)    → EXPIRE user:online:{userId} 60
boolean isOnline(Long userId)→ EXISTS user:online:{userId}
```

### Step 13 — Application Use Case

**`CreateRoomWithMemberUseCase`** (`@Component`): orchestrate `ChatRoomService.createRoom()` + `MessageService.sendSystemMessage()` trong 1 `@Transactional`.

### Step 14 — Frontend (`src/main/resources/static/`)

**`index.html`**: auth section (login + register toggle) + app section (room list sidebar + message area + input + typing indicator). Load từ **CDN**:
```html
<script src="https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/stompjs@2.3.3/lib/stomp.min.js"></script>
```

**`app.js`**:
- `apiFetch(path, options)`: attach Bearer header, auto-refresh on 401 với `localStorage.getItem('refreshToken')`
- `connectWebSocket()`: `new SockJS('/ws/chat')` → `Stomp.over(socket)` → `connect({Authorization: 'Bearer ' + token}, ...)`
- `subscribeToRoom(roomId)`: `stompClient.subscribe('/topic/rooms/' + roomId, onMessage)`
- `sendMessage(content)`: `stompClient.send('/app/chat.send', {}, JSON.stringify({roomId, content, type:'TEXT'}))`
- `sendTyping(isTyping)`: `stompClient.send('/app/chat.typing', {}, JSON.stringify({roomId, typing: isTyping}))`
- `handleIncomingMessage(wsMsg)`: switch `wsMsg.eventType` → render CHAT_MESSAGE / TYPING / PRESENCE

### Step 15 — README.md

- Prerequisites: Java 21, Docker Compose
- Local dev: `docker-compose up mysql redis -d` → `mvn spring-boot:run`
- Full stack: `./gradlew build -x test && docker-compose up`
- Env vars: JWT_SECRET, MYSQL_PASSWORD, SPRING_PROFILES_ACTIVE

---

## Critical Gotchas

| Issue | Solution |
|-------|----------|
| Flyway 9+ + MySQL | `flyway-mysql` artifact PHẢI khai báo cùng `flyway-core` — thiếu là startup failure |
| jjwt 0.13.0 API | `Jwts.parser().verifyWith(key).build()` — không có `setSigningKey()` |
| MapStruct + Lombok order | `annotationProcessorPaths` trong `maven-compiler-plugin`: lombok → mapstruct-processor → lombok-mapstruct-binding |
| StringRedisTemplate | KHÔNG khai báo bean thủ công — inject auto-configured bean |
| DaoAuthenticationProvider | KHÔNG khai báo explicit bean — Spring Boot auto-configure |
| STOMP auth | JWT trong STOMP CONNECT header, validate qua `ChannelInterceptor` |
| Presence TTL | Refresh TTL mỗi inbound STOMP frame (trong ChannelInterceptor), không chỉ dựa vào connect/disconnect |
| Hibernate 6 soft delete | JPQL `m.deletedAt IS NULL` — không dùng deprecated `@Where` |
| LocalDateTime Jackson | `JacksonConfig` phải register `JavaTimeModule` |
| SockJS CORS | Security `permitAll("/ws/**")`; WebSocket CORS riêng trong `registerStompEndpoints()` |
| Rate limit Redis key | `"rate_limit:{ip}:{group}:{currentMinute}"` — INCR + EXPIRE (set EXPIRE chỉ khi count==1) |
| Redis pub/sub channel | Dùng `ChannelTopic("chat:messages")` 1 channel, route theo `wsMsg.getRoomId()` trong payload |

---

## Verification (End-to-End)

1. `docker-compose up mysql redis -d` → `mvn spring-boot:run` → app lên :8080
2. `POST /api/auth/register` → nhận `{accessToken, refreshToken}`
3. `POST /api/auth/login` → nhận tokens
4. `POST /api/auth/login` 6 lần trong 1 phút → lần 6 trả `429`
5. `POST /api/auth/refresh` → nhận access token mới; refresh lần 2 với token cũ → `401`
6. `POST /api/rooms` (PUBLIC) → room created, creator là ADMIN
7. `POST /api/rooms` (PRIVATE) → user B `POST /join` → `403`; ADMIN `POST /invite {userId: B}` → B vào được
8. `GET /api/rooms/{id}/messages?page=0&size=20` → newest first, page metadata đúng
9. Mở `http://localhost:8080` → login → join room → send message → thấy realtime
10. Tab 2: send → tab 1 nhận qua Redis pub/sub (test multi-instance fanout)
11. Close tab 1 → presence offline

---

## Deferred Features & Decisions

Xem [BACKLOG.md](BACKLOG.md) để biết:
- Các quyết định đã chốt (build tool, base package, rate limit strategy, message order, private room flow)
- Tính năng deferred: Read Receipts, Message Reactions
- Tính năng có thể cân nhắc sau: file upload, message edit, push notification, search, cursor pagination
