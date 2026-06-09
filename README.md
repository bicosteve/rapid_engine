<div align="center">

# Rapid Engine

### High-throughput, delta-aware ingestion service for sports odds data

A production-style **Spring Boot microservice** that
polls [The Rundown](https://www.therundown.ai/) for live sports events, lines,
and odds, then streams them onto **RabbitMQ** topics for downstream consumers in
a sportsbook data pipeline.

[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](#)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.11-6DB33F?logo=springboot&logoColor=white)](#)
[![Maven](https://img.shields.io/badge/Maven-3.9+-C71A36?logo=apachemaven&logoColor=white)](#)
[![License](https://img.shields.io/badge/License-MIT-blue)](#)
[![Status](https://img.shields.io/badge/Status-Active%20Development-success)](#)

</div>

---

## Table of Contents

- [ Overview](#-overview)
- [ Key Features](#-key-features)
- [ Architecture](#-architecture)
- [ Tech Stack](#-tech-stack)
- [ Project Structure](#-project-structure)
- [ Getting Started](#-getting-started)
- [ Configuration](#-configuration)
- [ Usage](#-usage)
- [ Messaging Contract](#-messaging-contract)
- [ Data Model](#-data-model)
- [ Design Decisions](#-design-decisions)
- [ Observability & Logging](#-observability--logging)
- [ Testing](#-testing)
- [ Development](#-development)
- [ Roadmap](#-roadmap)
- [ Contributing](#-contributing)
- [ License](#-license)
- [ Author](#-author)
- [ Acknowledgements](#-acknowledgements)

---

## Overview

**Rapid Engine** is the *ingestion tier* of a larger sportsbook platform.
Its job is to keep the platform's database to be continuously fed with fresh
sports events, market lines, and prices coming from The Rundown API for the
downstream services.

Instead of naively re-pulling the world on every tick, the service is built
around a **delta-driven** model: each successful API call returns a
`delta_last_id` cursor, which is persisted in **Redis** and used as a bookmark
for the next incremental fetch. This keeps the network chatter and the per-cycle
API quota small even as the configured list of sports grows.

The fetched events are then serialized with Jackson and dispatched onto
**RabbitMQ topic exchange**, decoupling ingestion from the consumers that
persist the events.

---

## Key Features

| Feature                                | Description                                                                                                         |
|----------------------------------------|---------------------------------------------------------------------------------------------------------------------|
| **Scheduled Polling**                  | `@Scheduled` worker that wakes up every 5 minutes (configurable) to refresh event data                              |
| **Round-Robin Sports Cycling**         | Distributes API load across 38 sport IDs (e.g. football, basketball, tennis) over successive cycles                 |
| **Delta-Aware Fetches**                | Uses the `delta_last_id` returned by The Rundown, persisted in Redis with a 24h TTL, to perform incremental updates |
| **Multi-Day Windows**                  | Pulls *yesterday*, *today*, and (optionally) *tomorrow* fixtures in a single cycle                                  |
| **RabbitMQ Producer**                  | Declares `matches.*` and `results.*` topic exchanges, queues, and bindings on startup                               |
| **Externalized Configuration**         | All secrets & environment-specific values are read from a `.env` file via `spring-dotenv`                           |
| **Snake-Case JSON**                    | Global Jackson `ObjectMapper` mapped to snake_case — no more `@JsonProperty` boilerplate                            |
| **Redis-Backed Cursor**                | Hot, low-latency storage for the per-sport delta cursor with automatic expiry                                       |
| **Profile-Aware Logging**              | Console-only in `dev`, size+time-rotated async file logs (with a dedicated error stream) in `prod`                  |
| **Built-in Retry & Failure Tolerance** | Per-sport exceptions are caught, logged, and never crash the scheduler                                              |
| **Decoupled Design**                   | The producer doesn't know or care who the consumers are — any subscriber to the configured topic gets the events    |

---

## Architecture

```
                    ┌──────────────────────┐
                    │  The Rundown API     │
                    │  (sports odds feed)  │
                    └──────────┬───────────┘
                               │  HTTPS (delta + events)
                               ▼
       ┌──────────────────────────────────────────────┐
       │              Rapid Engine                    │
       │                                              │
       │  ┌──────────────────┐   ┌──────────────────┐ │
       │  │  MatchSyncTask   │──▶│  EventProducer   │ │
       │  │  (@Scheduled)    │   │  (REST client)   │ │
       │  └──────────────────┘   └────────┬─────────┘ │
       │                                  │           │
       │                       ┌──────────▼────────┐  │
       │                       │      Redis        │  │
       │                       │ (delta_last_id)   │  │
       │                       └──────────┬────────┘  │
       │                                  │           │
       │                       ┌──────────▼────────┐  │
       │                       │   RabbitTemplate  │  │
       │                       │ (JSON converter)  │  │
       │                       └──────────┬────────┘  │
       └──────────────────────────────────┼───────────┘
                                          │
                          ┌───────────────┴───────────────┐
                          ▼                               ▼
                ┌──────────────────┐           ┌──────────────────┐
                │  matches.exchange│           │ results.exchange │
                │  (topic)         │           │ (topic)          │
                └──────────────────┘           └──────────────────┘
                          │                               │
                          ▼                               ▼
                ┌──────────────────┐           ┌──────────────────┐
                │  matches.queue   │           │ results.queue    │
                │  (downstream DB) │           │  (settlement)    │
                │                  │           │                  │
                └──────────────────┘           └──────────────────┘
```

**Flow in plain English:**

1. `MatchSyncTask` runs every 5 minutes (after a 20s startup delay).
2. It picks the next sport ID using a **round-robin** cursor and asks
   `EventProducer` to fetch.
3. `EventProducer` reads the last `delta_last_id` for that sport from Redis.
4. It calls The Rundown API for *yesterday*, *today*, and *(optionally)*
   *tomorrow*.
5. The response is deserialized into a `RundownResponse` of `Event` aggregates (
   each carrying its `Scores`, `Teams[]`, `Markets[]`, and `Prices`).
6. Each `Event` is JSON-serialized and published to the **`matches`** topic
   exchange.
7. The new `delta_last_id` is written back to Redis (TTL: 24h).
8. The next sport in the list is selected, and the cycle continues.

---

## Tech Stack

| Layer                | Technology                                        |
|----------------------|---------------------------------------------------|
| Language             | **Java 21**                                       |
| Framework            | **Spring Boot 3.5.11**                            |
| Build Tool           | **Maven** (with the `mvnw` wrapper)               |
| Web/REST             | `spring-boot-starter-web` (`RestTemplate`)        |
| Messaging            | `spring-boot-starter-amqp` (RabbitMQ)             |
| Cache & Cursor Store | `spring-boot-starter-data-redis`                  |
| JSON                 | **Jackson** (`JavaTimeModule`, snake_case naming) |
| Logging              | **Logback** with profile-specific configuration   |
| Config               | `spring-dotenv` 5.1.0 — loads `.env` at startup   |
| DX                   | Lombok, Spring DevTools                           |

---

## Project Structure

```
rapid-engine/
├── Makefile                          # Convenience targets (run, install)
├── pom.xml                           # Maven build & dependencies
├── mvnw, mvnw.cmd                    # Maven wrapper
├── .env-example                      # Template for required environment vars
├── .gitignore
├── entities.txt                      # Downstream DB schema notes
├── notes.txt                         # API response field reference
├── logs/                             # Rotated log output (prod)
└── src/
    ├── main/
    │   ├── java/com/bixx/rapid_engine/
    │   │   ├── RapidEngineApplication.java   # @SpringBootApplication entry point
    │   │   ├── config/
    │   │   │   ├── AppConfig.java            # RestTemplate bean
    │   │   │   ├── JacksonConfig.java        # Global ObjectMapper (snake_case, JavaTime)
    │   │   │   ├── RedisConfig.java          # StringRedisTemplate
    │   │   │   └── RundownConfig.java        # @ConfigurationProperties("app.rundown-api")
    │   │   ├── rabbitmq/
    │   │   │   ├── RabbitMQConfig.java       # @ConfigurationProperties("app.rabbitmq")
    │   │   │   └── RabbitMQBeans.java        # Queues, exchanges, bindings, RabbitTemplate
    │   │   ├── producer/
    │   │   │   └── EventProducer.java        # The actual ingestion logic
    │   │   ├── utils/
    │   │   │   └── MatchSyncTask.java        # @Scheduled poller (round-robin)
    │   │   └── models/
    │   │       ├── RundownResponse.java
    │   │       ├── Event.java
    │   │       ├── Meta.java
    │   │       ├── Score.java
    │   │       ├── Schedule.java
    │   │       ├── Team.java
    │   │       ├── Conference.java
    │   │       ├── Market.java
    │   │       ├── Participant.java
    │   │       ├── Line.java
    │   │       └── Price.java
    │   └── resources/
    │       ├── application.yml               # Active profile + optional .env import
    │       ├── application-dev.yaml          # Dev profile config
    │       └── rapid-engine-logback.xml      # Profile-aware Logback config
    └── test/
        └── java/com/bixx/rapid_engine/
            └── RapidEngineApplicationTests.java
```

---

## Getting Started

### Prerequisites

Make sure you have the following installed locally (or reachable on your
network):

- **Java 21+**
  ```bash
  java -version
  ```
- **Maven 3.9+** (or use the bundled `./mvnw` wrapper)
- **RabbitMQ** instance (local Docker is fine)
- **Redis** instance
- A valid **Rundown API key** (sign up
  at [therundown.ai](https://www.therundown.ai/))

### 1. Clone the repository

```bash
git clone git@github.com:bicosteve/rapid_engine.git
cd rapid_engine
```

### 2. Configure environment

Copy the example env file and fill in your values:

```bash
cp .env-example .env
```

Then edit `.env`:

```ini
APP_PORT=5002
LOGGING_LEVEL=DEBUG

RUNDOWN_API_KEY=your-rundown-key
RUNDOWN_BASE_URL=https://api.therundown.io/v1
RUNDOWN_SPORT_IDS=1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,32,33,38,39
AFFILIATE_IDS=23

RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=guest
RABBITMQ_PASSWORD=guest

RABBITMQ_MATCHES_EXCHANGE=matches.exchange
RABBITMQ_MATCHES_QUEUE=matches.queue
RABBITMQ_MATCHES_ROUTING_KEY=matches.routing.key

RABBITMQ_RESULTS_EXCHANGE=results.exchange
RABBITMQ_RESULTS_QUEUE=results.queue
RABBITMQ_ROUTING_KEY=results.routing.key

REDIS_HOST=localhost
REDIS_PORT=6379
```

> The `spring-dotenv` library auto-loads `.env` at startup. No need to`export`
> anything manually.

### 3. Start dependencies (Docker quickstart)

```bash
# RabbitMQ with management UI
docker run -d --name rabbit \
  -p 5672:5672 -p 15672:15672 \
  -e RABBITMQ_DEFAULT_USER=guest \
  -e RABBITMQ_DEFAULT_PASS=guest \
  rabbitmq:3-management

# Redis
docker run -d --name redis -p 6379:6379 redis:7-alpine
```

### 4. Build & run

Using the Makefile (recommended):

```bash
make install     # mvn clean install
make run         # mvn spring-boot:run
```

Or directly with Maven:

```bash
./mvnw clean install
./mvnw spring-boot:run
```

The service will boot on the port defined by `APP_PORT` (default `5002`) and
will perform its first fetch **20 seconds** after startup. Subsequent fetches
occur every **5 minutes** (per the `fixedRate` in `MatchSyncTask`).

---

## Configuration

All runtime configuration is driven by `application.yml` and
`application-dev.yaml`, with secrets loaded from `.env`.

### Active profiles

`application.yml` activates the `dev` profile by default. To run in `prod`, set:

```bash
SPRING_PROFILES_ACTIVE=prod ./mvnw spring-boot:run
```

### `application-dev.yaml` keys

| Key                               | Source | Purpose                                    |
|-----------------------------------|--------|--------------------------------------------|
| `spring.data.redis.host` / `port` | env    | Redis connection                           |
| `spring.rabbitmq.*`               | env    | RabbitMQ connection                        |
| `app.rabbitmq.matches.*`          | env    | Matches exchange/queue/routing key         |
| `app.rabbitmq.results.*`          | env    | Results exchange/queue/routing key         |
| `app.rundown-api.key`             | env    | The Rundown API key                        |
| `app.rundown-api.host`            | env    | The Rundown base URL                       |
| `app.rundown-api.sports-id`       | env    | Comma-separated list of sport IDs to cycle |
| `app.rundown-api.affiliate-id`    | env    | Affiliate ID required by the provider      |
| `server.port`                     | env    | HTTP port (mostly unused — headless)       |
| `logging.level.*`                 | env    | Per-package log level                      |

### `MatchSyncTask` schedule

```java
@Scheduled(fixedRate = 300_000, initialDelay = 20_000)
```

- **`fixedRate = 300_000`** → run every 5 minutes
- **`initialDelay = 20_000`** → first run 20s after the app starts

To change it, edit `MatchSyncTask.java` or extract to
`@ConfigurationProperties` (see the [Roadmap](#-roadmap)).

---

## Usage

This service is a **background worker** — there is no HTTP API to call. Once
started, it will:

1. Log the boot banner and the active profile.
2. After 20s, perform the **first fetch** for sport ID #1.
3. Continue cycling through every sport ID in `RUNDOWN_SPORT_IDS`, one per
   cycle.
4. Publish every fetched event to the `matches` exchange.
5. Log a summary of published events per cycle.

To verify it's working, tail the logs:

```bash
tail -f logs/rapid-engine.log         # prod profile
./mvnw spring-boot:run                # dev profile (console)
```

In another terminal, watch the queue grow:

```bash
# RabbitMQ management UI
open http://localhost:15672            # guest / guest

# Or via redis-cli to inspect delta cursors
redis-cli GET rundown:delta_last_id:1
```

### Sample log output

```
2026-06-07 09:05:48 | INFO  | MatchSyncTask | fetchMatches | Cycling to sport id 1
2026-06-07 09:05:48 | INFO  | MatchSyncTask | fetchMatches | Trigger match producer
2026-06-07 09:05:49 | INFO  | EventProducer  | fetchEventsForADate | No delta found for 1. Fetching the events data
2026-06-07 09:05:51 | INFO  | EventProducer  | fetchEventsForADate | Sport Id 1 - saved new delta abc123def456
2026-06-07 09:05:51 | INFO  | MatchSyncTask | fetchMatches | Published events 24
```

---

## Messaging Contract

The service declares two topic exchanges. Downstream services must bind their
queues to the corresponding routing keys.

### `matches` channel

| Component   | Value                                                          |
|-------------|----------------------------------------------------------------|
| Exchange    | `RABBITMQ_MATCHES_EXCHANGE` (default `matches.exchange`)       |
| Queue       | `RABBITMQ_MATCHES_QUEUE` (default `matches.queue`)             |
| Routing key | `RABBITMQ_MATCHES_ROUTING_KEY` (default `matches.routing.key`) |
| Payload     | `Event` JSON (see [Data Model](#-data-model))                  |

### `results` channel

| Component   | Value                                                          |
|-------------|----------------------------------------------------------------|
| Exchange    | `RABBITMQ_RESULTS_EXCHANGE` (default `results.exchange`)       |
| Queue       | `RABBITMQ_RESULTS_QUEUE` (default `results.queue`)             |
| Routing key | `RABBITMQ_RESULTS_ROUTING_KEY` (default `results.routing.key`) |
| Payload     | (reserved for score-result events)                             |

### Message format

All messages are **JSON**, serialized by Jackson via
`Jackson2JsonMessageConverter` (which inherits the global snake-case
`ObjectMapper`). Example payload:

```json
{
  "event_id": "abc123",
  "event_uuid": "uuid-...",
  "sport_id": 1,
  "event_date": "2026-06-07T19:00:00Z",
  "score": {
    "event_id": "abc123",
    "event_status": "STATUS_SCHEDULED",
    "score_away": 0,
    "score_home": 0
  },
  "teams": [
    {
      "team_id": 3934,
      "name": "Elche",
      "is_away": true
    },
    {
      "team_id": 1500,
      "name": "Villarreal",
      "is_home": true
    }
  ],
  "markets": [
    {
      "id": 2674437,
      "market_id": 1,
      "name": "moneyline",
      "participants": [
        {
          "id": 3934,
          "name": "Elche",
          "lines": [
            {
              "prices": {
                "3": {
                  "price": 250
                }
              }
            }
          ]
        },
        {
          "id": 1500,
          "name": "Villarreal",
          "lines": [
            {
              "prices": {
                "3": {
                  "price": -120
                }
              }
            }
          ]
        }
      ]
    }
  ]
}
```

---

## Data Model

All API responses are deserialized into plain POJOs (Lombok-annotated) under
`com.bixx.rapid_engine.models`. The aggregate root is **`Event`**, which fans
out into teams, markets, participants, lines, and prices.

```
RundownResponse
└── Meta                 (delta cursor)
└── List<Event>
    ├── Score            (status, clock, period, broadcast, venue)
    ├── List<Team>
    │   └── Conference
    ├── Schedule         (season, headline, attendance)
    └── List<Market>
        └── List<Participant>
            └── List<Line>
                └── Map<String, Price>   (keyed by bookmaker id)
```

### Field reference

| Model             | Key fields                                                                                                                                     |
|-------------------|------------------------------------------------------------------------------------------------------------------------------------------------|
| `RundownResponse` | `meta`, `events[]`                                                                                                                             |
| `Meta`            | `delta_last_id` (the cursor written back to Redis)                                                                                             |
| `Event`           | `event_id`, `event_uuid`, `sport_id`, `event_date`, `score`, `teams[]`, `schedule`, `markets[]`                                                |
| `Score`           | `event_status`, `event_status_detail`, `team_id_away/home`, `score_away/home`, `game_clock`, `game_period`, `winner_*`, `broadcast`, `venue_*` |
| `Team`            | `team_id`, `name`, `mascot`, `abbreviation`, `conference_id`, `division_id`, `ranking`, `record`, `is_home/away`, `conference`                 |
| `Conference`      | `conference_id`, `sport_id`, `name`                                                                                                            |
| `Schedule`        | `season_type`, `season_year`, `event_name`, `event_headline`, `attendance`, `conference_competition`                                           |
| `Market`          | `id`, `market_id`, `period_id`, `name`, `description`, `participants[]`                                                                        |
| `Participant`     | `id`, `type` (`TYPE_TEAM` / `TYPE_RESULT`), `name`, `lines[]`                                                                                  |
| `Line`            | `id`, `value` (`"+1.25"`, `"2.5"`, etc.), `prices{}` (bookmaker → `Price`)                                                                     |
| `Price`           | `id`, `price` (American odds), `price_delta`, `is_main_line`, `updated_at`, `closed_at`                                                        |

The downstream relational schema is sketched in `entities.txt` (events, scores,
teams, markets, participants, prices). The consumer service is expected to
flatten this graph into those tables, computing surrogate keys where needed.

---

## Design Decisions

### Why a separate producer service?

The ingestion loop has very different scaling characteristics (network-bound,
quota-bound, latency-sensitive) from the persistence and pricing logic.
Splitting them out:

- Allows the consumer to be scaled, restarted, or replaced without disrupting
  the polling cadence.
- Keeps the API key and third-party endpoint usage isolated to one place.
- Lets us swap the upstream provider (e.g. switch from The Rundown to any
  provider) by changing *one* class (`EventProducer`).

### Why a delta cursor in Redis?

The Rundown API exposes a `delta_last_id` cursor for incremental updates. Using
Redis (instead of a relational table) for the cursor:

- Avoids hammering the database on every cycle.
- Provides natural TTL semantics (24h) so stale cursors expire on their own.
- Keeps the access pattern trivially fast (`GET`/`SET` of a single string).

If Redis is unreachable the producer **does not fail** — the next cycle simply
falls back to a full fetch (no `delta_last_id` parameter is sent). This is
intentional: graceful degradation over hard failure.

### Why round-robin across sports?

Each `fetchEvents` call may take several seconds (especially for football), and
we don't want to block the scheduler for too long. Round-robin ensures every
sport is touched regularly without one slow sport starving the others.

### Why a custom Jackson `ObjectMapper`?

- **Snake-case naming** matches the upstream API out of the box, so DTOs don't
  need `@JsonProperty` on every field.
- `FAIL_ON_UNKNOWN_PROPERTIES = false` makes the client forward-compatible with
  API additions.
- `JavaTimeModule` + `WRITE_DATES_AS_TIMESTAMPS = false` serializes
  `OffsetDateTime` as ISO-8601 strings — the rest of the pipeline will thank
  you.

### Why `RestTemplate` over `WebClient`?

The upstream is a simple, blocking JSON REST API. `RestTemplate` is more than
enough and avoids the extra dependency on `spring-webflux`. If we ever need
streaming or higher concurrency, the bean can be swapped for `WebClient` in a
single place (`AppConfig`).

### Why a headless worker (no controllers)?

There is no use case for an external caller to trigger fetches on demand — the
data is continuously streamed. Removing the HTTP surface also removes a class of
security concerns (no input validation, no rate limiting, no auth).

---

## Observability & Logging

Logging is fully driven by **`rapid-engine-logback.xml`** and is profile-aware.

### `dev` profile

- Logs to the **console only**.
- App packages log at `DEBUG`; Spring framework at `WARN`.
- Ideal for local development.

### `prod` profile

- **Async** appenders (non-blocking) write to:
    - `logs/rapid-engine.log` — all logs.
    - `logs/rapid-engine-error.log` — `ERROR` level only.
- Logs are rotated by **size and time** (`SizeAndTimeBasedRollingPolicy`).
    - Max file size: `50MB`
    - Retention: `30 days`
    - Total size cap: `500MB` (100MB for the error stream).
- Spring framework is muted to `WARN` to reduce noise; SQL/JDBC is at `INFO` for
  visibility into persistence.

Activate `prod` with:

```bash
SPRING_PROFILES_ACTIVE=prod ./mvnw spring-boot:run
```

### Log pattern

```
%d{yyyy-MM-dd HH:mm:ss} | %-5level | %logger{25} | %X{traceId} | %msg%n
```

- `%X{traceId}` is reserved for future distributed-tracing (Micrometer Tracing /
  OpenTelemetry) support.

### Hooks for future work

- Drop in **Micrometer** + **Prometheus** for `/actuator/prometheus` metrics (
  events published per cycle, fetch latency, error counts).
- Add a **correlation ID** at the start of each cycle to thread through the
  `traceId` MDC slot.

---

## Testing

The current test scaffold contains the auto-generated Spring context smoke test:

```java

@SpringBootTest
class RapidEngineApplicationTests {
    @Test
    void contextLoads(){
    }
}
```

### Recommended next steps (see [Roadmap](#-roadmap))

- **Unit tests** for `EventProducer` (mock `RestTemplate` + `RedisTemplate` +
  `RabbitTemplate`).
- **Slice tests** (`@JsonTest`, `@DataRedisTest`) for serialization and cursor
  logic.
- **Testcontainers** for integration tests against a real Redis + RabbitMQ.
- **Contract tests** (Pact / Spring Cloud Contract) between `EventProducer` and
  downstream consumers.

Run the existing tests with:

```bash
./mvnw test
```

---

## Development

### Useful Make targets

```bash
make install   # mvn clean install
make run       # mvn spring-boot:run
```

### Hot reload

`spring-boot-devtools` is included for runtime class reloading during local
development — just save a file and the app will restart.

### IDE tips

- **IntelliJ IDEA**: enable *Annotation Processing* (Lombok plugin) and *Build
  project automatically* for the best dev experience.
- **VS Code**: install the *Extension Pack for Java* and *Lombok Annotations
  Support*.

### Adding a new upstream provider

1. Create a new `XxxConfig` (mirroring `RundownConfig`).
2. Create a new `XxxProducer` (mirroring `EventProducer`).
3. Reuse the existing `RabbitTemplate` and `RedisTemplate` beans.
4. Wire the new producer into `MatchSyncTask` (or add a second scheduler).

### Adding a new event type

1. Extend the `Event` model (or create a new DTO).
2. Publish to the appropriate RabbitMQ exchange via the `RabbitTemplate`.
3. Update the consumer service to subscribe to the new routing key.

---

## Roadmap

- [ ] **Unit & integration test suite** (Testcontainers, Mockito)
- [ ] **Dead-letter queue (DLQ)** wiring for failed RabbitMQ publishes
- [ ] **Spring Boot Actuator** + Prometheus metrics
- [ ] **Micrometer Tracing** (OpenTelemetry) for distributed tracing
- [ ] **Results consumer** — populate the `results` exchange from score updates
- [ ] **Configurable schedule** — drive `fixedRate` and `initialDelay` from
  `application.yml`
- [ ] **Graceful shutdown** — finish in-flight fetches on `SIGTERM`
- [ ] **Dockerfile + docker-compose** (app + Redis + RabbitMQ) for one-command
  local startup
- [ ] **CI workflow** (GitHub Actions: build, test, lint)
- [ ] **Multi-environment profiles** (`staging`, `prod`) with separate
  `application-*.yaml`
- [ ] **WebClient migration** if we need streaming/async upstream calls

---

## Contributing

Contributions are welcome!

1. Fork the repository.
2. Create a feature branch: `git checkout -b feature/awesome-thing`
3. Commit your changes: `git commit -m "feat: add awesome thing"`
4. Push to your branch: `git push origin feature/awesome-thing`
5. Open a Pull Request describing the change and its motivation.

Please follow conventional commit messages (`feat:`, `fix:`, `chore:`, `docs:`,
`refactor:`, `test:`) and ensure `./mvnw clean install` passes before requesting
review.

---

## License

This project is licensed under the **MIT License**. See [`LICENSE`](./LICENSE)
for the full text (add a `LICENSE` file at the repo root if it does not yet
exist).

```
MIT License

Copyright (c) 2026 bicosteve

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## Author

**Steve (bicosteve)**

- GitHub: [@bicosteve](https://github.com/bicosteve)
-

Repo: [github.com/bicosteve/rapid_engine](https://github.com/bicosteve/rapid_engine)

---

## Acknowledgements

- [The Rundown](https://www.therundown.ai/) — the upstream sports data provider.
- [Spring Boot](https://spring.io/projects/spring-boot) — the framework that
  powers the service.
- [RabbitMQ](https://www.rabbitmq.com/) — the message broker used for downstream
  delivery.
- [Redis](https://redis.io/) — the low-latency cursor store.
- [spring-dotenv](https://github.com/paulschwarz/spring-dotenv) — effortless
  `.env` loading.
- The open-source community — for the libraries and tools that make projects
  like this possible.

---

<div align="center">

⭐ If you find this project useful, consider giving it a star on GitHub!

</div>
