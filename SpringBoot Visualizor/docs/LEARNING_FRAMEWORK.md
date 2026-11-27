# SpringLab: Learning and Visualization Requirements (permanent)

These are product-wide requirements for every learning/visualization module (REST Request Lifecycle,
JWT Security Simulator, JPA Relationship Lab, and any later module). They were set by the project owner
after Phase 2 and are binding. Phases 1 and 2 (users table, registration, login, JWT security) are done and must
not be changed by this work.

**Learning flow of every module: Understand → Visualize → Experiment → Observe → Explain → Test.**

---

## 1. Requirements (verbatim intent)

The platform is an interactive Spring Boot concept visualization and experimentation environment, not a set of static tutorials.

### 5W1H explanation (every module)
1. **What**: what is the concept?
2. **Why**: why does it exist, what problem does it solve?
3. **Who**: which Spring components, classes, users or database entities participate?
4. **Where**: where does it operate in the application architecture?
5. **When**: at what point in the execution lifecycle does it operate?
6. **How**: how does it work, step by step?

### Visualization
- React Flow (`@xyflow/react`) for architecture and relationship diagrams where appropriate.
- CSS transitions + React state for simple animations.
- Highlight the currently executing component; animate requests, responses and data movement.
- Play, Pause, Next Step and Reset controls.
- Code inspector showing relevant Java code and annotations.
- Detailed explanation panel.
- "What If" experiments: the learner changes inputs/configuration and observes consequences.
- Diagrams responsive and usable on mobile.

### Technical accuracy
- Distinguish conceptual simulations from actual Spring Boot runtime execution.
- Never claim simulated SQL is actual Hibernate-generated SQL.
- Accurate Spring Security, Spring MVC, JPA and Hibernate terminology.
- Explain the *consequences* of configuration changes, not just change labels.
- State assumptions wherever behaviour depends on configuration or version.
- No animation may contradict actual backend behaviour.

### Priority
1. REST Request Lifecycle  2. JWT Security Simulator  3. JPA Relationship Lab.
Complete, working modules before decorative animation or additional modules.

---

## 2. Architectural decisions from the requirements review

### 2.1 One scenario model, two sources (the central decision)
SIMULATED and LIVE modes must render through the **same** UI. Both produce a `Scenario`/`Trace` object:

```
Scenario {
  id, title, module,
  mode: "SIMULATED" | "LIVE",
  assumptions: [ "Spring Boot 3.5.x, Spring Security 6.5.x, Hibernate 6.6.x", "open-in-view=false", ... ],
  explain: { what, why, who[], where, when, how[] },          // 5W1H
  nodes: [ { id, label, kind, position } ],                   // React Flow
  edges: [ { id, from, to, label } ],
  steps: [ {
     id, title, activeNodes[], activeEdges[], payload?,        // data moving along the edge
     explanation, terminology[],
     codeRef: { file, language, lines[], annotationNotes[] },
     provenance: "CAPTURED" | "DERIVED" | "CONCEPTUAL"        // per-step truthfulness
  } ],
  whatIf: [ ... ]                                             // see 2.5
}
```
- SIMULATED: scenario JSON bundled with the frontend (authored, reviewed).
- LIVE: the backend executes real code in a sandbox and returns the same shape, built from what it observed.
- **Provenance is per step**, because a LIVE run still contains conceptual steps. Example: for a LIVE lifecycle trace the
  controller/service/repository method calls and the executed SQL are `CAPTURED`; the real security filter names are
  `CAPTURED` (read from `FilterChainProxy`); the DispatcherServlet handler-adapter detail is `CONCEPTUAL` and shown as such.
  The UI badge reflects the weakest provenance in view; no step may show LIVE styling if it is not CAPTURED.

### 2.2 Backend: sandboxed `lab` package (no change to Phase 1/2 code)
- New package `com.springlab.lab.*` with shared `LabTrace` DTOs (records) and a `TraceCollector` (request-scoped, thread-bound).
- Lab endpoints are authenticated (any role), whitelisted by scenario name, **never** accept user-supplied SQL/JPQL/class names.
- Lab data lives in separate `lab_*` tables (Flyway V2+). JPA scenarios run in a transaction that is **rolled back**.
- SQL is captured with a Hibernate `StatementInspector` (`hibernate.session_factory.statement_inspector`). It shows the
  SQL text Hibernate prepares (with `?` placeholders), not bound values and not "what the database did". The UI says so.
