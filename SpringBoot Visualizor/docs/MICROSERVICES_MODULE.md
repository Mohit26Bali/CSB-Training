# Microservices Architecture Lab (Phase 9)

Route: `/learn/microservices`. Flow: Understand → Visualize → Experiment → Observe → Explain → Test.
Built on the learning framework (`LEARNING_FRAMEWORK.md`). It adds 8 topics and 37 experiments that run **simulated services** inside the
SpringLab JVM against the local MySQL database, and the `microservices` Challenge Arena topic (34 questions).

## What this is, and what it is not

SpringLab itself is a **modular monolith**: one Spring Boot application, one deployable, one MySQL schema. The Microservices Lab is a
**simulator inside that monolith**. It exists to teach the ideas of a microservice architecture (service boundaries, synchronous calls and
events, a gateway, discovery, data ownership, sagas, resilience, tracing) with observable, repeatable behaviour.

It is **not a deployed microservices system**:

* There is **no network**. The services are Java classes in one JVM that call each other through an in-process, typed `ServiceBus`.
  Nothing is serialised or sent anywhere.
* The services are **not independently deployed, scaled or restarted**, and a "service going down" is injected behaviour, not a crashed process.
* There is **no message broker**. The event channel is an in-memory list for one run. It is not Kafka or RabbitMQ.
* The registry is a small simulated component. It is not Eureka or Consul. The circuit breaker, retry, bulkhead and idempotency code is
  SpringLab's own, written for teaching. It is not Resilience4j. Spans and correlation ids illustrate tracing inside one process. This is not
  OpenTelemetry.
* The databases are **logically** separated (see below), not physically.

The lab states this on the page: a persistent banner, a "What is real here, and what is simulated" panel, and the limitations the server
returns in `GET /api/lab/microservices/meta`.

## The eight services and their ownership

`com.springlab.lab.micro`. Each service has a typed contract (`contract` package) and owns its tables; other services reach its data only through
that contract. Tables are created by `V8__micro_lab.sql`, plus `micro_lab_orders.unit_price` by `V9__micro_lab_order_snapshot.sql`.

| Service | Responsibility | Owned tables |
|---|---|---|
| Gateway (simulated) | Authenticates a credential value held in lab state, rate-limits per client in a fixed window on the simulated clock, routes | none |
| User | Customer accounts for the lab | `micro_lab_users` |
| Product | Catalogue and prices | `micro_lab_products` |
| Order | Places orders; orchestrates the saga; handles choreography events; outbox, dual-write and relay | `micro_lab_orders`, `micro_lab_saga_steps`, `micro_lab_outbox` |
| Inventory | Stock, reservations, release | `micro_lab_stock`, `micro_lab_reservations` |
| Payment | Charges and refunds; idempotent by key | `micro_lab_payments` |
| Notification | Consumes events; naive and idempotent consumer | `micro_lab_notifications`, `micro_lab_processed_events` |
| Registry (simulated) | Registrations, heartbeats, health, round-robin lookup | `micro_lab_registry` |

`micro_lab_runs` is the lab's own bookkeeping (run state and stored result), not a service table. A separate `MonolithOrderWorkflow` exists only
for comparison: it is the one class allowed to touch several services' tables in one transaction, which is the point of the comparison.

## Core mechanisms

* **ServiceBus.** Every call is `bus.call(from, Operation, request, options)` and returns a `CallResult` with an `Outcome`: `SUCCESS`,
  `BUSINESS_FAILURE`, `UNAVAILABLE`, `TIMEOUT`, `TECHNICAL_ERROR`, `BULKHEAD_REJECTED`, `CIRCUIT_OPEN`. A business refusal (for example a declined
  payment) is a valid answer, not a dependency failure. Each call records a span with parent, caller, callee, kind (`SYNC` or `EVENT`) and outcome.
* **Simulated time.** `SimClock` is a logical clock. Delays, timeouts and backoff advance it; the lab never sleeps, so runs are deterministic.
  Injected behaviours are `UP`, `DOWN`, `DELAYED`, `REQUEST_LOST` and `ERROR`, per service or per instance.
