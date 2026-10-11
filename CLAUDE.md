# CLAUDE.md

Guidance for AI coding agents (and humans) working in this repository.

## What this is

`order-service`: a layered Spring Boot 3.5 / Java 21 microservice. REST in, Kafka in and out,
MongoDB as the system of record, DynamoDB as an append-only audit trail. It is a pilot for applying
agentic AI tooling to larger enterprise services with the same shape, so keep the patterns here
representative of production code rather than clever.

## Commands

```bash
mvn -B verify                                   # full build: unit, web, architecture and integration tests
mvn -B test -Dtest=OrderServiceTest             # one test class
mvn -B test -Dtest='!OrderFlowIntegrationTest'  # everything except the Docker-based test (fast loop)
mvn -B compile                                  # quick compile check
```

Integration tests use Testcontainers (MongoDB, Kafka, LocalStack for DynamoDB) and need Docker.
In Claude Code cloud sessions the SessionStart hook starts Docker for you.

## Layout and layering rules

```
com.example.orders
├── api          REST controllers, request/response DTOs (api.dto), ApiExceptionHandler
├── service      business rules; the ONLY layer that coordinates persistence + messaging
├── domain       plain records (Order, OrderItem, OrderStatus, AuditEntry); no framework imports
├── persistence
│   ├── mongo    OrderDocument + Spring Data repository (orders collection)
│   └── dynamo   OrderAuditRecord + repository (order-audit table, AWS SDK v2 enhanced client)
├── messaging    Kafka publisher (orders.events) and listener (payments.events), event records
└── config       AppProperties (prefix `app`), DynamoDB client/table, Kafka topics, Clock
```

These rules are enforced by `ArchitectureTest` (ArchUnit). Fix the design, never the rule:

- `api` calls `service` only. Controllers never touch repositories, documents or audit records.
- `service` is called by `api` and `messaging` (listeners). It owns all state transitions.
- `persistence` is used only by `service` and `config`.
- `messaging` publishers are used only by `service`.
- `domain` has no Spring, AWS, Kafka or Jackson imports.
- Map between layers explicitly: `OrderDocument.fromDomain/toDomain`, `OrderResponse.from`.
  Do not return persistence types from the service or domain types from controllers.

## Conventions

- Constructor injection only; no field `@Autowired` in main code.
- Records for DTOs, events and domain types. Domain state changes return new instances (`withStatus`).
- Every order state change goes through `OrderService.transition`: save to MongoDB, write an audit
  row to DynamoDB, publish `ORDER_STATUS_CHANGED`. New transitions must reuse it.
- Kafka payloads are JSON strings serialized with the Spring `ObjectMapper`; keys are `orderId`.
  Consumers must be idempotent (see `applyPaymentResult` ignoring non-`CREATED` orders).
- Errors: throw a domain exception from `service`; map it to a `ProblemDetail` in `ApiExceptionHandler`.
- Time comes from the injected `Clock`, never `Instant.now()` in `service`, so tests can fix it.
- Configuration lives under `app.*` in `application.yml` and is bound by `AppProperties`.
  Environment-specific values come from env vars (`MONGODB_URI`, `KAFKA_BOOTSTRAP_SERVERS`, `DYNAMODB_ENDPOINT`, ...).

## Tests

| Layer | Style | Example |
|---|---|---|
| service | plain JUnit + Mockito, fixed `Clock` | `OrderServiceTest` |
| api | `@WebMvcTest` + `@MockitoBean` service | `OrderControllerTest` |
| end to end | `@SpringBootTest` + `TestcontainersConfiguration` | `OrderFlowIntegrationTest` |
| architecture | ArchUnit | `ArchitectureTest` |

When adding a feature, add or extend a test at each layer you touched. A new endpoint needs a
`@WebMvcTest` case; new Kafka behaviour needs coverage in the integration test.

## Adding a typical feature (checklist)

1. Domain: new fields/status in `domain`, kept framework-free.
2. Persistence: update `OrderDocument` mapping (and `OrderAuditRecord` if the audit shape changes).
3. Service: business rule + transition via `transition(...)`; unit test it.
4. Messaging: new event type constant in `OrderEvent` or a new listener calling the service.
5. API: DTO + controller method + exception mapping; `@WebMvcTest` it.
6. Docs: update the API / Kafka tables in `README.md`.
7. Run `mvn -B verify` before opening a PR.