- The JWT simulator uses a **separate lab signing key** held in a plain class (`LabJwtService`), **not** a second `JwtDecoder`
  bean. A second `JwtDecoder`/`JwtEncoder` bean would make injection ambiguous and could change what the real resource
  server uses. Phase 2 security wiring stays untouched. The real signing secret is never exposed.
- `GET /api/lab/meta` returns the actual running versions (Spring Boot, Spring Security, Hibernate) and relevant effective
  settings (e.g. `open-in-view`), so the "assumptions" shown to learners come from the runtime, not from memory.
- Security rules for lab endpoints: size/time limits on inputs, no secrets in responses, normal 401/403 behaviour.

### 2.3 Source of the code shown in the Code Inspector
Hand-copied snippets drift from reality. Rule: every `codeRef` points to a real file and line range in this repository.
A backend test (`CodeReferenceTest`) verifies each referenced excerpt still exists in the source file, so a refactor that
breaks an excerpt fails the build. Snippets that illustrate concepts not in this repo (e.g. a framework internal) are
explicitly labelled "illustrative excerpt, not SpringLab source".

### 2.4 "Truthfulness tests" (new test category)
Each LIVE scenario gets a backend test asserting that the visualized claim is what actually happened, e.g.:
- the N+1 scenario really issues 1 + N select statements (counted via the collector);
- the LAZY scenario with `open-in-view=false` outside a transaction really throws `LazyInitializationException`;
- an expired / tampered / wrong-key lab token is really rejected by the real Nimbus decoder;
- a request without a token is really answered 401 and a wrong-role request 403.
If the framework behaviour and the animation disagree, the test fails.

### 2.5 What If experiments: how they stay honest
Spring/Hibernate configuration cannot be flipped at runtime safely, and mutating global config from a web request would be
unsafe. Therefore every What If is one of two kinds, labelled as such:
- **LIVE What If**: the backend runs a *predefined real variant* of the code (e.g. a lab entity mapped LAZY vs. another mapped
  EAGER; a token that is expired / tampered / signed with another key / carries another role; cascade ALL vs. none). The
  learner chooses the variant; the result is a real new trace. Global application configuration is never mutated.
- **SIMULATED What If**: only for settings that cannot be run safely (e.g. `ddl-auto`, `open-in-view=true`, CSRF on/off).
  Each outcome is authored, must name the real documented behaviour it models, state assumptions/versions, and be labelled SIMULATED.
Every What If result shows: *what changed → what happened → why (consequence) → how to see it in real code*.

### 2.6 Frontend structure (new; nothing existing is modified)
```
frontend/src/learning/
  engine/        simulationReducer.js, useSimulation.js     # state machine, pure + unit-tested
  components/
    ModuleShell.jsx          # Understand→Visualize→Experiment→Observe→Explain→Test stages
    Explain5W1H.jsx          # reusable; schema requires all six sections
    SimulationControls.jsx   # Play, Pause, Next, Back, Reset, speed, "Step n of N" (aria-live)
    FlowCanvas.jsx           # @xyflow/react wrapper: active-node highlight, animated edges, mobile mode
    AnimatedEdge.jsx         # moving data packet along the edge (SVG/CSS), respects reduced motion
    CodeInspector.jsx        # highlighted lines for the current step, file path, annotation callouts
    ExplanationPanel.jsx     # step detail, terminology, assumptions/version notes
    ModeBadge.jsx            # LIVE / SIMULATED (+ per-step provenance); text + icon, never colour alone
    WhatIfPanel.jsx          # declarative controls → run variant → before/after + consequence
    AssumptionsList.jsx      # versions and config the explanation depends on
    TestYourself.jsx         # links to Challenge Arena questions for this module's topic
  scenarios/     restLifecycle.sim.js, jwtSecurity.sim.js, jpaRelationships.sim.js   # SIMULATED data
  api/           labApi.js                                                        # LIVE traces
```
- **Engine** (`useSimulation`): states `idle | playing | paused | finished`; actions `play, pause, next, back, reset, setSpeed, goTo`.
  Timer cleanup on pause/unmount, `Next` while playing pauses first, `Reset` returns to step 0 and idle. Honors `prefers-reduced-motion`
  (auto-play off, transitions removed; stepping still works). Pure reducer so it can be unit tested without a DOM.
- **Mobile**: React Flow canvas with touch pan/zoom, `fitView`, nodes non-draggable, MiniMap hidden on small screens, `preventScrolling`
  off so the page can scroll past the diagram. Below `md` the layout stacks (diagram → controls → explanation → code) and offers a
  vertical-list rendering of the same steps as an accessible alternative to the canvas.
