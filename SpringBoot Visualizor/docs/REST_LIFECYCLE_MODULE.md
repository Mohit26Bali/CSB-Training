# REST Request Lifecycle module (Phase 5)

The first learning module and the proof of the learning framework described in `LEARNING_FRAMEWORK.md`.
Route: `/learn/rest-lifecycle`. Flow: Understand → Visualize → Experiment → Observe → Explain → Test.

## What is LIVE and what is SIMULATED

| Part | Mode | Source of truth |
|---|---|---|
| Walkthrough of a generic request | SIMULATED | `frontend/src/learning/scenarios/restLifecycle.sim.js`; every step is CONCEPTUAL, no SQL, no evidence |
| "Send real request" | LIVE | one real `GET` to a catalogued SpringLab endpoint with the learner's own token |
| LIVE experiments (no token, tampered token, insufficient role, missing resource) | LIVE | a modified copy of a real request; the server is never reconfigured |
| Open-in-view and role-claim experiments | SIMULATED | explanation only, with a pointer to where the real behaviour is documented |

Each step carries a **provenance**, and the badge is derived from it by one function (`badgeFor`):

| Provenance | Badge | Meaning |
|---|---|---|
| CAPTURED | LIVE | observed while the real request ran |
| DERIVED | INFERRED | inferred from captured facts plus SpringLab's code; the text says what was inferred |
| CONCEPTUAL | SIMULATED | explains the framework; nothing was observed (for example "MySQL executes the statement") |

A LIVE trace therefore contains steps of all three kinds. Only CAPTURED steps may carry SQL text.

## How a request is traced (all in `com.springlab.lab`)

0. **Ownership ticket (added in the Phase 6 follow-up).** The browser first calls `POST /api/lab/traces/tickets` with the
   signed-in user's real token and receives a random, single-use, 60-second ticket bound to that user's database id.
   A traced request can itself be anonymous or carry a bad token, so ownership cannot come from the request being traced.
1. The browser adds `X-SpringLab-Trace: <ticket>` to a `GET` for one of the endpoints in `lab/lifecycle-catalog.json`
   (the allow-list: `/api/auth/me`, `/api/topics`, `/api/admin/users`, `/api/challenges/0`). Anything else, and any
   missing, unknown, expired or already-used ticket (including the old literal `1`), is not traced.