* **Real concurrency limit.** Bulkheads are real `java.util.concurrent.Semaphore`s; what is simulated is only the work that holds a permit.
* **EventChannel (SIMULATED).** An in-memory list for one run with explicit delivery. Events stay pending until delivered. A delivery to an
  unavailable consumer fails and the event stays pending (at-least-once). Every published entry is distinct and delivered separately, even when
  two entries share a `messageId`; recognising the duplicate is the consumer's job. The lab makes no claim of exactly-once delivery.
* **Retry** is bounded (`maxAttempts` counts the first call) with deterministic backoff 100 ms doubling, capped at 800 ms, on the simulated
  clock. It retries `UNAVAILABLE`, `TECHNICAL_ERROR`, `BULKHEAD_REJECTED`, and `TIMEOUT` only for an idempotent operation. It never retries a
  `BUSINESS_FAILURE` or a call refused by an open breaker.
* **Circuit breaker.** `CLOSED` → `OPEN` after the configured consecutive failures; after the open duration the next call is the single probe
  (`HALF_OPEN`): success closes it, failure reopens it. Calls refused while open never reach the service (`CIRCUIT_OPEN`) and may be answered by a fallback.
* **Idempotency.** Payment stores the result under the caller's key, so a retried charge returns the first result instead of charging again.
  Notification's idempotent consumer records the message id with its effect in one local transaction.
* **Registry TTL.** An instance is fresh while its last heartbeat is no older than 3000 simulated ms. A crashed instance that sent no
  deregistration is still returned until it expires.

## Data ownership: database per service, and its limit

Each service uses its own `JdbcTemplate`/transaction manager over a connection wrapped by `OwnershipGuardedDataSource`. A statement that
mentions a `micro_lab_*` table the service does not own is refused before it reaches the driver, and the refusal is recorded. A source-level
test (`ServiceBoundaryTest`) also fails the build if service code reaches into another service's tables or classes.

This is **logical isolation**. All the tables are in one MySQL schema and one server; nothing in MySQL stops a statement, so the lab does.
With separate physical databases the database server itself would refuse (different credentials and servers). Foreign keys exist only inside a
service's own tables; across services a table holds an id, and consistency is the application's job.

## Outbox, saga and recovery

* **Dual-write gap.** Committing the order and then publishing the event are two writes; a failure between them leaves an order with no event.
* **Transactional outbox.** The order and its outbox row are written in one local transaction, so they commit or vanish together. A relay
  publishes pending rows later and marks them published. If the relay fails after publishing but before marking, the row is published
  again: delivery is **at-least-once**, and the consumer must be idempotent.
* **Saga by orchestration.** The Order service coordinates Inventory (reserve), Payment (charge) and the confirmation. On failure it runs
  compensations in reverse order (release the reservation, refund). A compensation is a **new business operation**, not a database rollback:
  the reserved stock was committed and leaves a `RELEASED` reservation as history. A compensation can itself fail; the saga then ends
  `COMPENSATION_FAILED` and **nothing recovers it automatically**. The experiment performs an explicit resume (`resumeCompensation`) once
  Inventory is back. The saga log records every step.
* **Saga by choreography.** There is no coordinator. Services react to each other's events (for example Inventory reacts to a
  `PaymentFailed` event by releasing the reservation). The overall flow exists only in their combined behaviour, which is harder to follow.

## Observability

Every call is a span with `spanId`, `parentSpanId`, the correlation id, caller, callee, operation, kind, outcome, instance and timings. Timings
are kept apart: **simulated ms** come from the logical clock; **measured µs** are the real elapsed time of the in-process handler in the JVM.
They are never mixed or converted into each other. Self time is a span's duration minus its children's. Propagating the correlation id keeps
the trace connected, including across an event; dropping it leaves downstream spans as unrelated traces (the experiments show both).

## Provenance: CAPTURED, DERIVED, CONCEPTUAL

Every step carries one, shown as a badge:

| Backend | Badge | Meaning |
|---|---|---|
| `CAPTURED` | LIVE | Observed in this run: service calls and outcomes, SQL handed to the driver, values read back from MySQL, commits and rollbacks, saga log |
| `DERIVED` | INFERRED | Computed from captured observations (for example "the reservation still exists as history") |
| `CONCEPTUAL` | SIMULATED | Explanation only. Carries no SQL, no evidence, and draws no call |

The eight walkthroughs on the Visualize stage are conceptual: every step is SIMULATED and reports no observation.

## What is real, simulated and illustrative

| | |
|---|---|
| **Real** | MySQL 8.4 reads and writes through JDBC; local transactions (commit and rollback); the ownership guard's refusals; Semaphore permits; measured in-process time; the saga log and outbox rows |
| **Simulated** | The network, service processes and their failures; latency and timeouts (logical clock); the event channel; the gateway; the registry and instances; service deployment and scaling |
| **Illustrative** | Correlation ids and spans (the idea of distributed tracing); the diagram's service layout; the conceptual walkthroughs and the "what orchestration costs" style explanations |

## Run lifecycle and protections

`/api/lab/microservices` (any signed-in role; 401 without a token):

| Method and path | Purpose |
|---|---|
| `GET /meta` | Services (with owned tables), environment statement, limitations |
| `GET /topics`, `GET /experiments` | The 8 topics, and the 37 experiments with their What-If options (key, kind, bounds or choices, default) |
| `POST /runs` `{experimentId, whatIf}` | Creates a run in `CREATED`. The client sends only an experiment id and What-If values from the published options |
| `POST /runs/{id}/execute` | Executes once; a repeat returns the stored result |
| `POST /runs/{id}/reset` | Returns a finished run to `CREATED` so it can run again |
| `GET /runs/{id}` | State and, once finished, the result |

* States: `CREATED`, `EXECUTING`, `COMPLETED`, `FAILED`. Execution is guarded by a database compare-and-set from `CREATED` to `EXECUTING`, so a
  run's business operations run at most once; a concurrent execute is refused with 409.
* **Ownership.** A run belongs to its creator. A foreign, unknown or expired run id returns the same 404.
* **Limits.** At most 4 runs execute at once (409 "Too many experiments are running right now"); a user may hold 20 unfinished runs (the oldest
  finished run is recycled); a run lives 30 minutes.
* **Cleanup.** Each run removes exactly its own rows from the service tables (by a random run key) when it ends, success or failure, and reports
  the count. At startup, interrupted runs older than 10 minutes become `FAILED`, expired runs are removed, and stale service rows are deleted.
* The workflow is **not** wrapped in one transaction; each service commits its own short local transaction.
* What-If values are validated against the published options (BOOLEAN, INT with bounds, CHOICE) on the server; the form only mirrors them.

## Experiments (37)

| Topic | Experiments |
|---|---|
| Monolith vs microservices (4) | mono-order-workflow, mono-coupling, mono-boundaries, mono-over-split |
| Service communication (6) | comm-sync-order-calls, comm-sync-failure, comm-async-event, comm-async-duplicate, comm-sync-vs-async, comm-failure-semantics |
| API gateway (4) | gw-routing, gw-auth, gw-rate-limit, gw-failure |
| Service discovery (4) | disc-register-lookup, disc-load-balancing, disc-unavailable, disc-stale-registration |
| Database per service (4) | dbs-ownership-contracts, dbs-forbidden-access, dbs-foreign-keys, dbs-shared-coupling |
| Distributed transactions: outbox and saga (6) | outbox-dual-write-gap, outbox-commit-rollback, outbox-duplicate-delivery, saga-orchestrated, saga-choreographed, saga-compensation-vs-rollback |
| Resilience (5) | res-retry-backoff, res-circuit-breaker, res-idempotency-key, res-retry-amplification, res-bulkhead |
| Observability (4) | obs-trace-order, obs-failure-propagation, obs-missing-correlation, obs-async-correlation |

## Frontend

