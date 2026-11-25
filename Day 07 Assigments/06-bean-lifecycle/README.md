# 6. Bean Lifecycle: Initialization and Destruction

**Concept:** Spring Core. `DatabaseClient` opens a real MySQL connection in `@PostConstruct`, uses it in a business method, and closes it in `@PreDestroy`. The program starts the context and **explicitly closes** it. The order is recorded twice: numbered console steps, and rows in a database table.

## Tech

Java 17 · Maven · Spring Framework 6.1.14 · `jakarta.annotation-api` 2.1.1 (for `@PostConstruct` / `@PreDestroy`) · MySQL 8

**Database:** shared Day 07 database `Day_007_Assigments`. Table **`a06_lifecycle_events`** uses the `a06_` prefix, so it is uniquely identifiable as Assignment 6. It holds one row per phase that has a connection, grouped by `run_id`.

## Structure

```text
src/main/java/com/company/spring
├── client/DatabaseClient          @Component: constructor, setter injection, @PostConstruct,
│                                  business method, @PreDestroy
├── lifecycle/LifecycleLog         numbers every step (STEP 1, STEP 2 ...)
├── config/
│   ├── DbConfig                   DataSource from db.properties
│   ├── AppConfig                  DbConfig + component scan of client/
│   └── BrokenDbConfig             DataSource to a non-existent database (edge case)
├── extension/                     optional extension
│   ├── LegacyDatabaseClient       no annotations; @Bean(initMethod = "connect", destroyMethod = "disconnect")
│   ├── AllCallbacksBean           every lifecycle mechanism in one bean
│   └── ExtensionConfig
└── app/
    ├── LifecycleApp               main program
    ├── LifecycleEdgeCasesApp      never closed / failing init / shutdown hook
    └── InitDestroyComparisonApp   initMethod/destroyMethod vs annotations vs interfaces
```

## How to run

1. Run `src/main/resources/schema.sql` in MySQL. It drops/recreates only `a06_lifecycle_events`.
2. In Eclipse: **File → Import → Maven → Existing Maven Projects**, select `06-bean-lifecycle`, and set the JRE to **JavaSE-17**.
3. Run `LifecycleApp`, `LifecycleEdgeCasesApp` and `InitDestroyComparisonApp`.

## Observed output: `LifecycleApp`

```text
main: creating the Spring context...
   STEP 1  | DatabaseClient       | constructor - dataSource is null (not injected yet)
   STEP 2  | DatabaseClient       | dependency injected - DataSource set
   STEP 3  | DatabaseClient       | @PostConstruct - connection OPENED to Day_007_Assigments
main: context started - DatabaseClient is ready to use

main: calling the business method twice...
   STEP 4  | DatabaseClient       | business method - 'customer statement generated' saved; rows for this run = 2
   STEP 5  | DatabaseClient       | business method - 'monthly report exported' saved; rows for this run = 3

main: calling context.close() explicitly...
   STEP 6  | DatabaseClient       | @PreDestroy - connection CLOSED (isClosed=true)
main: context closed

Rows written to a06_lifecycle_events in this run (run_id 764768f5):
   ----------------------------------------------------------------------
   1    POST_CONSTRUCT  connection opened              2026-10-11 11:20:24.041
   2    BUSINESS        customer statement generated   2026-10-11 11:20:24.084
   3    BUSINESS        monthly report exported        2026-10-11 11:20:24.134
   4    PRE_DESTROY     closing connection             2026-10-11 11:20:24.142
   ----------------------------------------------------------------------
```

**Recorded order:**

```text
constructor → (dependency injection) → @PostConstruct → business method → @PreDestroy
```

- **Steps 1–3 happen inside `new AnnotationConfigApplicationContext(...)`,** before `main` gets the bean. When `getBean` returns, the client is already connected.
- **The constructor cannot use the database:** the `DataSource` is still `null` there. That's why the connection is opened in `@PostConstruct`.
- **The database rows confirm the order,** with millisecond timestamps. `@PreDestroy` wrote its row *before* closing the connection.

## Edge cases: `LifecycleEdgeCasesApp`

```text
CASE 1 - context created but NEVER closed

   STEP 1  | DatabaseClient       | constructor - dataSource is null (not injected yet)
   STEP 2  | DatabaseClient       | dependency injected - DataSource set
   STEP 3  | DatabaseClient       | @PostConstruct - connection OPENED to Day_007_Assigments
   ...main moves on without calling close(). No @PreDestroy line appears
   for this context - its connection stays open until the JVM dies.
   (context still active: true)

CASE 2 - @PostConstruct throws (database does not exist)

   STEP 1  | DatabaseClient       | constructor - dataSource is null (not injected yet)
   STEP 2  | DatabaseClient       | dependency injected - DataSource set
WARNING: Exception encountered during context initialization - cancelling refresh attempt:
         ... BeanCreationException: Error creating bean with name 'databaseClient': Invocation of init method failed

   STARTUP FAILED in the init phase:
   BeanCreationException: Error creating bean with name 'databaseClient'
   Root cause: SQLSyntaxErrorException: Unknown database 'day_007_database_that_does_not_exist'
   Constructor and injection ran (STEP 1-2); the bean never became usable.

CASE 3 - registerShutdownHook(): no close() call, but cleanup still happens

   STEP 1  | DatabaseClient       | constructor - dataSource is null (not injected yet)
   STEP 2  | DatabaseClient       | dependency injected - DataSource set
   STEP 3  | DatabaseClient       | @PostConstruct - connection OPENED to Day_007_Assigments
   main() is ending now. Watch for @PreDestroy AFTER this line:
   STEP 4  | DatabaseClient       | @PreDestroy - connection CLOSED (isClosed=true)
```

