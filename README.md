# Navio Server

Navio Server documents up to five domain Spring Boot services plus three mandatory Spring platform applications: API Gateway, Configuration Server, and Discovery Server. Four core domain services run on the application VM. When enabled, AI Planning uses Spring AI and can run beside Ollama on the ML VM or on the application VM with a hosted model provider.

Keycloak and NGINX remain non-Spring platform components. NGINX is the production edge in front of Spring Cloud Gateway; it never replaces the gateway.

## Service inventory

| Service | Port | Directory | Owned schemas | Deployment |
| --- | ---: | --- | --- | --- |
| User Management Service | 8081 | `user-management-service/` | `iam` | Application VM |
| Trip Planning Service | 8082 | `trip-planning-service/` | `trip` | Application VM |
| Mobility & EV Service | 8083 | `mobility-service/` | `ev` | Application VM |
| Community Service | 8084 | `community-service/` | `social`, `notif`, `media` | Application VM |
| AI Planning Service | 8085 | `ai-planning-service/` | `ai` | ML VM with Ollama, or application VM with hosted provider |

Mandatory Spring platform projects:

| Application | Port | Directory | Responsibility |
| --- | ---: | --- | --- |
| API Gateway | 8080 | `platform/api-gateway/` | Shared routing and Eureka-aware load balancing |
| Configuration Server | 8888 | `platform/configuration-server/` | Versioned non-secret configuration from private Git |
| Discovery Server | 8761 | `platform/discovery-server/` | Eureka service registration and discovery |

## Gateway routing

```text
Development: Client ──> Spring Cloud Gateway :8080
Production:  Client ──> NGINX :80/:443 ──> Spring Cloud Gateway :8080

Spring Cloud Gateway + Eureka
  ├── /v1/users, /v1/admin/users                              → USER-MANAGEMENT-SERVICE :8081
  ├── /v1/trips, /v1/public-trips, /v1/share                  → TRIP-PLANNING-SERVICE :8082
  ├── /v1/places, /v1/routes, /v1/geo, /v1/ev, /v1/chargers  → MOBILITY-SERVICE :8083
  ├── /v1/community, /v1/groups, /v1/posts,
  │   /v1/feed, /v1/notifications, /v1/media                   → COMMUNITY-SERVICE :8084
  └── /v1/ai                                                   → AI-PLANNING-SERVICE :8085
```

Only NGINX is public in production. Gateway, Config Server, Eureka, Actuator, telemetry, and domain-service ports are reachable only from the private network.

## Platform bootstrap

1. Start PostgreSQL, Kafka, Keycloak, and required networking.
2. Start Configuration Server at its fixed private address; it does not depend on Eureka.
3. Start the single-node Eureka Discovery Server using configuration from Config Server.
4. Start domain services; they load configuration and register with Eureka.
5. Start API Gateway; it loads explicit routes and resolves services through Eureka.
6. Start NGINX for production ingress and verify telemetry delivery.

The Config Git repository contains non-secret settings only. Database passwords, Keycloak credentials, provider tokens, and cryptographic material remain environment or mounted secrets.

## Authentication and user management

Keycloak is the Identity Provider and authoritative source for:

- Registration, login, logout, password reset, and email verification
- OIDC sessions, access tokens, and refresh tokens
- Global `USER`, `MODERATOR`, and `ADMIN` roles
- Global account enablement/disablement

User Management Service is the Navio application-data and administration layer:

- Mirrors the Keycloak subject into `iam.users`
- Stores profile, preferences, privacy settings, and saved vehicles
- Exposes administrator user-search, role-change, suspend, and reactivate APIs
- Changes global roles through the Keycloak Admin API
- Stores audit history and publishes user lifecycle events

User Management never stores credentials or issues JWTs. Each Spring service validates Keycloak JWTs using Spring Security OAuth2 Resource Server.

## Authorization ownership

| Permission | Authority |
| --- | --- |
| Global `USER`, `MODERATOR`, `ADMIN` role | Keycloak, managed through User Management |
| User suspension and administrative audit | User Management + Keycloak |
| Trip owner/editor/viewer | Trip Planning |
| Group owner/moderator/member | Community |
| Post/comment author permissions | Community |
| Charger review/submission permissions | Mobility & EV |
| AI quota and model access | AI Planning |

Do not create a Keycloak role per trip or community group. Resource permissions belong to the owning domain service.

## Cross-service communication

Use synchronous REST when the caller needs an immediate answer:

- Community validates an attached public trip through Trip Planning.
- Trip Planning requests place, route, charger, and EV calculations from Mobility & EV.
- AI Planning loads trip context and invokes mobility tools through internal APIs.
- User Management calls the Keycloak Admin API for global role changes.

Use Kafka only for asynchronous integration events:

| Topic | Producer | Consumers |
| --- | --- | --- |
| `user.events.v1` | User Management | Trip Planning, Community, Mobility & EV |
| `trip.events.v1` | Trip Planning | Community notification and trip-snapshot consumers |
| `community.events.v1` | Community | Notification/audit modules |
| `mobility.events.v1` | Mobility & EV | Community/admin consumers |

Each producer uses a transactional outbox. Each consumer deduplicates by `eventId`. The development/production baseline uses a single KRaft broker; Zookeeper and Schema Registry are not part of the final architecture.

## AI deployment

```text
Application VM                        Machine-learning VM
-------------------------------       ------------------------------
NGINX -> API Gateway /v1/ai/** ------> AI Planning Service :8085
Trip Planning <---------------------- agent tool calls
Mobility & EV <---------------------- agent tool calls
PostgreSQL ai schema <--------------- AI-owned persistence
                                      Ollama :11434 (localhost only)
```

AI Planning owns prompts, sessions, quotas, tool orchestration, validation, and SSE streaming. Spring AI's portable chat/streaming abstraction isolates domain code from the provider. Ollama is the inference engine only in the self-hosted profile. In the hosted profile, Spring AI calls the configured provider over HTTPS and the ML VM is not required. Model output is converted to schema-validated proposed actions; Trip Planning applies changes only after explicit user confirmation.

## Deployment targets

### Application VM

- Ubuntu Server 24.04
- 4 vCPU, 8 GB RAM, 60 GB storage
- NGINX, Next.js, Keycloak, PostgreSQL/PostGIS, Kafka, and Grafana Alloy
- User Management, Trip Planning, Mobility & EV, and Community
- API Gateway, Configuration Server, and Discovery Server
- Explicit JVM/container memory limits; this is a tight capstone profile

### Machine-learning VM

- Self-hosted AI profile only
- Ubuntu Server 24.04
- 8 vCPU, 8 GB RAM, 80 GB storage, no GPU
- AI Planning Service and Ollama
- One small quantized model and one active generation initially
- Grafana Alloy exports AI/Ollama telemetry to the centralized backend

### Hosted-provider AI profile

- AI Planning runs on the application VM
- Spring AI calls a configured hosted model API
- The ML VM and Ollama are omitted
- The public API, tool schemas, authorization, session persistence, and confirmation workflow remain unchanged
- The application VM should be upgraded to 16 GB before sustained production use

No Redis, Elasticsearch, MinIO, or standalone Notification Service is required for v1.

## Observability

- All eight possible Spring projects include Actuator, Micrometer, structured JSON logging, and OpenTelemetry trace propagation.
- Grafana Alloy collects application/container logs, metrics, and sampled traces on each active VM.
- The current 8 GB profile exports telemetry to a hosted centralized backend and Grafana UI.
- Request and trace context flows through NGINX, Gateway, REST, Kafka, and AI tool calls.
- Logs redact credentials, tokens, cookies, hosted-model keys, and unnecessary personal or prompt data.
- Self-hosting Loki, Prometheus, Tempo, and Grafana requires a separate observability VM or at least 16 GB on the application server.

## Service rules

1. A service may read and write only its owned schemas.
2. No cross-schema foreign keys or joins.
3. Keycloak is the global-role authority; local role values are display/audit snapshots only.
4. Every externally supplied identifier is re-authorized by the owning service.
5. AI never receives database credentials for another service's schema.
6. Ollama is not exposed outside the ML host.
7. Kafka is not used for synchronous commands or queries.
8. All privileged changes are audited.
9. Spring Cloud Gateway is used in development and production; NGINX is production edge only.
10. Configuration Server contains non-secret configuration only and starts without Eureka.
11. Gateway and domain services register with the private Eureka server.
12. Telemetry delivery failures never block domain requests.

## Local development

Each domain or platform application is a Java 21+ Spring Boot 3 project. Local clients call API Gateway on port 8080. The root Compose file is still a legacy bootstrap and must be extended with Config Server, Eureka, Gateway, and Alloy before it represents the target development topology.

```bash
cd user-management-service
./mvnw clean package -DskipTests
java -jar target/*.jar
```

Repeat for the other service directories as they are created.
