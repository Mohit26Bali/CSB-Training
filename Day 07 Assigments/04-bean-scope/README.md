# 4. Bean Scope: Singleton vs Prototype

**Concept:** Spring Core. The same `CounterService` class is registered twice:
- once with the **default (singleton)** scope
- once with **`@Scope("prototype")`**

Two references are obtained from the `ApplicationContext` and compared with `==`.

## Tech

Java 17 · Maven · Spring Framework 6.1.14 · no database (nothing to store in this assignment)

## Structure

```text
src/main/java/com/company/spring
├── service/CounterService      numbered instances (#1, #2 ...), per-object count, destroy() callback
├── config/
│   ├── SingletonConfig         @Bean counterService()               -> default scope (singleton)
│   └── PrototypeConfig         @Bean @Scope("prototype") counterService()
├── edgecase/
│   ├── EdgeCaseConfig          prototype CounterService used by two singletons
│   ├── ReportService           singleton with a prototype injected directly (pitfall)
│   ├── FreshReportService      singleton using ObjectProvider (fix)
│   └── WebScopeConfig          @Scope("request") outside a web app
└── app/
    ├── ScopeDemoApp            main program: singleton vs prototype
    └── ScopeEdgeCasesApp       edge cases
```

## How to run (Eclipse)

1. **File → Import → Maven → Existing Maven Projects**, select `04-bean-scope`, and set the JRE to **JavaSE-17**.
2. Run `ScopeDemoApp`, then `ScopeEdgeCasesApp` (**Run As → Java Application**).

## Observed output: `ScopeDemoApp`

```text
=== SINGLETON (default scope) ===
Starting container...
   [CounterService #1] constructor called
Container started.

first  = CounterService #1 (count=0)
second = CounterService #1 (count=0)
first == second           : true
after first.increment() x2: first.count=2, second.count=2
Is singleton? true | Is prototype? false

Closing container...
   [CounterService #1] destroy() called by Spring

=== PROTOTYPE (@Scope("prototype")) ===
Starting container...
Container started. (no constructor call yet - prototypes are created on request)

getBean #1:
   [CounterService #2] constructor called
getBean #2:
   [CounterService #3] constructor called
first  = CounterService #2 (count=0)
second = CounterService #3 (count=0)
first == second           : false
after first.increment() x2: first.count=2, second.count=0
Is singleton? false | Is prototype? true

Closing container... (watch: no destroy() for prototypes)

Total CounterService objects created in this run: 3
```

### What the output proves

| | Singleton (default) | Prototype |
|---|---|---|
| `first == second` | **true**: one object | **false**: two different objects |
| Objects created for 2 requests | 1 (`#1`) | 2 (`#2`, `#3`) |
| When created | **eagerly**, while the container starts | **lazily**, on each `getBean` |
| Shared state | `first.increment()` also changes `second` (count 2 / 2) | independent (count 2 / 0) |
| `destroy()` on container close | **called** | **not called**: Spring hands prototypes over and stops tracking them |

## Edge cases: `ScopeEdgeCasesApp`

```text
=== EDGE CASE 1 & 2: prototype inside a singleton ===
Starting container...
   [CounterService #1] constructor called
Container started.

1. Direct injection - 3 calls:
   ReportService uses CounterService #1 (count=0) -> count now 1
   ReportService uses CounterService #1 (count=1) -> count now 2
   ReportService uses CounterService #1 (count=2) -> count now 3

2. ObjectProvider - 3 calls:
   [CounterService #2] constructor called
   FreshReportService uses CounterService #2 (count=0) -> count now 1
   [CounterService #3] constructor called
   FreshReportService uses CounterService #3 (count=0) -> count now 1
   [CounterService #4] constructor called
   FreshReportService uses CounterService #4 (count=0) -> count now 1

=== EDGE CASE 3: request scope without a web application ===
Container started (request-scoped beans are not created at startup).
Asking for the request-scoped bean...
FAILED as expected:
   java.lang.IllegalStateException: No Scope registered for scope name 'request'
```

1. **A prototype injected into a singleton behaves like a singleton.** `ReportService` (singleton) received its prototype `CounterService` **once**, at creation, and reuses `#1` forever (count 1 → 2 → 3). "Prototype" means *a new object per request to the container*, not *per method call*.
2. **The fix is `ObjectProvider` (or `@Lookup`).** `FreshReportService` calls `counterProvider.getObject()` each time, so it really gets a new object (`#2`, `#3`, `#4`) on every call.
3. **Request scope needs a web application.** `@Scope("request")` in a plain `AnnotationConfigApplicationContext` fails with `No Scope registered for scope name 'request'`, because there is no HTTP request to attach the bean to.

## Explanation

**Default scope = singleton.** Unless told otherwise, Spring creates **one** instance of each bean per container:
- at startup (eagerly)
- shared by everyone who asks or is injected with it
- destroyed when the container closes (`destroy()` / `@PreDestroy`)

This is right for **stateless** services (repositories, services, controllers). Because the instance is shared by every caller and thread, it must not hold per-user or per-request data in fields. The output shows why: `second` saw `first`'s count.

**Prototype scope (`@Scope("prototype")`):**
- **A new instance for every `getBean` or injection.** Nothing is created at startup.
- **Spring creates and configures each instance but does not manage its end of life.** No destroy callback; cleanup is the caller's job.
- **Use it for stateful, short-lived objects**, where each user needs their own copy (a builder, a per-task calculator, a shopping-cart-like object outside a web app).
- **Watch the edge case above:** when a singleton needs a fresh prototype each time, inject `ObjectProvider<T>` rather than `T`.

## Optional extension: request and session scopes (web applications)

These scopes exist only in a **web-aware** `ApplicationContext`: Spring MVC, or Spring Boot with `spring-boot-starter-web`.

| Scope | One instance per… | Typical use |
|---|---|---|
| `request` (`@RequestScope`) | HTTP request; discarded when the response is sent | request ID, per-request audit/timing data |
| `session` (`@SessionScope`) | HTTP session; lives until the user's session expires | logged-in user's preferences, a shopping cart |
| `application` (`@ApplicationScope`) | `ServletContext`; whole web app | rarely needed (similar to singleton) |
| `websocket` | WebSocket session | per-connection state |

**Injecting into singletons.** A request/session bean is usually injected into a *singleton* controller or service. Spring injects a **scoped proxy** (`proxyMode = ScopedProxyMode.TARGET_CLASS`; `@RequestScope` and `@SessionScope` do this by default). Every method call on the proxy is routed to the instance belonging to the *current* request or session. This is the web equivalent of the `ObjectProvider` fix in edge case 2.

**Outside a web application** these scopes are simply not registered, which is exactly the error captured in edge case 3.
