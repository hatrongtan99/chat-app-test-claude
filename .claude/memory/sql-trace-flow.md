# SQL Trace Logging — Flow & Architecture

## Files liên quan

- `com.chatapp.common.aop.SqlTraceAspect` — AOP intercept, set MDC
- `com.chatapp.common.aop.SqlTraceQueryListener` — JDBC listener, log SQL + bind values
- `com.chatapp.config.DataSourceProxyConfig` — BeanPostProcessor wrap DataSource
- `app.sql-trace.enabled` trong `application.yml` — bật/tắt toàn bộ

## Flow

```
HTTP Request → JwtAuthFilter → Controller
                                    │
                                    ▼
                        SqlTraceAspect (@Around AOP)
                        Pointcut: public * com.chatapp..service..*
                                  public * com.chatapp..repository..*
                        → MDC.put("sqlCaller", "MessageService.getMessages")
                          (chỉ set nếu chưa có — giữ outermost caller)
                                    │
                                    ▼
                        Service → Repository → Hibernate → JDBC
                                                               │
                                                               ▼
                                              datasource-proxy (wrap qua BeanPostProcessor)
                                              SqlTraceQueryListener.afterQuery()
                                                1. đọc MDC["sqlCaller"]
                                                2. lấy SQL từ QueryInfo
                                                3. lấy bind values từ ParameterSetOperation
                                                   args[0]=index, args[1]=giá trị thực
                                                4. log.debug(...)
                                                               │
                                                               ▼
                                              [SQL-TRACE] MessageService.getMessages
                                                | params=[42, 20] | time=3ms
                                                SELECT m.id FROM messages m
                                                WHERE m.room_id=42 AND m.deleted_at IS NULL
                                                               │
                                    ┌──────────────────────────┘
                                    ▼
                        AOP finally block
                        → MDC.remove("sqlCaller")
```

## Lý do chọn từng layer

| Layer | Công cụ | Lý do |
|-------|---------|-------|
| Bắt tên caller | AOP `@Around` | Duy nhất thấy method name trước khi SQL chạy |
| Truyền context | MDC (ThreadLocal) | Không cần pass parameter qua call stack |
| Bắt SQL + bind values | datasource-proxy | JDBC level — thấy giá trị thực, không phải `?` |
| Tránh startup cycle | `BeanPostProcessor` | Wrap sau khi DataSource xong, không tạo dependency cycle |

## Tại sao giữ outermost caller

```java
// AOP intercept cả service lẫn repository
// Nếu không check previous, "sqlCaller" bị overwrite thành repo name
if (previous == null) {
    MDC.put(MDC_KEY, caller); // → giữ "MessageService.getMessages"
}
```

## Bật/tắt

```yaml
app:
  sql-trace:
    enabled: false  # @ConditionalOnProperty → không tạo bean → zero overhead
```

Khi `false`: cả `SqlTraceAspect` và `DataSourceProxyConfig` không được khởi tạo.
`SqlTracingStatementInspector` (Hibernate hook cũ) đã bị xóa — không còn dùng.
