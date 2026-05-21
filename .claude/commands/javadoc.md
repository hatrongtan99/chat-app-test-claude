# /javadoc — Java Documentation Updater

Scan Java source files and add enterprise-grade JavaDoc and inline comments. Invoke after any new feature, refactor, or file change to keep documentation current.

## Usage

```
/javadoc                    # documents files changed since last commit
/javadoc src/main/java/...  # documents a specific file or directory
/javadoc --all              # documents every .java file under src/main/java
```

## What to document

### Class-level JavaDoc
Add to every public/protected class, interface, and enum that lacks one:
- One-sentence summary of purpose
- Thread-safety note when relevant (e.g., `Thread-safe`, `Not thread-safe`, `Thread-safe via Redis`)
- Key collaborators or dependencies if non-obvious (e.g., Redis channel, STOMP broker)

### Method-level JavaDoc
Add only when the method name + parameter types do NOT make the intent obvious:
- `@param` — only for non-obvious parameters (skip `id`, `userId` if type is self-explanatory)
- `@return` — only when the return value has non-obvious semantics
- `@throws` — for every checked exception AND unchecked exceptions documented in the service contract (e.g., `AppException` with `FORBIDDEN` or `ROOM_NOT_MEMBER`)
- Transaction boundaries: note `@Transactional` scope when a method spans multiple writes

**Skip:** getters, setters, Lombok-generated constructors, simple delegate calls, trivial one-liners.

### Inline comments
Add inside method bodies only for:
- **Redis key patterns** — explain the key structure (e.g., `refresh_token:{userId}:{tokenId}`)
- **Security invariants** — token reuse detection, revocation cascades
- **Soft-delete conditions** — explain why `deletedAt IS NULL` is required vs `@Where`
- **Transaction boundaries** — when a single transaction spans Redis + DB writes, note what rolls back and what doesn't
- **Concurrency guards** — optimistic locking, Redis atomic increments
- **Performance-sensitive queries** — JOIN FETCH to avoid N+1, paginated queries
- **Legacy workarounds** — any non-obvious workaround for a framework limitation

## What NOT to document
- Obvious getters/setters
- Lombok-generated code
- Simple delegations with a single line body
- `@Override` methods where the parent JavaDoc is sufficient
- Comments that restate what the code already says clearly

## Style guide
- **Concise** — one paragraph max per method; one sentence per inline comment
- **Technical** — name the pattern (e.g., "token-bucket rate limiting", "refresh token rotation")
- **No tautologies** — never write "This method registers a user" for `register()`
- **Present tense** — "Validates the token" not "This will validate"
- Use `{@code ...}` for Redis keys, constants, and code references in JavaDoc

## Project-specific patterns (this codebase)

### Redis key conventions
```
refresh_token:{userId}:{tokenId}   → refresh token store (TTL = refreshExpiration)
user:online:{userId}               → presence marker (TTL = 60s, refreshed on STOMP frames)
rate_limit:{ip}:auth:{minuteBucket}→ sliding-window auth rate limit counter
chat:messages                      → Redis pub/sub channel for chat fanout
```

### Transaction boundaries
- `@Transactional` on service methods = MySQL transaction only
- Redis pub/sub publish in `MessageService.publish()` is **outside** the MySQL transaction — failure does not roll back the DB write
- `CreateRoomWithMemberUseCase.execute()` wraps both `createRoom` + `sendSystemMessage` in one transaction

### Security patterns
- HTTP JWT auth: `JwtAuthFilter` → reads `Authorization: Bearer` header, sets `SecurityContext`
- WebSocket JWT auth: STOMP `CONNECT` frame → `ChannelInterceptor` in `WebSocketConfig`, sets STOMP `Principal`
- Refresh rotation: each `/refresh` deletes the old Redis key and creates a new one; if the old key is missing, all sessions for that user are revoked (token reuse attack mitigation)

### Soft-delete
- `Message.deletedAt` — set to current time on delete; never hard-deleted
- All queries must include `AND m.deletedAt IS NULL` explicitly — do NOT use `@Where` (deprecated in Hibernate 6)

## Execution steps

When invoked, Claude should:

1. **Detect target files**
   - No args: run `git diff --name-only HEAD -- '*.java'` to find changed files
   - Path arg: use the given path
   - `--all`: glob `src/main/java/**/*.java`

2. **Read each file** and identify documentation gaps using the rules above

3. **Edit each file** — add JavaDoc and inline comments without changing any logic

4. **Verify** — run `.\mvnw.cmd compile` to confirm zero compilation errors

5. **Report** — list files modified and a one-line summary of what was added per file
