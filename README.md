# Chat App

Production-ready real-time chat backend — Java 21 + Spring Boot 3.5.x.

## Stack

- **Backend:** Spring Boot 3.5, Spring Security, Spring Data JPA
- **Realtime:** WebSocket/STOMP + SockJS, Redis Pub/Sub (multi-instance fanout)
- **Persistence:** MySQL 8.0 (Flyway migrations), Redis 7
- **Auth:** JWT (access 15 min + refresh 7 days with rotation)
- **Build:** Maven

## Prerequisites

- Java 21
- Docker & Docker Compose

## Local Development

```powershell
# Start MySQL + Redis
docker-compose up mysql redis -d

# Run app (dev profile — connects to localhost)
.\mvnw.cmd spring-boot:run
```

App runs at `http://localhost:8080` (local) or `http://localhost:9090` (Docker). Open in browser for the chat UI.

## Full Docker Stack

```powershell
.\mvnw.cmd clean package -DskipTests
docker-compose up
```

## Docker Hub

```powershell
# Login
docker login

# Build image
.\mvnw.cmd clean package -DskipTests
docker build -t chat-app:latest .

# Tag for Docker Hub
docker tag chat-app:latest <dockerhub-username>/chat-app:latest

# Push
docker push <dockerhub-username>/chat-app:latest

# View local images
docker images
```

## Key Endpoints

| Method | Path | Auth |
|--------|------|------|
| POST | `/api/auth/register` | Public |
| POST | `/api/auth/login` | Public |
| POST | `/api/auth/refresh` | Public |
| POST | `/api/auth/logout` | Bearer |
| GET/POST | `/api/rooms` | Bearer |
| GET | `/api/rooms/my` | Bearer |
| POST | `/api/rooms/{id}/join` | Bearer |
| POST | `/api/rooms/{id}/invite` | Bearer (ADMIN) |
| GET/POST | `/api/rooms/{id}/messages` | Bearer |
| GET/PUT | `/api/users/me` | Bearer |
| WS | `/ws/chat` (SockJS) | STOMP CONNECT header |

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `JWT_SECRET` | (changeme in yml) | Min 32-char secret |
| `SPRING_PROFILES_ACTIVE` | — | Set to `docker` in containers |

## Architecture

WebSocket message flow:
1. Client → STOMP `/app/chat.send`
2. `MessageService` saves to MySQL, publishes JSON to Redis `chat:messages`
3. `RedisMessageSubscriber` on every instance → `SimpMessagingTemplate` → `/topic/rooms/{id}`
4. All subscribers receive the message (works across N instances)
