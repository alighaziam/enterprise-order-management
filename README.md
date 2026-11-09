# Enterprise Order Management

A portfolio-grade enterprise order management system built as a **modular monolith** using Java 25, Spring Boot, Spring Modulith, PostgreSQL, and Flyway.

## Architecture

The system is one deployable application with explicit business capability boundaries. Each module owns its domain model and persistence. Cross-module collaboration must happen through published module APIs or domain events—not by accessing another module's repositories or tables.

Planned modules:

| Module | Responsibility |
|---|---|
| customer | Customer profiles and lifecycle |
| catalog | Product information and pricing |
| inventory | Stock availability and reservations |
| order | Order lifecycle and orchestration |
| payment | Payment authorization and capture boundary |
| shipping | Shipment and delivery lifecycle |

Long-running workflows will use saga-style orchestration. Reliable asynchronous event publication will use a transactional outbox where required. These patterns will be introduced with concrete use cases rather than added speculatively.

## Technology

- Java 25
- Spring Boot 4.1.2
- Spring Modulith 2.1.2
- PostgreSQL 17
- Flyway
- Maven

## Run locally

```bash
docker compose up -d
mvn spring-boot:run
```

Application: http://localhost:8080

## Build and test

```bash
mvn clean verify
```

## First API

- `POST /api/v1/customers` — create a customer
- `GET /api/v1/customers/{id}` — retrieve a customer

Example request:

```json
{
  "fullName": "Ada Lovelace",
  "email": "ada@example.com"
}
```

## Design rules

1. Organize code by business module, then by technical responsibility inside each module.
2. A module owns its entities, repositories, and schema.
3. No cross-module repository or table access.
4. Keep module APIs small and explicit.
5. Use domain events for decoupled collaboration.
6. Extract a microservice only when independent deployment, scaling, or ownership justifies its operational cost.

## Status

- [x] Repository bootstrap and PostgreSQL Compose setup
- [x] Spring Boot application skeleton
- [x] Customer creation and retrieval API
- [x] Customer Flyway migration
- [x] Spring Modulith boundary verification test
- [x] GitHub Actions Maven verification workflow
- [ ] Catalog and inventory
- [ ] Order lifecycle and invariants
- [ ] Payment and shipping integration boundaries
- [ ] Transactional outbox and saga workflow