- **Accessibility**: all controls keyboard-operable; current step announced via `aria-live="polite"`; highlight uses outline + label, not colour only.
- **Frontend tests**: add Vitest + React Testing Library when the framework is built (engine reducer, 5W1H schema completeness
  for every scenario, ModeBadge never shows LIVE for non-CAPTURED steps).
- **Libraries (decide at implementation, verify current versions then)**: `@xyflow/react` (v12), a light Java-capable syntax highlighter
  (e.g. `prism-react-renderer`), `recharts` only for the teacher dashboard.

### 2.7 Terminology rules (review checklist for every scenario)
Use: `DispatcherServlet`, `HandlerMapping`, `HandlerAdapter`, `SecurityFilterChain`, `DelegatingFilterProxy`/`FilterChainProxy`,
`BearerTokenAuthenticationFilter`, `JwtDecoder`, `SecurityContextHolder`, `AuthenticationEntryPoint`, `AccessDeniedHandler`,
`persistence context`, `EntityManager`, `flush`, `dirty checking`, `lazy proxy`, `owning side` / `mappedBy`.
Avoid: calling the persistence context "the cache" without qualification, saying Hibernate "stores JWTs", saying `@Transactional`
"opens a connection" (it binds a transaction/EntityManager; connection acquisition may be deferred), claiming SQL runs at `save()` (it runs at flush).

### 2.8 Required content per module
Each module ships: 5W1H, assumptions list, a SIMULATED walkthrough, a LIVE run wherever it can be done safely, ≥3 What If
experiments (each with a stated consequence), a code inspector bound to real source, and a Test-yourself link with
questions seeded for that topic.

---

## 3. Updated development plan

| Phase | Scope | Status |
|---|---|---|
| 1 | Foundation (Maven, Vite, MySQL, Flyway V1, users) | Done |
| 2 | Authentication and security | Done |
| 3 | Admin: user/role management, seeded admin, DB-authoritative roles/enabled | Done |
| 4 | Challenge Arena + Teacher dashboard (needed so each module's "Test" step is real) | Done |
| 5 | **Learning framework + REST Request Lifecycle** (engine, shared components, 5W1H, lab `TraceCollector`, SIMULATED + LIVE) | Done, see `REST_LIFECYCLE_MODULE.md` |
| 6 | **JWT Security Simulator** (lab key, token variants, real filter-chain facts, What Ifs) | Done, see `JWT_SIMULATOR_MODULE.md` |
| 7 | **JPA Relationship Lab** (`lab_*` entities, rollback scenarios, SQL capture, LAZY/EAGER/N+1/cascade What Ifs) | |
| 8 | Hardening: full test pass, accuracy review against §2.7, accessibility/mobile pass, docs | |

Rationale: the framework is built together with its first real module (REST Lifecycle, the simplest to capture truthfully)
so it is proven by a working vertical slice rather than designed in the abstract, and the Security and JPA modules then reuse it.
Phase 4 stays before the modules so "Test" links to real questions.

## 3a. "Test yourself" contract (implemented in Phase 4)

Every learning module links to the Challenge Arena with a URL; the arena pre-selects the options and the learner presses Start:

```
/arena?topic=<topic-slug>[&difficulty=EASY|MEDIUM|HARD][&count=1..20]
```

Seeded topic slugs (stable; part of the contract): `java-core`, `spring-boot-basics`, `rest-lifecycle`, `jwt-security`, `jpa-hibernate`.
The REST Lifecycle module uses `rest-lifecycle`, the JWT Security Simulator `jwt-security`, the JPA Lab `jpa-hibernate`.
`GET /api/topics` returns each topic with its count of active questions (a module can hide the link when the count is 0).
Correct answers and explanations are only returned after an attempt is submitted; the module never needs them client-side.

## 4. Risks specific to this requirement set
1. **Fake LIVE**: labelling scripted output as LIVE. Mitigated by per-step provenance + truthfulness tests.
2. **Animation/behaviour drift** after refactors. Mitigated by `CodeReferenceTest` and truthfulness tests in CI.
3. **Unsafe What If**: letting a web request change global config or run arbitrary SQL. Mitigated by whitelisted predefined variants.
4. **Shared captured state** across threads/users (SQL collector). Mitigated by request-scoped, thread-bound collector.
5. **Version-dependent behaviour** (Hibernate 6.x SQL shape, Spring Security defaults). Mitigated by `/api/lab/meta` and an assumptions list.
6. **Mobile usability of node-link diagrams**: mitigated by stacked layout and a list alternative.
7. **Scope**: three complete modules before any decoration or extra module.