1. **Forgetting to close the context means `@PreDestroy` never runs.** The connection from case 1 was never released. The database shows it too: that run has a `POST_CONSTRUCT` row but **no `PRE_DESTROY`** row. Check with:
   ```sql
   SELECT run_id, GROUP_CONCAT(phase ORDER BY event_id) AS phases
   FROM Day_007_Assigments.a06_lifecycle_events GROUP BY run_id;
   ```
2. **An exception in `@PostConstruct` stops the whole application.** Spring reports `Invocation of init method failed`, cancels startup, and nothing can use a half-initialised bean. Construction and injection had succeeded, because no connection is attempted until init. A broken configuration is therefore found at startup, not at the first request.
3. **`registerShutdownHook()` tells the JVM to close the context when the program ends.** `@PreDestroy` runs *after* `main()` returns. Spring Boot registers this hook automatically.

## Optional extension: `@Bean(initMethod, destroyMethod)` (`InitDestroyComparisonApp`)

```text
main: creating the Spring context...
   STEP 1  | LegacyDatabaseClient | constructor (DataSource via constructor)
   STEP 2  | LegacyDatabaseClient | initMethod connect() - connection OPENED
   STEP 3  | AllCallbacksBean     | constructor
   STEP 4  | AllCallbacksBean     | BeanNameAware.setBeanName("allCallbacksBean")
   STEP 5  | AllCallbacksBean     | @PostConstruct
   STEP 6  | AllCallbacksBean     | InitializingBean.afterPropertiesSet()
   STEP 7  | AllCallbacksBean     | @Bean initMethod customInit()
main: context started

   STEP 8  | LegacyDatabaseClient | business method ping() - connection valid = true
   STEP 9  | AllCallbacksBean     | business method doWork()

main: calling context.close() explicitly...
   STEP 10 | AllCallbacksBean     | @PreDestroy
   STEP 11 | AllCallbacksBean     | DisposableBean.destroy()
   STEP 12 | AllCallbacksBean     | @Bean destroyMethod customDestroy()
   STEP 13 | LegacyDatabaseClient | destroyMethod disconnect() - connection CLOSED
main: context closed
```

**`LegacyDatabaseClient`** has **no annotations at all**. Its plain `connect()` and `disconnect()` methods became lifecycle callbacks only through `@Bean(initMethod = "connect", destroyMethod = "disconnect")`.

**`AllCallbacksBean`** shows the fixed order when several mechanisms are combined:
- **Init:** `@PostConstruct` → `afterPropertiesSet()` → `initMethod`
- **Destroy:** `@PreDestroy` → `destroy()` → `destroyMethod`

**Beans are destroyed in reverse order of creation:** `AllCallbacksBean`, created second, is destroyed first. Something created later may depend on something created earlier, so it must be cleaned up first.

| | `@PostConstruct` / `@PreDestroy` | `@Bean(initMethod, destroyMethod)` | `InitializingBean` / `DisposableBean` |
|---|---|---|---|
| Declared in | The class (jakarta annotations) | The **configuration**, not the class | The class (implements Spring interfaces) |
| Class depends on | Jakarta annotation API only | Nothing; plain Java | Spring itself |
| Best for | Your own classes (most common) | **Classes you can't edit** (library classes) | Rarely used today; ties the class to Spring |
| Order if combined | 1st | 3rd | 2nd |

Bonus: a `@Bean` whose class has a public `close()` or `shutdown()` method gets it called automatically on destroy (inferred `destroyMethod`), which is how connection pools are closed.

## Explanation

**Bean lifecycle.** For each singleton, the container runs these phases:

1. **Instantiate:** call the constructor (with constructor-injected dependencies).
2. **Populate:** inject setter/field dependencies.
3. **Aware callbacks:** e.g. `BeanNameAware`.
4. **Initialize:** `@PostConstruct` → `afterPropertiesSet()` → `initMethod`.
5. **Ready:** the bean is handed out and used by the application.
6. **Destroy** (when the context closes): `@PreDestroy` → `destroy()` → `destroyMethod`.

Prototype beans go through phases 1–5 only. Spring does not destroy them (see Assignment 4).

**Initialization callbacks (`@PostConstruct`):**
- run once, after **all dependencies are injected** and before the bean is used
- the place to open connections, load caches, validate configuration, or start timers
- if init fails, the application refuses to start, so problems surface immediately

**Destruction callbacks (`@PreDestroy`):**
- run once, when the container is **closed** (`close()`, or a shutdown hook)
- the place to release resources: close connections, flush buffers, stop threads
- if the context is never closed, they never run (case 1)
