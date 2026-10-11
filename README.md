# FAF Message Broker

The **FAF Message Broker** is a high-performance gateway and message routing service for the [Forged Alliance Forever (FAF)](https://faforever.com/) platform. 
The service iis built to be the central handler of all real-time messaging between clients and backend services.

Built on Quarkus and Java 25 with virtual threads, it bridges real-time client WebSocket connections with backend RabbitMQ messaging infrastructure, enforcing JSON schema validation and JWT authentication.

---

## Table of Contents

- [Overview & Architecture](#overview--architecture)
- [Key Features](#key-features)
- [Tech Stack](#tech-stack)
- [Messaging & Communication Flow](#messaging--communication-flow)
  - [WebSocket Endpoints](#websocket-endpoints)
  - [RabbitMQ Channels](#rabbitmq-channels)
- [Configuration Reference](#configuration-reference)
- [Database & Migrations](#database--migrations)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Running in Development Mode](#running-in-development-mode)
  - [Building & Testing](#building--testing)
  - [Docker & Container Images](#docker--container-images)
- [Related Guides](#related-guides)

---

## Overview & Architecture

`faf-message-broker` acts as the edge communication broker between FAF game/lobby clients and backend server microservices:

1. **Client Connectivity**: Manages client lifecycle, session establishment, user-agent detection, and keepalive pings over WebSockets.
2. **Authentication & Policy Validation**: Verifies client JWT tokens against Ory Hydra JWKS and optionally checks client connections against a policy server.
3. **Dynamic Schema Validation**: Enforces strict JSON Schema (Draft 2020-12) validation for all inbound lobby commands and GPG game passthrough messages before dispatching them downstream.
4. **Targeted Message Distribution**: Delivers outbound messages from backend services to individual sessions, specific players across sessions, or broadcast to all active connections.

```
+--------------------+        WebSocket (Port 8003)        +------------------------+
|  FAF Client / Game | <=================================> |   faf-message-broker   |
+--------------------+                                     +------------------------+
                                                               |         |         |
                          +------------------------------------+         |         +-----------------------+
                          | (JWT & Policy Verification)                  | (Schema Persistence)            | (Event Brokering)
                          v                                              v                                 v
                 +-------------------+                          +-----------------+               +-----------------+
                 | Hydra / Policy API|                          | PostgreSQL (DB) |               | RabbitMQ Broker |
                 +-------------------+                          +-----------------+               +-----------------+
```

---

## Key Features

- **Virtual Thread Concurrency**: Utilizes Java 25 virtual threads (`@RunOnVirtualThread`) for scalable connection handling.
- **Bi-directional Passthrough & Routing**:
  - Inbound lobby and GPG (`target: game`) messages validated and published to RabbitMQ.
  - Outbound messages routed by target criteria: `Session(sessionId)`, `Player(playerId)`, or `All()`.
- **Dynamic & Persisted Schema Registry**:
  - Automatically compiles and registers schema definitions on startup.
  - Dynamically registers new schemas via RabbitMQ (`lobby-schema-registration`).
  - Persists schemas in PostgreSQL (`lobby_command_schema` table) via Flyway migrations.
- **Zero-Downtime Session Management**: Thread-safe concurrent session maps tracking player-to-session relationships.
- **Strict Code Quality & Null Safety**: Enforced with Error Prone and NullAway in JSpecify mode.

---

## Tech Stack

- **Java**: 25
- **Framework**: [Quarkus 4.x](https://quarkus.io/)
- **Messaging**: SmallRye Reactive Messaging ([RabbitMQ Connector](https://quarkus.io/guides/rabbitmq))
- **WebSockets**: [Quarkus WebSockets Next](https://quarkus.io/guides/websockets-next-reference)
- **Database & Persistence**: PostgreSQL, Hibernate ORM with Panache, Flyway
- **Authentication**: SmallRye JWT with Ory Hydra JWKS validation
- **Schema Validation**: NetworkNT JSON Schema Validator & Victools JSON Schema Generator
- **Build System**: Gradle (Kotlin DSL)

---

## Messaging & Communication Flow

### WebSocket Endpoints

- **Path**: `/` (Default port: `8003`)
- **Connection Handshake**:
  1. `ask_session` (`Message.Connection.SessionRequest`): Client sends agent and version, receives a generated `session` ID (`Message.Connection.SessionResponse`).
  2. `auth` (`Message.Connection.AuthenticateRequest`): Client submits JWT token and unique machine ID. Token is parsed to obtain `playerId`, verified with Hydra, and checked against the policy server (if enabled).
  3. `ping` / `pong`: Bi-directional heartbeat keepalive messages.
  4. **Passthrough Messages**: Inbound commands (`Lobby` or `GPG`) routed after authentication.

### RabbitMQ Channels

| Channel | Direction | VHost | Description |
| :--- | :--- | :--- | :--- |
| `lobby-inbound` | Outgoing (Broker &rarr; RabbitMQ) | `/faf-core` | Transmits validated inbound client messages with routing keys matching command names or `gpg`. |
| `lobby-outbound` | Incoming (RabbitMQ &rarr; Broker) | `/faf-core` | Receives outbound messages targeted to a session, player, or all players. |
| `lobby-schema-registration` | Incoming (RabbitMQ &rarr; Broker) | `/faf-core` | Receives runtime schema registrations to dynamically update validation rules. |

---

## Configuration Reference

The application is configured via `src/main/resources/application.yml` and supports the following environment variables:

| Variable | Description | Default |
| :--- | :--- | :--- |
| `DATABASE_HOST` | PostgreSQL host | `localhost` |
| `DATABASE_PORT` | PostgreSQL port | `5432` |
| `DATABASE_NAME` | Database name | `faf-messages` |
| `DATABASE_USER` | Database user | `faf-message-broker` |
| `DATABASE_PASS` | Database password | `banana` |
| `RABBITMQ_HOST` | RabbitMQ host | `localhost` |
| `RABBITMQ_PORT` | RabbitMQ port | `5672` |
| `RABBITMQ_USER` | RabbitMQ user | `faf-message-broker` |
| `RABBITMQ_PASSWORD` | RabbitMQ password | `banana` |
| `RABBITMQ_VHOST` | RabbitMQ virtual host | `/faf-core` |
| `ALLOWED_HOST` | Allowed HTTP/WebSocket host header validation | `ws.faforever.localhost,localhost` |
| `HYDRA_TOKEN_ISSUER` | Expected JWT issuer | `http://ory-hydra:4444` |
| `HYDRA_JWKS_URL` | Ory Hydra JWKS public key endpoint | `http://localhost:4444/.well-known/jwks.json` |
| `USE_POLICY_SERVER` | Enable or disable verification with policy server | `false` |
| `POLICY_SERVER_URL` | Base URL of the policy verification service | `http://localhost:8097` |

---

## Database & Migrations

The service uses PostgreSQL for storing registered command schemas. Flyway runs automatically on startup:

- **`V1__lobby_command_schema.sql`**: Creates `lobby_command_schema` table:
  - `command` (`VARCHAR`): Command identifier.
  - `direction` (`VARCHAR`): `inbound` or `outbound`.
  - `schema` (`JSONB`): JSON Schema Draft 2020-12 specification.

---

## Getting Started

### Prerequisites

- **Java JDK**: 25 or higher
- **PostgreSQL**: 14+ (or compatible container)
- **RabbitMQ**: 3.9+ with `/faf-core` virtual host configured
- **Ory Hydra**: (for JWT authentication verification)

For local development and orchestrating all supporting backend services (PostgreSQL, RabbitMQ, Hydra, etc.), you can use the [FAForever gitops-stack](https://github.com/FAForever/gitops-stack) Tilt setup.

### Running in Development Mode

The dev config is setup to run as a local service using [Tilt via gitops-stack](https://github.com/FAForever/gitops-stack)

Run the application in Quarkus development mode with live reload:

```bash
./gradlew quarkusDev
```
*(On Windows PowerShell: `.\gradlew.bat quarkusDev`)*

In dev mode, traffic logging for WebSockets and SQL statement logging are enabled by default.

### Building & Testing

To execute unit and integration tests:

```bash
./gradlew test
```

To build the executable application package:

```bash
./gradlew build
```

### Docker & Container Images

Dockerfiles for various deployment models are available under `src/main/docker/`:

- `Dockerfile.jvm`: Standard OpenJDK container image.
- `Dockerfile.legacy-jar`: Thin jar packaging.
- `Dockerfile.native`: Native binary container image.
- `Dockerfile.native-micro`: Minimalist micro image for native executable.

---

## Related Guides

- [Quarkus RabbitMQ Connector Guide](https://quarkus.io/guides/rabbitmq): Reactive messaging with RabbitMQ.
- [Quarkus WebSockets Next Reference](https://quarkus.io/guides/websockets-next-reference): WebSocket API documentation.
- [Quarkus YAML Configuration](https://quarkus.io/guides/config-yaml): YAML configuration in Quarkus.
- [Quarkus Datasource / PostgreSQL Guide](https://quarkus.io/guides/datasource): Connecting to PostgreSQL via JDBC.
- [Hibernate ORM with Panache](https://quarkus.io/guides/hibernate-orm-panache): Persistence code simplified for Hibernate ORM.
