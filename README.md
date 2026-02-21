# Navio-Server

> **The backend ecosystem for Navio** — a microservices platform for intelligent trip planning, EV charging intelligence, community sharing, and AI-assisted itinerary generation.

Navio-Server is the umbrella repository that houses all backend microservices as Git submodules. Each service runs as an independent Spring Boot 3 application on a dedicated port, communicating via Apache Kafka events and internal REST APIs.

---

## Architecture Overview

```
  NGINX (port 80)
    │
    ├── /v1/trips, /v1/share, /v1/media, /v1/me, /v1/mod  →  Trip & Media Service (8081)
    ├── /v1/ev                                              →  EV Intelligence Service (8082)
    ├── /v1/posts, /v1/feed, /v1/search, /v1/notifications  →  Community Service (8083)
    └── /v1/ai                                              →  AI Orchestrator (8084)
```

---

## Service Inventory

| Service                     | Port | Submodule Dir              | Postgres Schemas       | Status      |
| --------------------------- | ---- | -------------------------- | ---------------------- | ----------- |
| **Trip & Media Service**    | 8081 | `trip-media-service/`      | `trip`, `iam`, `media` | In progress |
| **EV Intelligence Service** | 8082 | `ev-intelligence-service/` | `ev`                   | Planned     |
| **Community Service**       | 8083 | `community-service/`       | `social`, `notif`      | Planned     |
| **AI Orchestrator**         | 8084 | `ai-orchestrator/`         | `ai`                   | Planned     |

---

## IAM (Identity & Access Management)

IAM is a **module embedded within the Trip & Media Service** (port 8081), not a standalone service. It is the backbone of authentication and authorization across the entire platform.

### Why IAM Lives in Trip & Media Service

- Trip access control (owner/editor/viewer) is the most latency-sensitive ACL check in the system. Co-locating IAM in the same JVM eliminates network overhead — ACL checks are direct Java method calls.
- IAM owns the `iam` Postgres schema exclusively. No other service reads from or writes to this schema.

### Responsibilities

| Capability            | Description                                                                                                                                 |
| --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| **User Profile Sync** | Mirrors Keycloak user data (`sub`, `email`, `display_name`) into `iam.users` on first login                                                 |
| **Roles**             | Three-tier role model: `user`, `mod`, `admin` — stored in `iam.users.role`                                                                  |
| **Ban / Unban**       | Moderators and admins can ban/unban users; ban history tracked in `iam.bans`                                                                |
| **ACL Engine**        | Generic resource-level access control via `iam.acl_entries` (permission: `owner`, `editor`, `viewer`)                                       |
| **Audit Logging**     | Append-only `iam.audit_log` records all security-sensitive actions (bans, ACL changes, visibility changes, moderation deletions)            |
| **Event Publishing**  | Publishes `UserRegistered.v1`, `UserProfileUpdated.v1`, `UserBanned.v1`, `UserUnbanned.v1` to Kafka via Transactional Outbox (`iam.outbox`) |

### IAM Database Schema (`iam`)

| Table             | Purpose                                                                            |
| ----------------- | ---------------------------------------------------------------------------------- |
| `iam.users`       | Keycloak profile mirror with app-specific fields (role, status, bio, preferences)  |
| `iam.bans`        | Ban/unban history; active ban = latest record with `unbanned_at IS NULL`           |
| `iam.acl_entries` | Resource-level permissions (one permission per user per resource, upsert on grant) |
| `iam.audit_log`   | Append-only security audit trail                                                   |
| `iam.outbox`      | Transactional Outbox for IAM domain events → Kafka `iam.events.v1`                 |

### IAM Endpoints

**Public (through NGINX):**

| Method | Path                           | Description             | Auth Required |
| ------ | ------------------------------ | ----------------------- | ------------- |
| GET    | `/v1/me`                       | Current user profile    | Yes (JWT)     |
| PATCH  | `/v1/me/preferences`           | Update user preferences | Yes (JWT)     |
| POST   | `/v1/mod/users/{userId}/ban`   | Ban a user              | Mod/Admin     |
| POST   | `/v1/mod/users/{userId}/unban` | Unban a user            | Mod/Admin     |

**Internal (service-to-service, localhost only):**

| Method | Path                          | Description                         |
| ------ | ----------------------------- | ----------------------------------- |
| POST   | `/internal/v1/users/sync`     | Upsert user profile from JWT claims |
| GET    | `/internal/v1/users/{userId}` | Get user profile                    |
| POST   | `/internal/v1/acl/check`      | Check permission on a resource      |
| POST   | `/internal/v1/acl/grant`      | Grant permission                    |
| DELETE | `/internal/v1/acl/revoke`     | Revoke permission                   |
| GET    | `/internal/v1/acl/list`       | List permissions on a resource      |

### How Other Services Use IAM

| Service             | Interaction                                                                   |
| ------------------- | ----------------------------------------------------------------------------- |
| **Trip Engine**     | In-process Java method calls for ACL checks (same JVM) — no HTTP overhead     |
| **Community**       | Consumes `iam.events.v1` Kafka topic for author name sync and ban enforcement |
| **EV Intelligence** | No direct IAM dependency — JWT validation handled by Spring Security          |
| **AI Orchestrator** | No direct IAM dependency — JWT validation handled by Spring Security          |

---

## Authentication Flow

1. User signs up / signs in via **Keycloak** (OIDC)
2. Frontend receives a JWT containing `sub` (userId), `email`, `name`
3. Frontend sends JWT in `Authorization: Bearer` header to NGINX
4. NGINX routes to the appropriate service (no JWT validation at proxy level)
5. Each Spring Boot service validates the JWT via Keycloak's JWKS endpoint (`spring-security-oauth2-resource-server`)
6. On first authenticated request, Trip & Media Service syncs user profile to `iam.users` and publishes `UserRegistered.v1`

---

## Cross-Service Communication

| Pattern                   | Mechanism                       | Example                                            |
| ------------------------- | ------------------------------- | -------------------------------------------------- |
| **Async events**          | Kafka via Transactional Outbox  | `UserBanned.v1` → Community Service enforces ban   |
| **Sync request/response** | Java HttpClient (internal REST) | Community → Trip for search, AI → Trip for context |
| **In-process calls**      | Direct Java method invocation   | Trip Engine → IAM ACL checks (same JVM)            |

Internal endpoints are secured with a shared secret header (`X-Internal-Auth`). All traffic stays on `localhost`.

---

## Tech Stack

| Layer            | Technology                      |
| ---------------- | ------------------------------- |
| Runtime          | Java 21+ / Spring Boot 3        |
| Auth             | Keycloak (OIDC/JWT)             |
| Database         | PostgreSQL 16 + PostGIS         |
| Messaging        | Apache Kafka (single broker)    |
| Caching          | Caffeine (in-process JVM)       |
| Search           | Postgres `tsvector` + `pg_trgm` |
| Migrations       | Flyway                          |
| Resilience       | Resilience4j                    |
| Config           | Spring Cloud Config Server      |
| Object Storage   | MinIO / local filesystem        |
| Event Publishing | Transactional Outbox pattern    |

---

## Getting Started

```bash
# Clone with submodules
git clone --recurse-submodules https://github.com/bestprappy/Navio-Server.git

# Or initialize after cloning
git submodule update --init --recursive
```

Each service is a standard Maven project:

```bash
cd trip-media-service
./mvnw clean package -DskipTests
java -Xmx256m -jar target/*.jar
```

---

## Related Documentation

- [System Design (Summary)](../docs/Summary.md)
- [Database Design](../docs/Database.md)
- [Implementation Guide](../docs/Implementation.md)
