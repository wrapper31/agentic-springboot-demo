# order-service

A small but realistically layered Spring Boot service used to pilot agentic AI tooling
(coding agents, MCP servers, Spring AI) before applying it to larger, access-restricted services.

It mirrors a typical enterprise setup: REST in, Kafka in and out, MongoDB as the system of record,
and DynamoDB for an append-only audit trail.

## Architecture

```
api (REST controllers, DTOs, error mapping)
  -> service (business rules, the only layer that coordinates the others)
       -> persistence.mongo   (orders collection, system of record)
       -> persistence.dynamo  (order-audit table, state history)
       -> messaging           (publishes orders.events, consumes payments.events)
domain (Order, OrderItem, OrderStatus: plain records, no framework types)
config (AppProperties, DynamoDB client/table, Kafka topics, Clock)
```

Order lifecycle: `CREATED -> PAID -> SHIPPED`, `CREATED -> PAYMENT_FAILED`, or `CREATED -> CANCELLED`.
Every transition is saved to MongoDB, audited in DynamoDB, then published to `orders.events`.

## API

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/orders` | Create an order |
| GET | `/api/orders/{id}` | Get an order |
| GET | `/api/orders?customerId=` | List a customer's orders |
| POST | `/api/orders/{id}/cancel` | Cancel a `CREATED` order |
| POST | `/api/orders/{id}/ship` | Ship a `PAID` order (`carrier`, `trackingNumber`) |
| GET | `/api/orders/{id}/history` | Audit history from DynamoDB |

## Kafka

| Topic | Direction | Payload |
|---|---|---|
| `orders.events` | produced, key = orderId | `OrderEvent` (`ORDER_CREATED`, `ORDER_STATUS_CHANGED`) |
| `payments.events` | consumed | `PaymentEvent` (`SUCCEEDED` / `FAILED`) |

## Agent tooling

- `CLAUDE.md`: architecture, layering rules and conventions for AI coding agents.
- `tools/codebase-mcp`: a Spring AI MCP server exposing endpoints, Kafka topics, data models and
  type usages of any Spring Boot codebase. See its [README](tools/codebase-mcp/README.md).

## Build and test

Requires Java 21 and Docker.

```bash
mvn verify            # unit, web-layer and Testcontainers integration tests
```

## Run locally

```bash
docker compose up -d
DYNAMODB_ENDPOINT=http://localhost:4566 DYNAMODB_CREATE_TABLE=true \
AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test \
mvn spring-boot:run

curl -X POST localhost:8080/api/orders -H 'Content-Type: application/json' \
  -d '{"customerId":"c-1","items":[{"sku":"BOOK-1","quantity":2,"unitPrice":12.50}]}'
```
