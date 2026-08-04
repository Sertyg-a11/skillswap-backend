# SkillSwap Backend

Microservices backend for SkillSwap - a chat-first mentorship platform.

## Architecture

```
                                 ┌─────────────────┐
                                 │    Frontend     │
                                 │  (React/Vite)   │
                                 └────────┬────────┘
                                          │
                                          ▼
┌──────────────────────────────────────────────────────────────────┐
│                      API GATEWAY (:8081)                         │
│                   Spring Cloud Gateway + OAuth2                  │
│  Routes:                                                         │
│    /api/users/**  → user-service     /api/gdpr/** → orchestrated │
│    /api/skills/** → user-service     /ws/**       → message-svc  │
│    /api/messages/** → message-service                            │
│    /api/conversations/** → message-service                       │
└──────────────────────────────────────────────────────────────────┘
              │                                    │
              ▼                                    ▼
┌─────────────────────────┐          ┌─────────────────────────┐
│   USER SERVICE (:8082)  │          │ MESSAGE SERVICE (:8083) │
│   - User profiles       │◄────────►│   - Conversations       │
│   - Skills management   │ RabbitMQ │   - Real-time messages  │
│   - GDPR compliance     │  Events  │   - WebSocket/STOMP     │
│   - Privacy events      │          │   - Read receipts       │
└───────────┬─────────────┘          └───────────┬─────────────┘
            │                                    │
            ▼                                    ▼
      ┌──────────┐                         ┌──────────┐
      │ User DB  │                         │ Msg DB   │
      │ (Neon)   │                         │ (Neon)   │
      └──────────┘                         └──────────┘
```

## Services

### API Gateway (`api-gateway/`)
- **Port:** 8081
- **Purpose:** Single entry point, routing, JWT validation, CORS
- **Tech:** Spring Cloud Gateway, OAuth2 Resource Server
- **Key Features:**
  - Route requests to appropriate microservices
  - Validate JWT tokens via Keycloak
  - GDPR orchestration (coordinates data export/deletion across services)

### User Service (`user-service/`)
- **Port:** 8082
- **Purpose:** User management, profiles, skills
- **Tech:** Spring Boot, JPA, PostgreSQL
- **Key Features:**
  - User profile CRUD operations
  - Skills management (add/remove/list)
  - User search with input sanitization
  - Rate limiting (Bucket4j + Redis)
  - GDPR data export and deletion
  - Privacy event auditing

### Message Service (`message-service/`)
- **Port:** 8083
- **Purpose:** Real-time messaging, conversations
- **Tech:** Spring Boot, WebSocket/STOMP, JPA
- **Key Features:**
  - Conversation management
  - Real-time message delivery via WebSocket
  - Unread message tracking (Redis)
  - Read receipts
  - Message sanitization (XSS prevention)
  - GDPR data export and deletion

## Communication Patterns

### Synchronous (REST)
- Frontend → API Gateway → Services
- Message Service → User Service (user lookup via `InternalUserServiceClient`)

### Asynchronous (RabbitMQ)
- **GDPR Events:**
  - `gdpr.export.user-service` / `gdpr.export.message-service` - Export requests
  - `gdpr.deletion.user-service` / `gdpr.deletion.message-service` - Deletion requests
  - `gdpr.export.response.gateway` - Export responses back to gateway

### WebSocket (STOMP)
- **Endpoint:** `/ws` (SockJS) or `/ws` (native WebSocket)
- **Destinations:**
  - `/user/{userId}/queue/messages` - Receive messages
  - `/app/message.send` - Send messages
- **Authentication:** JWT token in STOMP CONNECT headers

## Local Development

### Prerequisites
- Java 21
- Docker & Docker Compose
- Node.js 22+ (for frontend)

### Quick Start

```bash
# Start infrastructure (PostgreSQL, Redis, RabbitMQ, Keycloak)
docker compose -f docker-compose.dev.yml up -d

# Run services (in separate terminals)
./gradlew :api-gateway:bootRun
./gradlew :user-service:bootRun
./gradlew :message-service:bootRun
```

### Environment Variables

| Variable | Service | Description |
|----------|---------|-------------|
| `KEYCLOAK_ISSUER_URI` | All | Keycloak realm URL |
| `USER_DB_URL` | user-service | PostgreSQL connection URL |
| `SWAP_DB_URL` | message-service | PostgreSQL connection URL |
| `REDIS_HOST` | All | Redis host for caching |
| `RABBIT_HOST` | All | RabbitMQ host |

## API Endpoints

### User Service
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/users/me` | Get current user profile |
| PUT | `/api/users/me/profile` | Update profile |
| GET | `/api/users/search?q=` | Search users |
| GET | `/api/skills/me` | Get user's skills |
| POST | `/api/skills` | Add skill |
| DELETE | `/api/skills/{id}` | Remove skill |

### Message Service
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/conversations` | List user's conversations |
| POST | `/api/messages/to/{userId}` | Send message (creates conversation if needed) |
| GET | `/api/messages/conversation/{id}` | Get messages (paginated) |
| POST | `/api/messages/conversation/{id}/read` | Mark as read |

### GDPR (API Gateway)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/gdpr/export` | Export all user data |
| DELETE | `/api/gdpr/delete?type=FULL\|ANONYMIZE` | Delete/anonymize data |
| GET | `/api/gdpr/info` | Get GDPR information |

## Security

- **Authentication:** OAuth2/OIDC via Keycloak
- **Input Sanitization:** OWASP Java Encoder for XSS prevention
- **Rate Limiting:** Bucket4j with Redis backend
- **HTTPS:** Enforced in production via Traefik

## Testing

```bash
# Run all tests
./gradlew test

# Run specific service tests
./gradlew :message-service:test

# Integration tests (requires Docker)
./gradlew integrationTest
```

Tests use Testcontainers for PostgreSQL, Redis, and RabbitMQ.

## Build

```bash
# Build all services
./gradlew build

# Build Docker images
docker build -t skillswap-api-gateway -f api-gateway/Dockerfile .
docker build -t skillswap-user-service -f user-service/Dockerfile .
docker build -t skillswap-message-service -f message-service/Dockerfile .
```

## Project Structure

```
skillswap-backend/
├── api-gateway/          # API Gateway service
│   └── src/main/java/.../
│       ├── config/       # Security, routing config
│       ├── gdpr/         # GDPR orchestration
│       └── route/        # Route definitions
├── user-service/         # User management service
│   └── src/main/java/.../
│       ├── api/          # REST controllers
│       ├── domain/       # JPA entities
│       ├── gdpr/         # GDPR handlers
│       ├── repository/   # Data access
│       └── service/      # Business logic
├── message-service/      # Messaging service
│   └── src/main/java/.../
│       ├── api/          # REST controllers
│       ├── config/       # WebSocket config
│       ├── domain/       # JPA entities
│       ├── gdpr/         # GDPR handlers
│       └── service/      # Business logic
├── build.gradle.kts      # Root build config
└── docker-compose.dev.yml # Local dev environment
```
