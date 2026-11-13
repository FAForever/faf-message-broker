# FAF Message Broker

The **FAF Message Broker** is a high-performance gateway and message routing service for
the [Forged Alliance Forever (FAF)](https://faforever.com/) platform.
The service is built to be the central handler of all messaging between clients and backend services.

Built on Quarkus and Java 25 with virtual threads, it bridges real-time client WebSocket connections with backend
RabbitMQ messaging infrastructure, enforcing JSON schema validation and JWT authentication.

---

## Table of Contents

- [Overview & Architecture](#overview--architecture)
- [Key Features](#key-features)
- [Tech Stack](#tech-stack)
- [Messaging & Communication Flow](#messaging--communication-flow)
    - [WebSocket Endpoints & Session Message Queuing](#websocket-endpoints--session-message-queuing)
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

`faf-message-broker` acts as the edge communication broker between FAF game/lobby clients and backend server
microservices:

1. **Client Connectivity**: Manages client lifecycle, session establishment, user-agent detection, and keepalive pings
   over WebSockets.
2. **Authentication & Policy Reporting**: Verifies client JWT tokens against Ory Hydra JWKS (requiring the `lobby`
   scope) and optionally reports connection details to a policy server.
3. **Dynamic Schema Validation & Error Reporting**: Enforces strict JSON Schema (Draft 2020-12) validation for all
   inbound lobby commands and GPG game passthrough messages before dispatching them downstream. Invalid messages or
   unregistered commands return an immediate `failed_message` response to the client.
4. **Active Player & Session Tracking**: Maintains player-to-session mappings, emits periodic session heartbeats, and
   answers active player queries from backend microservices via RabbitMQ.
5. **Targeted Message Distribution**: Delivers outbound messages from backend services to individual sessions, specific
   players across sessions, or broadcast to all active connections.

```
+--------------------+        WebSocket (Port 8080)        +------------------------+
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

- **Virtual Thread Concurrency**: Utilizes Java 25 virtual threads (`@RunOnVirtualThread`) for scalable connection
  handling and asynchronous message draining.
- **Asynchronous Session Message Queues**:
    - Bounded per-session incoming and outgoing queues, each backed by a single-threaded executor that drains them
      asynchronously on a virtual thread.
    - Prevents blocking on slow network clients or broker dispatch and tracks queue metrics and dropped messages.
- **Bi-directional Passthrough & Routing**:
    - Inbound lobby and GPG (`target: game`) messages validated and published to RabbitMQ.
    - Inbound messages failing validation or lacking registered schemas trigger a structured `failed_message` client
      response.
    - Outbound messages routed by target criteria: `Session(sessionId)`, `Player(playerId)`, or `All()`.
    - An outbound `notice` message with `style: kick` closes the receiving session after it is sent.
- **Dynamic & Persisted Schema Registry**:
    - On startup, loads previously persisted schemas from PostgreSQL, then requests schemas from backend services via
      RabbitMQ (`lobby-schema-request`).
    - Generates and registers schemas for the broker's own connection protocol messages (route `connection`) on startup.
    - Dynamically registers new schemas via RabbitMQ (`lobby-schema-registration`).
    - Persists schemas in PostgreSQL (`lobby_command_schema` table, created by Flyway migrations).
- **Player Session & State Tracking**:
    - Emits real-time connection lifecycle events (`new`, `existing`, `gone`) with TTL expiry to RabbitMQ
      (`player-connection`).
    - Responds to on-demand active player session requests via RabbitMQ (`player-active-request`) using RPC reply-to
      routing.
- **Zero-Downtime Session Management**: Thread-safe concurrent session maps tracking player-to-session relationships.
- **Metrics & Health**: Micrometer metrics exported for Prometheus (sessions, players, queued/dropped messages, user
  agents, message timings) and SmallRye Health checks, served on the Quarkus management interface.
- **Strict Code Quality & Null Safety**: Enforced with Error Prone and NullAway in JSpecify mode.

---

## Tech Stack

- **Java**: 25
- **Framework**: [Quarkus 4.x](https://quarkus.io/)
- **Messaging**: SmallRye Reactive Messaging ([RabbitMQ Connector](https://quarkus.io/guides/rabbitmq))
- **WebSockets**: [Quarkus WebSockets Next](https://quarkus.io/guides/websockets-next-reference)
- **Serialization**: Jackson 3
- **Database & Persistence**: PostgreSQL, Hibernate ORM with Jakarta Data repositories, Flyway
- **Authentication**: SmallRye JWT with Ory Hydra JWKS validation
- **Schema Validation**: NetworkNT JSON Schema Validator & Victools JSON Schema Generator
- **Observability**: Micrometer (Prometheus registry), SmallRye Health
- **Boilerplate Reduction**: Lombok
- **Build System**: Gradle (Kotlin DSL)

---

## Messaging & Communication Flow

### WebSocket Endpoints & Session Message Queuing

- **Path**: `/` (Quarkus default HTTP port: `8080`)
- **Framing**: Messages are JSON objects with snake_case properties; every outgoing message is terminated with a newline.
- **Connection Handshake & Lifecycle**:
    1. `ask_session` (`ConnectionMessage.SessionRequest`): Client sends `user_agent` and `version`, receives a generated
       `session` ID (`ConnectionMessage.SessionResponse`).
    2. `auth` (`ConnectionMessage.AuthenticateRequest`): Client submits a JWT `token` and `unique_id`. The token is
       verified against the Hydra JWKS, must contain the `lobby` scope (`scp` claim), and its subject is used as the
       `playerId`. If the policy server is enabled, the session ID, player ID and unique ID are posted to its `/verify`
       endpoint (failures are logged and do not block login). On failure, or if the session is already authenticated,
       the broker sends `authentication_failed` (`ConnectionMessage.LoginFailureResponse`) and closes the connection.
    3. `ping` / `pong`: The broker sends a `ping` to every authenticated session every `faf.ping-interval` (default
       `1m`) and answers client `ping` messages with a `pong` carrying the same `request_id`.
    4. **Passthrough Messages**: Any other command is treated as a passthrough message: `GPG` if it has
       `target: game`, otherwise `Lobby` (a `request_id` is generated if absent). Passthrough messages received before
       authentication are queued and only dispatched once authentication succeeds. Lobby messages are validated against
       their registered schema and published with routing key `lobby.<route>.<command>`; GPG messages are validated
       against the built-in game passthrough schema and published with routing key `gpg.<command>`.
    5. **Validation Error Reporting (`failed_message`)**: If an inbound message fails schema validation or specifies an
       unknown command (and `faf.publish-unknown-messages` is disabled), the broker rejects the message and sends a
       `failed_message` (`FailedMessage`) directly back to the client session over WebSocket:
       ```json
       {
         "command": "failed_message",
         "request_id": "<request_id_from_client_or_null>",
         "message": "Invalid message received",
         "message_key": "message.invalid"
       }
       ```
       Registered lobby schemas dynamically include an optional `request_id` property, allowing clients to correlate
       validation error responses with specific outbound requests.
- **Session Message Queuing & Draining**:
    - **Inbound Queue**: Incoming passthrough messages are submitted to a per-session single-threaded executor
      (`incomingExecutor`) backed by a bounded queue. Its virtual thread waits until the session is authenticated, then
      forwards each message to `MessageBroker`.
    - **Outbound Queue**: Outgoing protocol messages sent to the session are submitted to a separate executor
      (`outgoingExecutor`) and written to the WebSocket connection on its own virtual thread.
    - **Backpressure & Capacity Limits**: The queue capacity is configured via `faf.max-message-queue-size` (default:
      `100`). If a queue is full, new messages are dropped and tracked via the `faf.messages.dropped` counter.
    - **Session Teardown**: When a session closes, it is unregistered and both executors stop accepting messages.
      Already-queued messages are drained (waiting up to one minute per queue) before the WebSocket is closed; queued
      inbound messages of a session that never authenticated are discarded.

### RabbitMQ Channels

| Channel                     | Direction                         | VHost       | Target Exchange / Queue                                                                                              | Description                                                                                                                                          |
|:----------------------------|:----------------------------------|:------------|:---------------------------------------------------------------------------------------------------------------------|:-----------------------------------------------------------------------------------------------------------------------------------------------------|
| `lobby-inbound`             | Outgoing (Broker &rarr; RabbitMQ) | `/faf-core` | Exchange: `lobby-message-inbound`                                                                                    | Transmits validated inbound client messages with routing keys matching `lobby.<route>.<command>` or `gpg.<command>`.                                 |
| `lobby-outbound`            | Incoming (RabbitMQ &rarr; Broker) | `/faf-core` | Exchange: `lobby-message-outbound`<br>Queue: `broker.message.outbound.${faf.instance-id}`                            | Receives outbound messages targeted to a session (`Session`), player (`Player`), or broadcast (`All`).                                               |
| `lobby-schema-request`      | Outgoing (Broker &rarr; RabbitMQ) | `/faf-core` | Exchange: `lobby-schema-request`                                                                                     | Dispatches a schema request on broker startup with `reply_to` pointing to the instance's schema registration queue (`broker.schema.registration.${faf.instance-id}`). |
| `lobby-schema-registration` | Incoming (RabbitMQ &rarr; Broker) | `/faf-core` | Exchange: `lobby-schema-register`<br>Queue: `broker.schema.registration.${faf.instance-id}`                          | Receives runtime schema registrations to dynamically update validation rules and persist them to PostgreSQL.                                         |
| `player-active-request`     | Incoming (RabbitMQ &rarr; Broker) | `/faf-core` | Exchange: `lobby-session-request`<br>Queue: `broker.session.request.${faf.instance-id}`                             | Receives queries for all currently active player sessions. The broker responds by publishing current player connection states to the request's `reply_to` queue. |
| `player-connection`         | Outgoing (Broker &rarr; RabbitMQ) | `/faf-core` | Exchange: `lobby-session-state`                                                                                      | Broadcasts player session lifecycle states (`new`, `existing` keepalives / query responses, and `gone`) with TTL expiry to backend services.         |

#### Player Session Tracking & Active Player Requests

- **Connection Lifecycle Events**:
    - **`new`**: Emitted via `player-connection` (to exchange `lobby-session-state`) when a player authenticates and
      establishes a new session. Includes a TTL in `seconds_to_expiry` (`faf.player-session-status-refresh-period * 5`).
    - **`gone`**: Emitted via `player-connection` when a session disconnects or is unregistered.
    - **`existing`**: Periodically broadcast via `player-connection` as a keepalive heartbeat for all active sessions on
      a scheduled interval (`faf.player-session-status-refresh-period`, default `1m`).
- **Active Player Query (RPC)**:
    - Backend services can request an immediate snapshot of active players by publishing a message to exchange
      `lobby-session-request` (`player-active-request`) with a RabbitMQ `replyTo` queue header.
    - The broker handles the request by iterating over all active player sessions and publishing `existing` state
      messages via `player-connection`, overriding the exchange with the default exchange (`""`) and using the
      requester's `replyTo` queue as the routing key.

---

## Configuration Reference

The application is configured via `src/main/resources/application.yml` and supports the following environment variables:

| Variable                   | Description                                                                                                        | Default                                       |
|:---------------------------|:-------------------------------------------------------------------------------------------------------------------|:----------------------------------------------|
| `DATABASE_HOST`            | PostgreSQL host                                                                                                    | `localhost`                                   |
| `DATABASE_PORT`            | PostgreSQL port                                                                                                    | `5432`                                        |
| `DATABASE_NAME`            | Database name                                                                                                      | `faf-messages`                                |
| `DATABASE_USER`            | Database user                                                                                                      | `faf-message-broker`                          |
| `DATABASE_PASSWORD`        | Database password                                                                                                  | `banana`                                      |
| `RABBITMQ_HOST`            | RabbitMQ host                                                                                                      | `localhost`                                   |
| `RABBITMQ_PORT`            | RabbitMQ port                                                                                                      | `5672`                                        |
| `RABBITMQ_USER`            | RabbitMQ user                                                                                                      | `faf-message-broker`                          |
| `RABBITMQ_PASSWORD`        | RabbitMQ password                                                                                                  | `banana`                                      |
| `RABBITMQ_VHOST`           | RabbitMQ virtual host                                                                                              | `/faf-core`                                   |
| `ALLOWED_HOST`             | Allowed HTTP/WebSocket host header validation                                                                      | `ws.faforever.localhost,localhost`            |
| `HYDRA_TOKEN_ISSUER`       | Expected JWT issuer                                                                                                | `http://ory-hydra:4444`                       |
| `HYDRA_JWKS_URL`           | Ory Hydra JWKS public key endpoint                                                                                 | `http://localhost:4444/.well-known/jwks.json` |
| `USE_POLICY_SERVER`        | Enable or disable verification with policy server                                                                  | `false`                                       |
| `POLICY_SERVER_URL`        | Base URL of the policy verification service                                                                        | `http://localhost:8097`                       |
| `HOSTNAME`                 | Instance identifier (`faf.instance-id`) used for unique RabbitMQ queue names                                       | `local`                                       |
| `PING_INTERVAL`            | Interval at which the broker pings all registered sessions (`faf.ping-interval`)                                   | `1m`                                          |
| `STATUS_REFRESH_PERIOD`    | Periodic refresh interval for active player session status broadcasts (`faf.player-session-status-refresh-period`) | `1m`                                          |
| `REAL_IP_HEADER`           | HTTP header used to extract real client IP from WebSocket connection (`faf.real-ip-header`)                         | `X-Real-IP`                                   |
| `MAX_MESSAGE_QUEUE_SIZE`   | Maximum message queue capacity for inbound and outbound session queues (`faf.max-message-queue-size`)              | `100`                                         |
| `PUBLISH_UNKNOWN_MESSAGES` | Forward unknown inbound messages without registered schemas (`faf.publish-unknown-messages`)                       | `false`                                       |
| `UNKNOWN_MESSAGE_ROUTE`    | Route key used when forwarding unknown inbound messages (`faf.unknown-message-route`)                              | `legacy`                                      |

---

## Database & Migrations

The service uses PostgreSQL for storing registered command schemas. Flyway runs automatically on startup:

- **`V1__lobby_command_schema.sql`**: Creates `lobby_command_schema` table (primary key `(command, direction)`):
    - `command` (`VARCHAR`): Command identifier.
    - `direction` (`VARCHAR`): `inbound` or `outbound`.
    - `schema` (`JSONB`): JSON Schema Draft 2020-12 specification.
    - `route` (`VARCHAR`): Route segment used in the `lobby.<route>.<command>` routing key for inbound messages.

---

## Getting Started

### Prerequisites

- **Java JDK**: 25 or higher
- **PostgreSQL**: 14+ (or compatible container)
- **RabbitMQ**: 3.9+ with `/faf-core` virtual host configured
- **Ory Hydra**: (for JWT authentication verification)

For local development and orchestrating all supporting backend services (PostgreSQL, RabbitMQ, Hydra, etc.), you can use
the [FAForever gitops-stack](https://github.com/FAForever/gitops-stack) Tilt setup.

### Running in Development Mode

The dev config is setup to run as a local service
using [Tilt via gitops-stack](https://github.com/FAForever/gitops-stack)

Run the application in Quarkus development mode with live reload:

```bash
./gradlew quarkusDev
```

*(On Windows PowerShell: `.\gradlew.bat quarkusDev`)*

In dev mode, traffic logging for WebSockets and SQL statement logging are enabled by default, and
`faf.publish-unknown-messages` is set to `true`.

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
- [Quarkus Hibernate ORM Guide](https://quarkus.io/guides/hibernate-orm): Persistence with Hibernate ORM, including
  Jakarta Data repositories.
