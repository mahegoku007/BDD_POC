# Classroom Booking System

A production-ready, event-driven microservices system built with **Spring Boot 3**, **Apache Camel 4**, **RabbitMQ**, and **Kubernetes** (Rancher Desktop). The project doubles as a complete **BDD / Cucumber integration-test showcase**.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Module Breakdown](#module-breakdown)
3. [Message Flow](#message-flow)
4. [Flow Walkthrough](#flow-walkthrough)
   - [Phase 1 — Synchronous Intake](#phase-1--synchronous-intake-client--gateway--booking-service)
   - [Phase 2 — Async Availability Check](#phase-2--asynchronous-availability-check-rabbitmq--availability-service)
   - [Phase 3 — Notification Dispatch](#phase-3--notification-dispatch-rabbitmq--notification-service)
   - [End-to-End Timeline](#end-to-end-timeline)
5. [RabbitMQ Topology](#rabbitmq-topology)
6. [REST API](#rest-api)
7. [Validation Rules](#validation-rules)
8. [Async Processing Pipeline](#async-processing-pipeline)
9. [BDD Test Suite](#bdd-test-suite)
10. [Technology Stack](#technology-stack)
11. [Project Structure](#project-structure)

---

## Architecture Overview

```
┌──────────────────────────────────────────────────────────────────────────┐
│                          KUBERNETES CLUSTER                              │
│  ┌──────────────┐   HTTP   ┌──────────────────┐   AMQP (booking.req)    │
│  │   Client     │ ──────►  │  api-gateway     │ ──────────────────────► │
│  │  (curl /     │  :8080   │  (Camel :8080)   │                         │
│  │   browser)   │          └──────┬───────────┘                         │
│  └──────────────┘                 │                                      │
│                                   │ HTTP forward (Route Slip)            │
│                                   ▼                                      │
│                         ┌──────────────────┐   AMQP (booking.req)       │
│                         │ service-booking  │ ──────────────────────►    │
│                         │   (:8081)        │                             │
│                         └──────────────────┘                             │
│                                   │                                      │
│                         ┌─────────▼────────┐                             │
│                         │  service-audit   │ ← audit trail (MongoDB)    │
│                         │   (:8084)        │                             │
│                         └──────────────────┘                             │
│                                                                          │
│   ┌──────────────────────────────────────────────────────────────────┐  │
│   │                    RabbitMQ  (:5672)                             │  │
│   │   Exchange: classroom.booking.exchange  (topic)                  │  │
│   │                                                                  │  │
│   │   booking.requested ──► service-availability (:8082)            │  │
│   │                              │                                   │  │
│   │            ┌─────────────────┴────────────────┐                  │  │
│   │            ▼                                  ▼                  │  │
│   │   booking.confirmed                  booking.rejected            │  │
│   │            │                                  │                  │  │
│   │            └──────────────┬───────────────────┘                  │  │
│   └───────────────────────────┼──────────────────────────────────────┘  │
│                               ▼                                          │
│                    ┌──────────────────┐                                  │
│                    │service-notification                                  │
│                    │   (:8083)        │  → logs notification (email sim) │
│                    └──────────────────┘                                  │
│                                                                          │
│   ┌───────────────┐                                                      │
│   │  MongoDB      │  ← persistent audit trail storage                   │
│   │  (:27017)     │                                                      │
│   └───────────────┘                                                      │
└──────────────────────────────────────────────────────────────────────────┘
```

The system is **entirely asynchronous** after the initial HTTP acceptance. A booking request receives an immediate `202 Accepted` response while the availability check and notification happen in the background through RabbitMQ queues.

---

## Module Breakdown

### `common-lib` — Shared Library
Contains all DTOs, enums, and utilities shared by every service. Nothing in here has a `main()` method; it compiles to a plain JAR consumed as a Maven dependency.

| Class | Purpose |
|-------|---------|
| `BookingRequest` | Inbound request DTO + RabbitMQ event payload for `booking.requested` |
| `BookingResponse` | Outbound result DTO + payload for `booking.confirmed` / `booking.rejected` |
| `AvailabilityCheckResponse` | Return type from the availability service's check operation |
| `TimeSlot` | Value object wrapping `startTime` / `endTime` with `HH:mm` JSON formatting |
| `BookingStatus` | Enum: `PENDING`, `CONFIRMED`, `REJECTED` |
| `AvailabilityStatus` | Enum: `AVAILABLE`, `UNAVAILABLE` |
| `RabbitMQConstants` | Single source of truth for all exchange, queue, and routing-key names |
| `BookingIdGenerator` | Generates collision-resistant booking IDs in the form `BK-<8hex>` |
| `BookingResponseFactory` | Builds `BookingResponse` from a `BookingRequest` in one line |
| `TimeSlotUtils` | Validates that `endTime > startTime` |

---

### `api-gateway-camel` — API Gateway (port 8080)
The public-facing entry point built with **Apache Camel 4** REST DSL on Spring Boot's embedded Tomcat.

**Responsibilities:**
- Expose `POST /bookings`, `POST /bookings/batch`, and `GET /actuator/health`
- Deserialise and validate the incoming JSON body (`classroomId`, `requestedBy`, `date`, `timeSlot` must all be present and parseable)
- Validate time slot ordering (`endTime` must be after `startTime`)
- Return `400 Bad Request` for any validation failure with a structured error body
- **Route Slip EIP** — process validated requests through a configurable pipeline (default: `direct:submitBooking,direct:audit`)
- Forward valid requests to `service-booking` over HTTP (`POST http://service-booking:8081/bookings`)
- Send audit events to `service-audit` over HTTP (`POST http://service-audit:8084/audit/bookings`)
- **Batch processing** — `POST /bookings/batch` accepts a JSON array, splits it using Camel's Splitter EIP, processes each through the route slip, and aggregates results
- Proxy the `202 Accepted` response (with `bookingId`) back to the client
- Handle error scenarios: `503` if booking service is down, `502` for unexpected upstream responses

**Camel Routes:**
| Route ID | Trigger | Action |
|----------|---------|--------|
| `validate-booking-route` | `direct:validateBooking` | Entry point from REST DSL, delegates to processSingleBooking |
| `process-single-booking` | `direct:processSingleBooking` | Parse JSON → validate fields → time slot check → route slip |
| `route-slip-dispatcher` | `direct:routeSlipDispatcher` | Dynamic Route Slip driven by ConfigMap (`ROUTE_SLIP_PIPELINE`) |
| `submit-booking-route` | `direct:submitBooking` | Serialise, set HTTP headers, call service-booking, proxy response |
| `audit-route` | `direct:audit` | POST booking payload to service-audit, restore original body |
| `batch-booking-route` | `direct:batchBooking` | Split JSON array → process each → aggregate into JSON array response |
| `gateway-info-route` | `direct:gatewayInfo` | Return gateway info JSON on `GET /bookings` |

---

### `service-booking` — Booking Service (port 8081)
Internal service (not reachable directly by clients) that receives HTTP requests from the API Gateway.

**Responsibilities:**
- Apply Bean Validation (`@Valid`) to the request body
- Assign a unique `bookingId` via `BookingIdGenerator`
- Set `status = PENDING`
- Validate that `endTime > startTime` (throws `IllegalArgumentException` → `400`)
- Publish a `BookingRequest` event to `booking.requested` queue
- Return `202 Accepted` with the enriched `BookingRequest` (includes `bookingId`)

---

### `service-availability` — Availability Service (port 8082)
Stateful service that owns the booking ledger (in-memory H2 database).

**Responsibilities:**
- **Consume** events from `booking.requested` queue
- **Check** for time-slot conflicts using a half-open interval query (`startTime < reqEndTime AND endTime > reqStartTime`)
- If **AVAILABLE**: persist the booking to H2, publish `BookingResponse` → `booking.confirmed`
- If **UNAVAILABLE**: publish `BookingResponse` → `booking.rejected` (nothing persisted)
- Expose test-support REST endpoints (`/test/bookings`, `/test/availability/check`) for BDD tests

**Overlap Detection Logic:**
```
Overlap exists when: existing.start < requested.end  AND  existing.end > requested.start
Adjacent slots (existing.end == requested.start) are NOT considered overlapping.
```

---

### `service-notification` — Notification Service (port 8083)
Stateless consumer that closes the booking lifecycle by notifying the requester.

**Responsibilities:**
- **Consume** events from `booking.confirmed` → send confirmation notification
- **Consume** events from `booking.rejected` → send rejection notification
- Build human-readable message strings, log them (simulating email/SMS)
- Maintain an in-memory `NotificationLog` with full idempotency (duplicate events are silently ignored)
- Expose test-support REST endpoints (`/test/notifications`, `/test/notifications/summary`) for BDD tests

---

### `service-audit` — Audit Service (port 8084)
MongoDB-backed audit trail service that records all booking lifecycle events.

**Responsibilities:**
- Receive audit events from the API Gateway via HTTP (`POST /audit/bookings`)
- Persist audit records to MongoDB with timestamps, action type, and source information
- Expose query endpoints for audit trail retrieval (`GET /audit/bookings/{bookingId}`, `GET /audit/classrooms/{classroomId}`)
- Return `404` for unknown booking IDs
- Provide chronological ordering of audit events

**Technology:**
- Spring Data MongoDB for persistence
- MongoDB 7 deployed as a Kubernetes pod with PVC for data durability

---

### `integration-tests` — Cucumber BDD Suite
Does **not** run as a service. Contains the full acceptance test suite that exercises the running system end-to-end.

See [BDD Test Suite](#bdd-test-suite) for details.

---

## Message Flow

A complete booking lifecycle from REST call to notification:

```
1.  Client         →  POST /bookings  →  api-gateway-camel (:8080)
2.  api-gateway    →  HTTP POST       →  service-booking (:8081)
3.  service-booking   assigns bookingId, sets PENDING
4.  service-booking   publishes BookingRequest  →  booking.requested queue
5.  service-booking   returns 202 Accepted + body to gateway
6.  gateway        →  proxies 202 Accepted + body  →  Client

    ── ASYNC from here ─────────────────────────────────────────────────
7.  service-availability  consumes from  booking.requested
8a. If slot is FREE:
      service-availability  persists booking to H2
      service-availability  publishes BookingResponse  →  booking.confirmed
9a. service-notification  consumes from  booking.confirmed
      logs / sends CONFIRMATION notification to requestedBy

8b. If slot is TAKEN:
      service-availability  publishes BookingResponse  →  booking.rejected
9b. service-notification  consumes from  booking.rejected
      logs / sends REJECTION notification to requestedBy
```

---

## Flow Walkthrough

The booking lifecycle has three distinct phases: **synchronous intake**, **asynchronous availability check**, and **notification dispatch**. Here is what happens at each phase in plain language.

### Phase 1 — Synchronous Intake (Client → Gateway → Booking Service)

When a client sends a `POST /bookings` request, the **API Gateway** (`api-gateway-camel`) is the only service that ever faces the public network. Built on Apache Camel's REST DSL, the gateway performs several checks before anything else happens:

1. **Content-Type guard** — If the `Content-Type` header is not `application/json`, the request is rejected immediately with `415 Unsupported Media Type`. Nothing downstream is touched.
2. **JSON deserialisation** — The raw request body is parsed into a `BookingRequest` object using Jackson. If the JSON is malformed or a date field uses the wrong format (e.g. `"25-06-2026"` instead of `"2026-06-25"`), a `400 Bad Request` is returned with a descriptive error message.
3. **Field validation** — Required fields (`classroomId`, `requestedBy`, `date`, `timeSlot`) are checked individually. Any missing field triggers a `400` with the specific field name in the message.
4. **HTTP forward** — Once the request passes all gateway checks, Camel serialises the `BookingRequest` back to JSON and forwards it over HTTP to `service-booking:8081/bookings`.

`service-booking` applies its own layer of Bean Validation (`@Valid`) and additionally verifies that `endTime > startTime`. If everything is valid, service-booking:
- Generates a unique `bookingId` in the form `BK-<8hexChars>` (e.g. `BK-23a7d08d`)
- Stamps the request with `status = PENDING`
- Publishes the enriched `BookingRequest` as a JSON message to the `booking.requested` RabbitMQ queue
- Returns `202 Accepted` with the enriched booking object

The gateway proxies this `202` response straight back to the client. **At this point the client has a booking reference but no outcome yet** — the result will be determined asynchronously.

> **Key design decision:** The gateway is intentionally stateless. It does not store any booking state; it only validates and routes. All persistent state lives in `service-availability`.

---

### Phase 2 — Asynchronous Availability Check (RabbitMQ → Availability Service)

The `booking.requested` queue acts as a buffer that decouples the client-facing layer from the availability check. As soon as the message lands in the queue, `service-availability` picks it up via a Spring AMQP `@RabbitListener`.

The availability check uses a **half-open interval query** against its in-memory H2 database:

```sql
-- Conflict exists when:
WHERE existing.startTime < :requestedEnd
  AND existing.endTime   > :requestedStart
  AND existing.classroomId = :classroomId
  AND existing.date        = :date
```

This correctly handles all overlap scenarios:

| Situation | Outcome |
|-----------|---------|
| No existing bookings for that classroom+date | AVAILABLE → CONFIRMED |
| New slot is an exact duplicate | UNAVAILABLE → REJECTED |
| New slot overlaps the start of an existing slot | UNAVAILABLE → REJECTED |
| New slot overlaps the end of an existing slot | UNAVAILABLE → REJECTED |
| New slot is entirely within an existing slot | UNAVAILABLE → REJECTED |
| New slot completely wraps an existing slot | UNAVAILABLE → REJECTED |
| New slot starts exactly when an existing one ends (adjacent) | AVAILABLE → CONFIRMED |
| New slot ends exactly when an existing one starts (adjacent) | AVAILABLE → CONFIRMED |
| Same time slot but different classroom | AVAILABLE → CONFIRMED |

If the slot is **available**, the service persists the booking to H2 (committing it so concurrent requests for the same slot will be rejected) and publishes a `BookingResponse` with `status = CONFIRMED` to the `booking.confirmed` queue.

If the slot is **unavailable**, the service publishes a `BookingResponse` with `status = REJECTED` and the conflict reason to the `booking.rejected` queue. Nothing is written to the database.

> **Note on concurrency:** Because the availability check and the persist are a single database operation scoped in a `@Transactional` method, two simultaneous requests for the same slot will serialize at the database level. Exactly one will win; the other will see the conflict and be rejected.

---

### Phase 3 — Notification Dispatch (RabbitMQ → Notification Service)

`service-notification` listens on both `booking.confirmed` and `booking.rejected` queues simultaneously via two separate `@RabbitListener` methods. Whichever queue receives a message first triggers the corresponding handler.

For a **confirmed** booking, the notification service builds a message in the form:
```
Your booking BK-23a7d08d for CR-101 on 2026-06-25 (09:00 - 10:00) is CONFIRMED
```

For a **rejected** booking:
```
Your booking request for CR-101 on 2026-06-25 has been REJECTED – Time slot is already booked
```

Both messages are logged (simulating an email / SMS gateway) and appended to the in-memory `NotificationLog`. The log is keyed by `bookingId`, so if the same event is delivered more than once (a legitimate scenario in at-least-once AMQP delivery), the duplicate is silently dropped — **idempotency is built in**.

The notification service exposes `/test/notifications` and `/test/notifications/summary` REST endpoints that the BDD tests use to verify that the correct notification was sent without needing a real email server.

---

### End-to-End Timeline

From the client's perspective, the entire flow typically completes in **< 2 seconds** under normal load:

| Stage | Typical latency |
|-------|----------------|
| Gateway validation + HTTP forward | ~20–50 ms |
| service-booking publish + 202 response | ~10–30 ms |
| **Client receives 202 Accepted** | **~50–80 ms total** |
| RabbitMQ delivery to availability service | ~5–20 ms |
| Availability check + DB write + publish | ~10–30 ms |
| RabbitMQ delivery to notification service | ~5–20 ms |
| Notification logged | ~1–5 ms |
| **Full pipeline complete** | **~100–200 ms after 202** |

---

## RabbitMQ Topology

```
Exchange: classroom.booking.exchange  (topic, durable)
│
├── routing key: booking.requested  →  Queue: booking.requested
│                                        (DLX: classroom.booking.dlx)
│
├── routing key: booking.confirmed  →  Queue: booking.confirmed
│
└── routing key: booking.rejected   →  Queue: booking.rejected
```

All queues are **durable**. The `booking.requested` queue has a dead-letter exchange configured so messages that fail all retry attempts are routed to `booking.dead-letter` instead of being lost.

---

## REST API

### `GET /bookings`

Returns gateway metadata (service name, version, available endpoints).

**Response** `200 OK`:
```json
{
  "service": "api-gateway-camel",
  "description": "Classroom Booking API Gateway",
  "endpoints": [
    "POST /bookings",
    "POST /bookings/batch",
    "GET /bookings",
    "GET /actuator/health"
  ]
}
```

### `POST /bookings`

Accepts a classroom booking request and returns immediately with `202 Accepted`.

**Request** (`Content-Type: application/json`):
```json
{
  "classroomId": "CR-101",
  "date":        "2026-06-25",
  "timeSlot": {
    "startTime": "09:00",
    "endTime":   "10:00"
  },
  "requestedBy": "alice@example.com"
}
```

**Success Response** `202 Accepted`:
```json
{
  "bookingId":   "BK-23a7d08d",
  "classroomId": "CR-101",
  "date":        "2026-06-25",
  "timeSlot": {
    "startTime": "09:00",
    "endTime":   "10:00"
  },
  "requestedBy": "alice@example.com",
  "status":      "PENDING"
}
```

**Error Response** `400 Bad Request` (validation failure):
```json
{
  "error":   "Bad Request",
  "message": "classroomId must not be blank"
}
```

### `GET /actuator/health`

Standard Spring Boot Actuator health endpoint.

```json
{ "status": "UP", "components": { "rabbit": { "status": "UP" }, ... } }
```

### `POST /bookings/batch`

Accepts a JSON array of booking requests and processes each individually. Returns `200 OK` with an array of results.

**Request** (`Content-Type: application/json`):
```json
[
  {
    "classroomId": "CR-101",
    "date": "2026-06-25",
    "timeSlot": { "startTime": "09:00", "endTime": "10:00" },
    "requestedBy": "alice@example.com"
  },
  {
    "classroomId": "CR-102",
    "date": "2026-06-25",
    "timeSlot": { "startTime": "09:00", "endTime": "10:00" },
    "requestedBy": "bob@example.com"
  }
]
```

**Success Response** `200 OK`:
```json
[
  { "bookingId": "BK-23a7d08d", "classroomId": "CR-101", "status": "PENDING", ... },
  { "bookingId": "BK-9bc4e1f2", "classroomId": "CR-102", "status": "PENDING", ... }
]
```

Items that fail validation will include an `error` field instead of `bookingId`.

---

## Validation Rules

All validation is applied at the gateway and/or service-booking layer:

| Rule | HTTP Status | Message |
|------|-------------|---------|
| `classroomId` missing or blank | `400` | `classroomId must not be blank` |
| `requestedBy` missing or blank | `400` | `requestedBy must not be blank` |
| `date` missing | `400` | `date must not be null` |
| `timeSlot` missing | `400` | `timeSlot must not be null` |
| `endTime` ≤ `startTime` | `400` | `endTime must be after startTime. Received: HH:mm - HH:mm` |
| Invalid date format (not `yyyy-MM-dd`) | `400` | `Invalid request body: Cannot deserialize…` |
| Malformed JSON body | `400` | `Invalid request body: …` |
| Booking service unreachable | `503` | `Booking service is currently unavailable` |

---

## Async Processing Pipeline

Once a booking enters the RabbitMQ pipeline, the outcome depends entirely on availability:

| Scenario | Gateway Response | Async Outcome |
|----------|-----------------|---------------|
| Slot is free | `202 PENDING` | → `CONFIRMED` notification |
| Exact same slot already booked | `202 PENDING` | → `REJECTED` notification |
| Overlapping slot (start/end overlap) | `202 PENDING` | → `REJECTED` notification |
| Adjacent slot (new starts when previous ends) | `202 PENDING` | → `CONFIRMED` notification |
| Different classroom, same time | `202 PENDING` | → `CONFIRMED` notification |

---

## BDD Test Suite

The `integration-tests` module contains a full **Cucumber 7** acceptance test suite that validates the complete system when all services are running.

### Feature Files

| Feature File | Tag(s) | What It Tests |
|---|---|---|
| `api_gateway.feature` | `@api @camel` | Gateway validation, HTTP status codes, Content-Type, health endpoint |
| `audit_logging.feature` | `@audit @camel` | Audit trail creation, chronological ordering, 404 for unknown IDs |
| `availability_check.feature` | `@availability` | Overlap detection logic directly against the availability service |
| `batch_booking.feature` | `@batch @camel @split-aggregate` | Batch submission, mixed results, single-item batch |
| `classroom_booking.feature` | `@booking` | End-to-end booking flow via gateway → async notification |
| `end_to_end_flow.feature` | `@end-to-end` | Full system flow, concurrent requests, sequential bookings |
| `messaging_flow.feature` | `@messaging @rabbitmq` | RabbitMQ queue declarations, payload validation, message routing |
| `notification.feature` | `@notification` | Notification content, idempotency, multiple events |

### Step Definition Classes

| Class | Feature(s) |
|-------|-----------|
| `ApiGatewayStepDefinitions` | `api_gateway.feature` |
| `AuditStepDefinitions` | `audit_logging.feature` |
| `AvailabilityStepDefinitions` | `availability_check.feature` |
| `BatchBookingStepDefinitions` | `batch_booking.feature` |
| `ClassroomBookingStepDefinitions` | `classroom_booking.feature` |
| `EndToEndStepDefinitions` | `end_to_end_flow.feature` |
| `MessagingFlowStepDefinitions` | `messaging_flow.feature` |
| `NotificationStepDefinitions` | `notification.feature` |
| `CommonStepDefinitions` | Shared hooks + cross-feature steps |

### Test Infrastructure

The BDD suite starts a minimal Spring Boot context (`webEnvironment = NONE`) with:
- `RestTemplate` — HTTP calls to all running services
- `RabbitTemplate` + `RabbitAdmin` — publish and inspect RabbitMQ queues
- `ObjectMapper` — JSON serialisation of DTOs
- `ScenarioContext` — scenario-scoped bean that carries state between step definitions

`@Before` hook clears notification log + availability store before every scenario.  
`@After` hook purges all queues after every scenario.

---

## Technology Stack

| Component | Technology |
|-----------|-----------|
| Language | Java 17 |
| Framework | Spring Boot 3.3.5 |
| API Gateway | Apache Camel 4.8.0 (REST DSL + platform-http + Route Slip EIP) |
| Messaging | RabbitMQ 3.13 + Spring AMQP |
| Persistence | H2 in-memory + Spring Data JPA (availability), MongoDB 7 + Spring Data MongoDB (audit) |
| Build | Maven 3 (multi-module) |
| Containerisation | Docker / containerd (Rancher Desktop) |
| Orchestration | Kubernetes via k3s (Rancher Desktop) |
| Ingress | Traefik (pre-installed in Rancher Desktop) |
| Autoscaling | Kubernetes HPA (CPU 70% / Memory 80%, min 2 / max 6 replicas) |
| BDD Testing | Cucumber 7.18 + JUnit Platform Suite |
| Code Generation | Lombok 1.18 |
| JSON | Jackson 2.17 + JavaTimeModule |

---

## Project Structure

```
BDD_POC/
├── pom.xml                          ← Parent POM (dependency management)
│
├── common-lib/                      ← Shared DTOs, enums, utilities
│   └── src/main/java/com/classroom/common/
│       ├── dto/                     ←  BookingRequest, BookingResponse, TimeSlot, …
│       ├── enums/                   ←  BookingStatus, AvailabilityStatus
│       └── util/                    ←  RabbitMQConstants, BookingIdGenerator, …
│
├── api-gateway-camel/               ← Apache Camel gateway (port 8080)
│   └── src/main/
│       ├── java/com/classroom/gateway/
│       │   ├── route/BookingRoute.java       ← REST DSL + Route Slip dispatcher
│       │   ├── processor/                    ← Camel processors (validation, serialization, batch)
│       │   └── config/JacksonConfig.java
│       └── resources/
│           ├── application.yml               ← Service URLs, route-slip config
│           └── camel/routes.xml              ← XML DSL routes (validate, submit, audit, batch)
│
├── service-booking/                 ← Booking acceptor (port 8081)
│   └── src/main/java/com/classroom/booking/
│       ├── controller/              ←  POST /bookings
│       ├── service/                 ←  ID assignment, validation, publish
│       └── messaging/               ←  BookingProducer → booking.requested
│
├── service-availability/            ← Availability + persistence (port 8082)
│   └── src/main/java/com/classroom/availability/
│       ├── messaging/               ←  Consumer + producer
│       ├── service/                 ←  Overlap detection
│       ├── repository/              ←  JPA overlap query
│       └── controller/              ←  /test/* endpoints for BDD
│
├── service-notification/            ← Notification dispatcher (port 8083)
│   └── src/main/java/com/classroom/notification/
│       ├── messaging/               ←  Consumers for confirmed/rejected
│       ├── service/                 ←  Message building + NotificationLog
│       └── controller/              ←  /test/* endpoints for BDD
│
├── service-audit/                   ← Audit trail service (port 8084)
│   └── src/main/java/com/classroom/audit/
│       ├── controller/              ←  POST /audit/bookings, GET /audit/bookings/{id}
│       ├── service/                 ←  Audit event persistence
│       └── model/                   ←  MongoDB document models
│
├── integration-tests/               ← Cucumber BDD acceptance tests
│   └── src/test/
│       ├── java/com/classroom/bdd/
│       │   ├── config/              ←  Spring context, ScenarioContext, Suite
│       │   └── steps/               ←  Step definition classes
│       └── resources/
│           ├── features/            ←  .feature files (Gherkin scenarios)
│           ├── application-test.yml ←  Test profile (RabbitMQ + service URLs)
│           └── cucumber.properties
│
├── k8s/                             ← Kubernetes manifests
│   ├── kustomization.yaml           ←  kubectl apply -k k8s/
│   ├── namespace.yaml
│   ├── configmap.yaml               ←  Service URLs, route-slip pipeline config
│   ├── rabbitmq-*.yaml              ←  RabbitMQ deployment, service, PVC, secret
│   ├── mongodb-deployment.yaml      ←  MongoDB deployment + service
│   ├── mongodb-secret.yaml          ←  MongoDB credentials
│   ├── api-gateway-*.yaml
│   ├── service-booking.yaml
│   ├── service-availability.yaml
│   ├── service-notification.yaml
│   ├── service-audit-deployment.yaml
│   ├── service-audit-service.yaml
│   ├── ingress.yaml                 ←  Traefik → /bookings, /actuator
│   └── hpa.yaml                     ←  Auto-scale gateway 2→6 replicas
│
└── docker-compose.yml               ← Placeholder (all infra is deployed via Kubernetes k8s/)
```