2. `LifecycleTraceFilter` (order -101, just before Spring Security's -100) binds a `TraceCollector` to the thread, reads the
   configured chain for the URL from `FilterChainProxy.getFilters(path)`, and replies with `X-SpringLab-Trace-Id`.
3. While the request runs, three observers feed the collector (and are no-ops for every other request):
   `LabTraceAspect` (service and repository calls: type, method, duration, exception class name),
   `LabTraceInterceptor` (the controller method DispatcherServlet chose, its mapping pattern and the authenticated
   principal; this marks the switch from the SECURITY phase to the HANDLER phase),
   `CapturingStatementInspector` (a Hibernate `StatementInspector` that receives each SQL string).
4. `LifecycleStepAssembler` (a pure function) turns the observations into ordered steps with provenance.
5. The trace is kept in `TraceStore` (200 entries, 5 minutes) together with its owner's database id. It is fetched with
   `GET /api/lab/traces/{id}` and **only the owner can read it**: anyone else (including an administrator) gets the same
   404 as for an unknown id, so nothing reveals that the trace exists. Capacity and expiry apply whoever the owner is.

| Ownership claim | Verified by |
|---|---|
| Owner reads (200); another authenticated user and an admin get 404 identical to an unknown id; no token gets 401 | `TraceOwnershipIntegrationTest` (two users + admin), live script |
| Ownership follows the ticket's holder, not the traced request (anonymous traced request still owned) | `aTracedRequestWithoutAnyTokenIsStillOwnedByWhoeverHeldTheTicket`, `usingSomeoneElsesTicketMakesThemTheOwnerNotYou` |
| Tickets are random, single-use, expiring, bounded; one winner under concurrent redemption | `TraceTicketStoreTest` |
| Owner-bound store: wrong owner finds nothing; capacity and expiry unchanged | `TraceInfrastructureTest` |
| Six users tracing at once: distinct traces, each owned correctly, no SQL leaking between requests | `TraceConcurrencyIntegrationTest` (committed data, real threads), live script |

Never recorded: header values, tokens, request or response bodies, method arguments, return values, exception messages,
bound SQL parameters.

## SQL capture: what it is and is not

* Source: the string Hibernate passes to JDBC for each statement, via `hibernate.session_factory.statement_inspector`.
* It contains `?` placeholders. Bound values are not available and are not shown.
* It proves Hibernate prepared that text. It does **not** show what MySQL returned or how it executed it, so the
  "MySQL executes the statement" step is CONCEPTUAL.
* Only statements issued through Hibernate are seen (not Flyway or plain JDBC).
* The inspector always returns the SQL unchanged and only records when the current thread runs a traced request.

## Assumptions the lessons state

Shown in the UI (static ones from the scenario file, versions and settings read live from `GET /api/lab/meta`):
servlet stack with embedded Tomcat, JWT resource server with HS256, authorities read from the database each request,
controllers return DTOs, `spring.jpa.open-in-view=false`, SQL text caveats above.

## Claim → test map (truthfulness)

| Claim shown to the learner | Verified by |
|---|---|
| Captured SQL count = what Hibernate prepared | `LifecycleTraceTruthfulnessIntegrationTest` compares each trace with Hibernate's `Statistics#getPrepareStatementCount()` for every role and scenario; the live script compares it with MySQL's own statement counters (per-schema digest counters, because other applications on the same server pollute the global `Com_select` counter) |
| Each catalogued endpoint returns the documented status / controller invoked / SQL count, per role | `everyCatalogTargetBehavesAsDocumentedForEveryRole` (expectations live in `lifecycle-catalog.json`) |
| Each LIVE experiment behaves as its "Why" text says, per role | `everyLiveExperimentBehavesAsClaimedForEveryRole` |
| No token / tampered token never reach a controller or the database | `anonymousAndTamperedRequestsNeverReachApplicationCodeOrTheDatabase` |
| A 403 costs exactly one SQL statement (the role lookup) | `forbiddenRequestShowsOnlyTheRoleLookupAndNoController` |
| Authentication SQL happens before the controller; handler SQL after | `meEndpointShowsAuthenticationLookup...` (phase and trigger of each statement) |
| Captured SQL has placeholders and never contains the bound username | same test |
| The listed security filters are the real chain for the URL | `securityFilterListEqualsTheChainSpringSecurityReportsForThatUrl` |
| "@Transactional on this method" matches the annotation | `topicsEndpointRunsOneQuery...` (independent reflection) |
| 404 path: handler ran, service threw `ResourceNotFoundException`, advice step is DERIVED | `missingChallengeReachesApplicationCodeAndIsTranslatedTo404` |
| Provenance rules (SQL only on CAPTURED, database step CONCEPTUAL, DERIVED where inferred) | `LifecycleStepAssemblerTest` and the invariants asserted in every integration scenario |
| No tokens, hashes, e-mail or secrets in a trace; trace endpoint needs auth | `tracesAreReadableOnlyWithAuthenticationAndNeverContainSecrets` |
| Tracing is ignored for non-catalogued paths, non-GET, other header values; bodies unchanged | `tracingIsIgnoredUnless...`, `tracedRequestsReturnTheSameBodyAsUntracedOnes` |
| Versions / settings shown are the running ones; nothing else leaks | `FrameworkMetaIntegrationTest` |
| Every code excerpt equals the real source lines; every note token is on its line | `CodeReferenceTest` |
| Store is bounded and expires; collector isolates threads and attributes SQL | `TraceInfrastructureTest` |
| Simulation state machine (play / pause / next / back / reset, timers, reduced motion) | `simulationReducer.test.js`, `useSimulation.test.jsx` |
| 5W1H complete; SIMULATED scenario has no SQL/evidence; only CAPTURED reads LIVE | `scenario.test.js`, `components.test.jsx` |

## Adding or changing code excerpts

`docs/tools/gen_code_refs.py` regenerates `code-references.json` from the real files. If you edit a listed source file,
`CodeReferenceTest` fails until the line numbers in the script are updated and it is run again.

## Known limits

* Self-invocations inside a service and calls that bypass Spring proxies are not observed.
* The live-trace allow-list is four GET endpoints; tracing a write request is intentionally impossible.
* Timings are single measurements and vary.
* Only the REST Request Lifecycle module exists; the JWT Security Simulator and JPA Relationship Lab are later phases.
