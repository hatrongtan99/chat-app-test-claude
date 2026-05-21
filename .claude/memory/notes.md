# Ghi chú quan trọng

## Gotchas khi build

- `flyway-mysql` phải khai báo cùng `flyway-core` — thiếu là startup fail
- Annotation processor order trong maven-compiler-plugin: `lombok` -> `mapstruct-processor` -> `lombok-mapstruct-binding`
- jjwt 0.13.0 API: dùng `Jwts.parser().verifyWith(key).build()`, không có `setSigningKey()`
- `JacksonConfig` phải register `JavaTimeModule` + disable `WRITE_DATES_AS_TIMESTAMPS`
- `DaoAuthenticationProvider` KHÔNG declare bean thủ công — Spring Boot tự config khi có `UserDetailsService` + `PasswordEncoder`

## Conventions

- Commands: dùng `.\mvnw.cmd` (Maven Wrapper), không dùng `mvn` global
- Không dùng Webjars — SockJS và STOMP.js load từ CDN trong index.html
- Redis StringRedisTemplate: inject trực tiếp (auto-configured)
- pubSubRedisTemplate: inject qua `@Qualifier("pubSubRedisTemplate")`

## AOP SQL Tracing

- Cơ chế: `SqlTraceAspect` (AOP) → MDC["sqlCaller"] → `SqlTraceQueryListener` (datasource-proxy JDBC) → log
- File: `com.chatapp.common.aop.SqlTraceAspect`, `SqlTraceQueryListener`, `config.DataSourceProxyConfig`
- Bật/tắt: `app.sql-trace.enabled` trong `application.yml` (@ConditionalOnProperty → zero overhead khi false)
- DataSource wrap dùng BeanPostProcessor (không phải @Bean inject) để tránh circular dependency với Flyway
- Bind values lấy từ `ParameterSetOperation.getArgs()[1]` (index=0, value=1)
- Chỉ giữ outermost caller trong MDC — service layer, không bị overwrite bởi repo layer
- Flow chi tiết: xem `.claude/memory/sql-trace-flow.md`

## Sessions

### 2026-05-19
- Giải thích STOMP destinations: `/topic` vs `/queue` vs `/user/queue`
- `/topic` và `/queue` với SimpleBroker hoạt động như nhau (đều broadcast) — tên chỉ là convention
- `setUserDestinationPrefix("/user")` là prefix toàn cụm, không phải chỉ cho `/queue`
- `convertAndSendToUser("alice", "/queue/dm", msg)` -> Spring map tới tất cả session của alice
- Client subscribe `/user/queue/direct-messages` -> Spring đính session ID nội bộ
- Implement: WebSocketConfig CONNECT frame giờ throw `MessageDeliveryException` khi JWT sai/thiếu thay vì silent ignore

### 2026-05-19 — Thảo luận WebSocket scaling (CHƯA implement, session sau bàn tiếp)

Kiến trúc hiện tại đúng hướng scale ngang: Redis Pub/Sub fanout giữa các instance. Auto-scale thêm instance OK.

4 điểm nghẽn đã phân tích:
1. `enableSimpleBroker` = in-memory broker (NGHẼN LỚN NHẤT) — subscription + heartbeat nằm RAM 1 JVM, không persistent, ~vài chục nghìn conn/instance. Giải pháp: chuyển sang external STOMP broker (RabbitMQ/ActiveMQ) qua `enableStompBrokerRelay()`
2. SockJS bắt buộc sticky session ở load balancer (cấu hình theo JSESSIONID/cookie)
3. WebSocket scale theo SỐ CONNECTION đồng thời, KHÔNG theo CPU — auto-scale rule nên dựa trên active connection count, không phải CPU
4. Redis Pub/Sub fanout O(n) — bottleneck ở mức rất cao (triệu msg/s)

Thay đổi quan trọng nhất khi lên scale rất lớn: SimpleBroker -> external broker relay.

TODO session sau: user cân nhắc triển khai phần nào — external broker relay / sticky session config / scaling metrics.