`frontend/src/learning/microLab/` (page, run view, parts), `scenarios/microLab.sim.js` (topics, 5W1H, SIMULATED walkthroughs, takeaways),
`schema/microRun.js` and `schema/microWhatIf.js` (pure helpers). Mechanisms are drawn differently, with a text label and not colour alone:
synchronous call (solid, round packet), event (dashed, square packet), local database transaction (dotted, to the service's database cylinder),
outbox relay (dash-dot), compensation (amber, long dash), refused locally (red dotted). Below the phone breakpoint the diagram is replaced
by the step's list of calls. Reduced motion removes the travelling packets. The Test stage links to `/arena?topic=microservices`
(difficulty and count are pre-selected; the learner presses Start).

## Challenge Arena topic

`V10__micro_questions.sql` adds the topic `microservices` ("Microservices Architecture") and 34 questions, each with four options and one correct
option. It reuses the existing Arena unchanged: answers and explanations are returned only after submission, scoring is on the server, an
attempt can be submitted once, and attempts belong to their owner (404 otherwise).

| Topic area | Questions |
|---|---|
| Monolith vs microservices | 3 |
| Service communication | 4 |
| API gateway | 3 |
| Service discovery | 3 |
| Database per service | 4 |
| Distributed transactions, outbox, saga | 7 |
| Resilience | 6 |
| Observability | 4 |

By difficulty: 7 easy, 16 medium, 11 hard. The correct option is spread over all four positions (8/9/8/9 for positions 1-4) with no position repeated more than twice in a row.

## Setup

Requirements: Java 17, Maven, Node.js with npm, MySQL 8.4 on the host. Docker is not required.

```bash
# environment (never committed): see README.md for the full table
export JWT_SECRET=<at least 32 bytes>   DB_USERNAME=<mysql user>   DB_PASSWORD=<mysql password>

# backend (http://localhost:8080). Flyway applies V1..V10 on startup, including V8-V10 for this module.
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev

# frontend (http://localhost:5173, proxies /api to the backend)
cd frontend && npm install && npm run dev
```

No new environment variable is needed. The lab needs migrations `V8`, `V9` and `V10`; without them the lab tables and the `microservices` topic do not exist.

## Tests

```bash
cd backend && mvn test                      # real MySQL (schema springlab_test); no Docker
cd frontend && npm run lint && npm test && npm run build
```

Backend (`com.springlab.lab.micro`): `ServiceBusTest`, `ResilienceUnitTest`, `EventChannelTest` (core, no database); `ServiceBoundaryTest` and
`OwnershipGuardTest` (ownership); `MicroRunLifecycleIntegrationTest` and `RunStateAndWhatIfTest` (ownership of runs, compare-and-set, limits,
expiry, cleanup, concurrency); one integration test per topic group; `MicroCatalogueIntegrationTest`; `MicroQuestionsIntegrationTest` (the Arena topic).

### Frontend fixture parity

The frontend validates its diagram, mechanism classification and What-If forms against `frontend/src/learning/scenarios/microCatalog.fixture.json`,
which the backend generates from real runs of all 37 experiments with default What-If values. `MicroFrontendFixtureTest` fails if the checked-in
file differs from what the backend produces. After changing an experiment, regenerate it and commit the result:

```bash
cd backend && mvn test -Dtest=MicroFrontendFixtureTest -Dmicro.fixture.write=true
```

### Code references

The Code Inspector shows excerpts of the real source with line-level notes (`backend/src/main/resources/lab/code-references.json`). The file is generated and
verified: if you edit or move lines in a referenced source file, `CodeReferenceTest` fails; update the line numbers in `docs/tools/gen_code_refs.py` and run
`python docs/tools/gen_code_refs.py`.

## Known limitations

* The fixture covers default What-If values only.
* The diagram is about 940 px tall (19 nodes); the Observe stage scrolls. A few lines cross lane labels.
* Visual checks so far were headless-Chrome screenshots of the diagram component; a manual browser acceptance pass is listed in Stage E.
* Saga recovery after a failed compensation is explicit, and shows the step; a durable, self-retrying recovery process is not implemented and not claimed.
