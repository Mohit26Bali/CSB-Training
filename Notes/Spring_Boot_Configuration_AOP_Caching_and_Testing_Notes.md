# Spring Boot: Configuration, Logging, AOP, Caching, Async, File Upload, Actuator and Testing

The "everything around the controller" notes: how a Spring Boot application gets its settings and profiles, how it logs, how **aspects** add behaviour without touching your code, how **caching**, **async** and **scheduling** work (and the one proxy trap that breaks all of them), how to accept file uploads safely, how to monitor the app with **Actuator**, and how to **test** it with JUnit 5, Mockito and MockMvc.

> **Verified against a real project.** Every snippet in this note comes from a small Spring Boot **4.1.1** project (`examples/spring-boot-features` in this repository) whose **22 tests pass**. Where Boot 3 differs, a note says so. The earlier notes (Spring Boot fundamentals, REST, JPA, Security) are the prerequisite.

---

## Table of Contents

1. [Externalized Configuration](#1-externalized-configuration)
2. [Typed Configuration with `@ConfigurationProperties`](#2-typed-configuration-with-configurationproperties)
3. [Profiles](#3-profiles)
4. [Logging](#4-logging)
5. [Proxies: The Idea Behind AOP, Caching, Async and `@Transactional`](#5-proxies-the-idea-behind-aop-caching-async-and-transactional)
6. [Aspect-Oriented Programming (AOP)](#6-aspect-oriented-programming-aop)
7. [Caching](#7-caching)
8. [Async and Scheduling](#8-async-and-scheduling)
9. [File Upload and Download](#9-file-upload-and-download)
10. [Actuator: Health and Metrics](#10-actuator-health-and-metrics)
11. [Testing in Spring Boot](#11-testing-in-spring-boot)
12. [Common Mistakes](#12-common-mistakes)
13. [Output Prediction Puzzles](#13-output-prediction-puzzles)
14. [Interview Questions](#14-interview-questions)
15. [Cheat Sheet](#15-cheat-sheet)
16. [Practice Tasks](#16-practice-tasks)

---

## 1. Externalized Configuration

**Rule of the twelve-factor app:** the same build must run in every environment; only the **configuration** changes. Spring Boot reads settings from many places and merges them.

### `application.properties` or `application.yml`

```yaml
app:
  name: Demo Shop
  mail:
    host: smtp.example.com
    port: 587
    sender: shop@example.com
    retries: 3
```

is the same as

```properties
app.name=Demo Shop
app.mail.host=smtp.example.com
app.mail.port=587
```

YAML is hierarchical and shorter for nested settings; properties files are flatter and simpler. Both work; do not put both for the same key.

### Where values come from (highest priority first, abridged)

| # | Source | Example |
|---|---|---|
| 1 | **Command-line arguments** | `java -jar app.jar --server.port=9090` |
| 2 | Java system properties | `-Dserver.port=9090` |
| 3 | **OS environment variables** | `SERVER_PORT=9090`, `APP_MAIL_HOST=...` |
| 4 | `application-{profile}.yml` next to the jar, then inside it | `application-prod.yml` |
| 5 | `application.yml` next to the jar, then inside it | |
| 6 | Defaults in code (`@Value("${x:default}")`) | |

A higher source **overrides** a lower one for the same key, key by key. This is how one jar runs everywhere: the container sets environment variables, the code ships defaults.

**Relaxed binding:** `app.mail.host` in a file equals `APP_MAIL_HOST` in the environment and `--app.mail.host` on the command line; `app.mailHost`, `app.mail-host` and `app.mail_host` all bind to the same Java property.

### `@Value` (simple, one value)

```java
@Value("${app.name}")                     // fails at start-up if missing
private String appName;

@Value("${app.timeout:30}")               // default 30
private int timeoutSeconds;
```

`@Value` is fine for one or two values. For groups of related settings use `@ConfigurationProperties` (next section): it is type-safe, validated and easier to test.

### Secrets

- **Never commit** passwords, API keys or JWT secrets. Put them in environment variables or a secret store (Vault, a cloud secret manager, Kubernetes Secrets).
- Keep an `application-local.yml` (or `.env`) in `.gitignore` for your machine, and commit an `.example` file that lists the keys with fake values.
- `spring.config.import=optional:file:./secrets.properties` loads an extra file if it exists.
- The Actuator `env` endpoint can leak settings: keep it off or secured (section 10).

---

## 2. Typed Configuration with `@ConfigurationProperties`

```java
@ConfigurationProperties(prefix = "app.mail")
@Validated
public record MailProperties(
        @NotBlank String host,
        @Min(1) @Max(65535) int port,
        @Email String sender,
        int retries) {
}
```

```java
@Configuration
@EnableConfigurationProperties(MailProperties.class)      // or put @ConfigurationPropertiesScan on the application class
public class AppConfig { }
```

```java
@RestController
class HelloController {
    private final MailProperties mail;
    HelloController(MailProperties mail) { this.mail = mail; }          // inject it like any bean
    @GetMapping("/mail-host") String host() { return mail.host() + ":" + mail.port(); }   // smtp.example.com:587
}
```

Why this is better than many `@Value`s:

| Benefit | Detail |
|---|---|
| **Type safety** | `int port` is converted for you; `Duration`, `DataSize`, enums and lists bind too (`timeout: 30s`, `max-size: 10MB`) |
| **Immutable** | a `record` (or `@ConstructorBinding` class) has no setters |
| **Validation** | add `spring-boot-starter-validation`, annotate with `@Validated` and constraints; **the application refuses to start** with a clear message if the value is invalid (a wrong config is caught at deployment, not at 3 am) |
| **One place** | the list of settings is documented by the class |
| **Testable** | build the record by hand in a unit test |

Verified behaviour: starting with `--app.mail.port=70000 --app.mail.sender=not-an-email` fails at start-up with `APPLICATION FAILED TO START` and a `BindValidationException`.

---

## 3. Profiles

A **profile** is a named set of beans and settings for an environment (`dev`, `test`, `prod`).

### Profile-specific settings

Either separate files (`application-dev.yml`, `application-prod.yml`) or one YAML with documents separated by `---`:

```yaml
spring:
  profiles:
    default: dev           # used when no profile is activated
---
spring:
  config:
    activate:
      on-profile: prod
app:
  mail:
    host: smtp.prod.example.com
    retries: 5
logging:
  level:
    demo: WARN
```

Verified behaviour with the `prod` profile: `host` and `retries` are overridden, while `port` (not mentioned in the prod document) is **inherited** from the base settings.

### Activating a profile

| How | Example |
|---|---|
| Environment variable (best in containers) | `SPRING_PROFILES_ACTIVE=prod` |
| Command line | `java -jar app.jar --spring.profiles.active=prod` |
| In a test | `@ActiveProfiles("test")` |
| Default when nothing is set | `spring.profiles.default: dev` |

Several at once: `prod,eu`. **Profile groups** (`spring.profiles.group.prod: proddb,prodmq`) switch several profiles on together.

### Profile-specific beans

```java
@Configuration
@Profile("dev")
class DevDataLoader {                     // only in dev: loads sample data
    @Bean CommandLineRunner seed(UserRepository repo) { return args -> repo.save(new User("demo")); }
}

@Service
@Profile("!prod")                         // everywhere except prod
class FakeEmailSender implements EmailSender { }
```

> **Never** let a profile decide *security*: a forgotten `prod` flag must not switch authentication off. Keep dev conveniences (H2 console, sample users, `ddl-auto=create`) out of the default settings.

---

## 4. Logging

Spring Boot logs with **SLF4J** (the API you code against) and **Logback** (the default engine). `System.out.println` has no levels, no time stamps, no thread names and cannot be switched off per package.

```java
private static final Logger log = LoggerFactory.getLogger(OrderService.class);   // one per class

log.info("Order {} placed by {}", orderId, userId);        // {} placeholders: no string concatenation cost if INFO is off
log.warn("Payment retry {} of {}", attempt, max);
log.error("Payment failed for order {}", orderId, exception);   // exception LAST: the stack trace is printed
```

(With Lombok, `@Slf4j` creates the `log` field.)

| Level | Use for |
|---|---|
| `ERROR` | something failed and needs attention |
| `WARN` | unexpected but handled (a retry, a fallback) |
| `INFO` | important business events (start-up, an order placed) |
| `DEBUG` | detail for developers |
| `TRACE` | very fine detail |

Set levels per package, without code changes:

```yaml
logging:
  level:
    root: INFO
    demo: DEBUG
    org.hibernate.SQL: DEBUG            # prints SQL
  file:
    name: logs/app.log
  pattern:
    console: "%d{HH:mm:ss.SSS} %-5level [%thread] %logger{20} - %msg%n"
```

Rules:

- **Never log secrets or personal data:** passwords, tokens, card numbers, full request bodies of login calls. Log an ID, not the user's details.
- Log an exception **once**, where it is handled, not at every layer it passes through.
- Do not log inside tight loops at `INFO`.
- `log.debug("data: " + big())` builds the string even when DEBUG is off; use `{}` placeholders or `log.atDebug().log(...)`.
- **MDC** (mapped diagnostic context) adds a request ID to every log line of a request: `MDC.put("requestId", id)` in a filter, and `%X{requestId}` in the pattern.
- In containers, log to the **console** (stdout) and let the platform collect it. Boot 3.4+ can write **structured JSON** logs with `logging.structured.format.console=ecs` (or `logstash`, `gelf`).
- Logging in `finally` blocks and inside `catch` must not throw itself.

---

## 5. Proxies: The Idea Behind AOP, Caching, Async and `@Transactional`

Many Spring features are **annotations that add behaviour around a method**: `@Transactional`, `@Cacheable`, `@Async`, `@Retryable`, `@PreAuthorize`, and your own aspects. They all work the same way.

```
caller -->  [ PROXY ]  --> real bean
            adds behaviour       (the code you wrote)
            before/after
```

Spring creates a **proxy object** that wraps your bean. Callers (other beans, controllers) get the proxy; calling a method on it first runs the extra behaviour, then your method.

- If the bean implements an **interface**, Spring may use a JDK dynamic proxy; otherwise (and by default in Boot) a **CGLIB** subclass proxy.
- Verified in the project: `AopUtils.isAopProxy(orders)` and `AopUtils.isCglibProxy(orders)` are both `true` for a service with an aspect.

### The two traps every proxy feature shares

**Trap 1: self-invocation bypasses the proxy.**

```java
@Service
public class OrderService {
    @Timed
    public String place(String item) { ... }

    public String placeTwice(String item) {
        return place(item) + ", " + place(item);      // plain Java call on "this": NO proxy, @Timed never runs
    }
}
```

Calling `place` from **inside the same class** is a normal method call on `this` (the real object), so the proxy is skipped. Verified: after `orders.placeTwice("pen")` no timing was recorded. The same applies to `@Transactional`, `@Cacheable`, `@Async`.

*Fixes:* move the called method to **another bean** (cleanest); or inject the proxy of itself (`@Lazy` self-reference); or use AspectJ weaving (rare).

**Trap 2: only `public` methods called from outside, on non-`final` classes.** A `private` or `final` method, or a `final` class, cannot be intercepted. And **fields of a proxied bean are not the real bean's fields**: the CGLIB subclass has its own, uninitialised fields, so `prices.lookups` (a public field) was `null` on the proxy in the verified project. **Always access a bean through its methods**, never its fields.

Also: the annotation only works on beans **created by Spring** (`new OrderService()` has no proxy), and the feature must be switched on (`@EnableCaching`, `@EnableAsync`, `@EnableScheduling`; `@Transactional` and AOP are enabled automatically by Boot).

---

## 6. Aspect-Oriented Programming (AOP)

**Cross-cutting concerns** such as logging, timing, auditing, security checks and retry would otherwise be copied into every method. AOP lets you write them **once** and apply them by rule.

**Analogy:** airport security. Every passenger (method call) passes through the same checkpoint (aspect) without each airline (service) building its own.

### Vocabulary

| Term | Meaning |
|---|---|
| **Aspect** | a class holding the cross-cutting behaviour (`@Aspect`) |
| **Join point** | a point in execution you can hook; in Spring AOP, always a **method call** |
| **Pointcut** | an expression selecting which join points (which methods) |
| **Advice** | the code to run: `@Before`, `@After`, `@AfterReturning`, `@AfterThrowing`, `@Around` |
| **Weaving** | linking aspects to targets; Spring does it at **run time with proxies** |

### Dependency

Boot 3: `spring-boot-starter-aop`. **Boot 4: `spring-boot-starter-aspectj`.** (Boot enables AspectJ auto-proxying automatically once it is on the classpath.)

### An `@Around` aspect and a marker annotation

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Timed { }

@Aspect
@Component
public class TimingAspect {
    private static final Logger log = LoggerFactory.getLogger(TimingAspect.class);

    @Around("@annotation(demo.aop.Timed)")                 // every method annotated with @Timed
    public Object time(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.nanoTime();
        try {
            return pjp.proceed();                          // run the real method (you MUST call it, and return its result)
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000;
            log.info("{} took {} ms", pjp.getSignature().toShortString(), ms);
        }
    }
}
```

`@Around` is the most powerful advice: it can run code before and after, change the arguments (`pjp.proceed(newArgs)`), change the result, catch or swallow exceptions, or skip the call. Forgetting `proceed()` means the real method never runs.

### Before, after returning, after throwing

```java
@Aspect
@Component
@Order(1)                                                   // lower number runs first (outermost)
public class AuditAspect {

    @Pointcut("execution(public * demo.service.OrderService.*(..))")
    void orderServiceMethods() { }                          // a named pointcut, reusable below

    @Before("orderServiceMethods()")
    public void before(JoinPoint jp) { /* jp.getSignature().getName(), jp.getArgs() */ }

    @AfterReturning(pointcut = "orderServiceMethods()", returning = "result")
    public void afterReturning(JoinPoint jp, Object result) { }

    @AfterThrowing(pointcut = "orderServiceMethods()", throwing = "ex")
    public void afterThrowing(JoinPoint jp, Exception ex) { }
}
```

Verified order for a successful call: `before place`, then `returned ordered a`; for a failing call: `before place`, then `failed empty item`. `@After` (not shown) runs in both cases, like `finally`.

### Pointcut expressions

| Expression | Selects |
|---|---|
| `execution(* demo.service.*.*(..))` | any method of any class in package `demo.service` |
| `execution(public * demo.service.OrderService.*(..))` | public methods of `OrderService` |
| `execution(* *..*Repository.save*(..))` | methods starting with `save` in any class ending in `Repository` |
| `within(demo.web..*)` | everything in `demo.web` and its sub-packages |
| `@annotation(demo.aop.Timed)` | methods carrying `@Timed` |
| `@within(org.springframework.stereotype.Service)` | all methods of classes annotated `@Service` |
| `args(String, ..)` | methods whose first parameter is a `String` |
| `bean(*Service)` | beans named `...Service` |
| `a() && !b()` | combinations with `&&`, `\|\|`, `!` |

`execution(modifiers? returnType declaringType? name(params) throws?)`; `..` means "any number of parameters" or "any sub-package".

### Typical uses and limits

| Good uses | Avoid |
|---|---|
| timing, logging entry/exit, auditing | putting **business rules** in aspects (hidden, hard to test) |
| security and permission checks | aspects that swallow exceptions silently |
| retry, rate limiting, metrics | pointcuts so broad (`execution(* *(..))`) that they slow everything |
| consistent error translation | relying on aspect order without `@Order` |

Spring AOP covers **method calls on Spring beans** only. Field access, constructors, `private` methods and objects created with `new` need full AspectJ.

---

## 7. Caching

A **cache** keeps the result of an expensive call so the next identical call is instant.

### Switch it on

```java
@Configuration
@EnableCaching
public class AppConfig { }
```

Dependency: `spring-boot-starter-cache`. With nothing else configured Boot uses a simple in-memory `ConcurrentHashMap` cache (fine for learning, but with **no expiry and no size limit**).

### The annotations

```java
@Service
public class PriceService {

    @Cacheable(cacheNames = "prices", key = "#sku", unless = "#result < 0")
    public int priceOf(String sku) {
        return slowLookup(sku);               // runs only when the cache has no entry for this sku
    }

    @CachePut(cacheNames = "prices", key = "#sku")      // always runs, then stores the result
    public int updatePrice(String sku, int newPrice) { return newPrice; }

    @CacheEvict(cacheNames = "prices", key = "#sku")    // remove one entry
    public void discontinue(String sku) { }

    @CacheEvict(cacheNames = "prices", allEntries = true)
    public void clearAll() { }
}
```

| Annotation | Behaviour |
|---|---|
| `@Cacheable` | check the cache first; if found, **skip the method**; otherwise run it and store the result |
| `@CachePut` | always run the method, **then** store the result (keep the cache fresh on updates) |
| `@CacheEvict` | remove one entry (`key`) or all (`allEntries = true`); `beforeInvocation = true` evicts even if the method fails |
| `@Caching` | combine several on one method |
| `@CacheConfig(cacheNames = "prices")` | class-level default cache name |

Options: `key` (a SpEL expression: `#sku`, `#user.id`, `#a0`), `condition` (decide **before** the call: `"#sku.length() > 3"`), `unless` (decide **after**, can use `#result`: do not cache nulls or errors), `sync = true` (only one thread computes a missing entry).

Verified behaviour: three calls to `priceOf("shoe")` ran the body **once**; `updatePrice("hat", 999)` made the next read return `999` without running the body; after `discontinue("hat")` the value was recomputed.

### Choosing a cache store

| Store | Good for | Notes |
|---|---|---|
| default (`ConcurrentMap`) | demos, tests | unbounded, no expiry |
| **Caffeine** (`spring-boot-starter-cache` + `com.github.ben-manes.caffeine`) | one-instance apps | size limit and expiry: `spring.cache.caffeine.spec=maximumSize=500,expireAfterWrite=10m` |
| **Redis** | several instances, shared cache | needs serialization of values; `spring.cache.redis.time-to-live=10m` |
| EhCache, Hazelcast | other needs | |

### Pitfalls

| Pitfall | Why it hurts | Fix |
|---|---|---|
| **Self-invocation** | verified: two calls through `cachedViaSelfCall` ran the body twice, nothing cached | call through another bean |
| **Stale data** | the database changed, the cache did not | `@CacheEvict`/`@CachePut` on every write, plus an expiry (TTL) |
| **No expiry** | memory grows and data ages forever | Caffeine or Redis with a TTL |
| **Caching mutable objects** | a caller changes the cached object and everyone sees it | return immutable objects (records) or copies |
| **Wrong key** | two different inputs share a key, or one input gets many | be explicit with `key`; remember the key includes all parameters by default |
| **Caching `null`/errors** | a failure is remembered | `unless = "#result == null"` |
| **Cache stampede** | the entry expires and 1,000 requests recompute it at once | `sync = true`, or stagger TTLs |
| **Cache with `@Transactional`** | the cache may be filled before commit, then the transaction rolls back | cache read-only data; evict after commit |
| **Per-user data in a shared key** | one user sees another's data | include the user in the key, or do not cache it |

> Rule: **measure first**. A cache is added complexity; use it for data that is expensive to compute, read much more often than written, and where slightly old data is acceptable.

---

## 8. Async and Scheduling

### `@Async`: run a method on another thread

```java
@Configuration
@EnableAsync
class AsyncConfig { }

@Service
public class ReportService {

    @Async                                                   // returns at once; the body runs on another thread
    public CompletableFuture<String> buildReport(String name) {
        return CompletableFuture.completedFuture(name + " ...");
    }
}
```

- Return `void` (fire and forget) or `CompletableFuture<T>` (so the caller can wait with `.get()` or chain with `.thenApply`).
- Same proxy rules: it must be called **from another bean**, on a public method.
- Verified: the report ran on a thread that was not `main`.
- Without configuration Boot uses a `ThreadPoolTaskExecutor` (core size 8); define your own bean (`@Bean TaskExecutor`) to set sizes and thread names, and use `@Async("myExecutor")` to pick it.
- **Exceptions** in a `void` `@Async` method are lost unless you register an `AsyncUncaughtExceptionHandler`; with a `CompletableFuture` they surface in the future.
- **Virtual threads:** with Java 21+ set `spring.threads.virtual.enabled=true` and Boot runs request handling, `@Async` and scheduled tasks on virtual threads (see the concurrency notes).
- Security context and transactions do **not** automatically travel to the other thread.

### `@Scheduled`: run something repeatedly

```java
@Configuration
@EnableScheduling
class SchedulingConfig { }

@Scheduled(cron = "0 0 2 * * *", zone = "Asia/Kolkata")    // every day at 02:00 India time
public void nightlyCleanup() { }

@Scheduled(fixedRate = 60_000)                              // every 60 s, measured start to start
@Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT30S")   // 5 minutes after the LAST run ENDED, first run after 30 s
```

| Attribute | Meaning |
|---|---|
| `fixedRate` | start every N ms regardless of how long the last run took (runs can overlap if it is slow, when async) |
| `fixedDelay` | wait N ms **after the previous run finished** (never overlaps) |
| `initialDelay` | wait before the first run |
| `cron` | a **6-field** expression: `second minute hour day-of-month month day-of-week` (Spring's cron has a seconds field, unlike Unix cron) |
| `zone` | time zone for the cron; the server's zone otherwise |

Cron quick reference: `*` every, `*/15` every 15, `1-5` range, `MON-FRI`, `0 0/30 9-17 * * MON-FRI` is every 30 minutes from 9:00 to 17:30 on weekdays. Methods must take **no arguments** and return `void`.

**Pitfalls:** by default one scheduler thread runs **all** tasks in sequence, so a slow task delays the others (`spring.task.scheduling.pool.size=5`). With **several instances** of the app, every instance runs the task: use a distributed lock (ShedLock) if it must run once. Time zones and daylight saving change cron behaviour: pin the `zone`.

---

## 9. File Upload and Download

```java
@PostMapping("/files")
public ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file) throws IOException {
    if (file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "empty file");
    String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
    String ext = original.contains(".") ? original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
    if (!ALLOWED.contains(ext)) throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "type not allowed");
    String stored = UUID.randomUUID() + "." + ext;             // never reuse the client's name
    file.transferTo(uploadDir.resolve(stored));
    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", stored));
}

@GetMapping("/files/{id}")
public ResponseEntity<Resource> download(@PathVariable String id) {
    Path file = uploadDir.resolve(id).normalize();
    if (!file.startsWith(uploadDir) || !Files.isRegularFile(file)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFileName() + "\"")
            .body(new PathResource(file));
}
```

Verified with MockMvc: a file named `../../evil name.PNG` is stored under a random `.png` name (no trace of `evil`); `run.exe` gets `415`; an empty file gets `400`; asking for `..%2F..%2Fetc%2Fpasswd` gets `404`.

### Settings

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 5MB          # per file (default 1MB)
      max-request-size: 10MB      # the whole request
```

Exceeding the limit gives `413` (`MaxUploadSizeExceededException`).

### Upload security checklist

| Risk | Defence |
|---|---|
| **Path traversal** via the file name (`../../app.jar`) | ignore the client's name; generate your own (UUID) |
| **Dangerous types** (`.jsp`, `.exe`, `.html` with script) | allow-list the extensions **and** check the content (magic bytes, e.g. Apache Tika); never trust `Content-Type` alone, the client sets it |
| **Huge files** | size limits at the server and in a reverse proxy |
| **Storing in the web root** | store **outside** any folder the server serves; deliver through a controller that checks permission |
| **Overwriting** | unique names; `Files.copy` without `REPLACE_EXISTING` |
| **Viruses** | scan if users share files |
| **Stored XSS via SVG/HTML** | serve downloads with `Content-Disposition: attachment` and `X-Content-Type-Options: nosniff` |
| **Disk full** | quotas per user; clean up temporary files |

In production prefer **object storage** (S3 and similar) with short-lived signed URLs instead of the application server's disk.

---

## 10. Actuator: Health and Metrics

**Actuator** adds production-ready endpoints: health, metrics, environment, thread dumps and more. Dependency: `spring-boot-starter-actuator`.

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info          # ONLY expose what you need
  endpoint:
    health:
      show-details: never             # or when-authorized
```

| Endpoint | Shows | Sensitive? |
|---|---|---|
| `/actuator/health` | `{"status":"UP"}`, plus components such as the database and disk | low (hide details publicly) |
| `/actuator/info` | build and git info you choose | low |
| `/actuator/metrics` | JVM memory, CPU, HTTP timings (Micrometer) | medium |
| `/actuator/prometheus` | metrics in Prometheus format (needs `micrometer-registry-prometheus`) | medium |
| `/actuator/env`, `/configprops` | configuration (secrets are masked, but not always) | **high** |
| `/actuator/heapdump`, `/threaddump`, `/loggers`, `/shutdown`, `/mappings` | internals and control | **very high** |

Verified: with `include: health,info`, `/actuator/health` returns `UP` and `/actuator/env` returns **404** (not exposed).

- **Never expose everything on the public internet** (`include: "*"`). Put the management port on an internal network (`management.server.port=9001`) or protect it with Spring Security.
- **Health groups and probes:** `/actuator/health/liveness` ("is the process alive: restart if not") and `/actuator/health/readiness` ("can it take traffic"); Kubernetes uses them. Boot 4 enables the probes by default (reported in the migration guide: check).
- Add your own indicator by implementing `HealthIndicator`.
- **Micrometer** is the metrics library behind it; `Counter`, `Timer` and `Gauge` beans let you publish business metrics (orders per minute).

---

## 11. Testing in Spring Boot

### The testing pyramid

| Level | Speed | What it checks | Tools |
|---|---|---|---|
| **Unit** (many) | milliseconds | one class in isolation | JUnit 5, Mockito, AssertJ |
| **Slice** (some) | a second | one layer with Spring (web, JPA) | `@WebMvcTest`, `@DataJpaTest` |
| **Integration** (few) | seconds | the wired application, a real database | `@SpringBootTest`, Testcontainers |
| **End to end** (fewest) | minutes | the whole system through the UI/API | Playwright, Postman |

### JUnit 5 and AssertJ essentials

```java
@Test
void totalIsSumOfLines() {
    assertThat(new Cart(List.of(10, 20)).total()).isEqualTo(30);
}

@Test
void rejectsEmptyName() {
    assertThatThrownBy(() -> new User(" ")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("name");
}

@ParameterizedTest
@CsvSource({"1,2,3", "5,5,10", "-1,1,0"})
void adds(int a, int b, int expected) { assertThat(a + b).isEqualTo(expected); }
```

| Annotation | Meaning |
|---|---|
| `@Test`, `@DisplayName` | a test, and a readable name |
| `@BeforeEach` / `@AfterEach` | run around every test (reset state here) |
| `@BeforeAll` / `@AfterAll` | once per class (`static`) |
| `@ParameterizedTest` + `@ValueSource`/`@CsvSource`/`@MethodSource` | one test, many inputs |
| `@Disabled`, `@Tag` | skip, group |
| `@Nested` | group related tests in an inner class |

AssertJ reads fluently (`assertThat(list).containsExactly(...)`, `assertThat(x).isBetween(1, 5)`) and gives clear failure messages.

### Mockito: replacing collaborators

```java
@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {
    @Mock PaymentGateway gateway;
    @InjectMocks CheckoutService service;

    @Test
    void chargesOnce() {
        when(gateway.charge("u1", 500)).thenReturn(true);       // stub
        assertThat(service.checkout("u1", 500)).isTrue();
        verify(gateway, times(1)).charge("u1", 500);            // verify the interaction
        verify(gateway, never()).refund(anyString());
    }
}
```

`when(...).thenReturn/thenThrow`, `verify`, `ArgumentCaptor`, `any()`/`anyString()`; **an unstubbed mock returns `null`/`0`/`false`/empty** (verified in the project). Mock **collaborators you own and that are slow or unpredictable**, not value objects, and do not over-verify internal calls: test outcomes more than steps.

### Slice test: only the web layer

```java
@WebMvcTest(HelloController.class)
class SliceAndMockTest {
    @Autowired MockMvc mvc;
    @MockitoBean OrderService orders;                       // Boot 3: @MockBean
    @MockitoBean MailProperties mail;

    @Test
    void controllerDelegatesToTheService() throws Exception {
        when(orders.place("lamp")).thenReturn("ordered lamp (mock)");
        mvc.perform(get("/order").param("item", "lamp"))
           .andExpect(status().isOk())
           .andExpect(content().string("ordered lamp (mock)"));
        verify(orders).place("lamp");
    }

    @Test
    void missingParameterIsABadRequest() throws Exception {
        mvc.perform(get("/order")).andExpect(status().isBadRequest());
    }
}
```

`@WebMvcTest` loads only controllers, filters and converters (fast); services are not created, so each collaborator is supplied as a mock bean.

### Full test with the real application

```java
@SpringBootTest
@AutoConfigureMockMvc                       // Boot 4: MockMvc is no longer provided automatically
class FeaturesTest {
    @Autowired MockMvc mvc;
    @Test
    void healthEndpointIsUp() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }
}
```

### Boot 4 test packages and dependencies (verified)

| Item | Boot 4 |
|---|---|
| Test starter | `spring-boot-starter-webmvc-test` (one per technology) |
| `@WebMvcTest`, `@AutoConfigureMockMvc` | `org.springframework.boot.webmvc.test.autoconfigure.*` |
| `@MockitoBean` | `org.springframework.test.context.bean.override.mockito.MockitoBean` |
| `@SpringBootTest` | `org.springframework.boot.test.context.SpringBootTest` (unchanged) |

### Testing guidance

- **Arrange, act, assert** in each test; one reason to fail per test.
- Tests must be **independent** and **repeatable**: no shared state between tests, no dependence on order, real time (inject a `Clock`) or random data.
- **Reset shared state** in `@BeforeEach` (the project clears caches and recorded events).
- Use a **real database** for repository tests where behaviour matters (Testcontainers) rather than only H2.
- Test the **sad paths**: validation errors, `404`, `403`, exceptions.
- Spring caches the application context between tests with the same configuration: avoid needless `@MockitoBean` variations, which force new contexts and slow the suite.
- A failing test that is "fixed" by loosening the assertion is not a fix.

---

## 12. Common Mistakes

| Mistake | Why | Fix |
|---|---|---|
| Calling an `@Async`/`@Cacheable`/`@Transactional` method from the same class | self-invocation bypasses the proxy | call through another bean |
| Reading a **public field** of a proxied bean | proxy field is not the real one (`null`) | use getters/methods |
| Forgetting `@EnableCaching`/`@EnableAsync`/`@EnableScheduling` | the annotations are silently ignored | add them |
| Not calling `pjp.proceed()` in `@Around` | the real method never runs | always proceed and return its result |
| Caching without expiry | stale data forever, memory growth | TTL and eviction on writes |
| `@Value` for many related settings | scattered, untyped, unvalidated | `@ConfigurationProperties` |
| Secrets in `application.yml` in git | leaked credentials | environment variables / secret store |
| Dev conveniences in the default profile | open H2 console or auto-create schema in production | put them in `dev` only |
| Logging passwords, tokens or full bodies | secret exposure | log IDs only |
| Exposing all actuator endpoints | environment, heap dumps, shutdown to the internet | expose `health` (and `info`) only |
| Trusting the uploaded file name or `Content-Type` | path traversal, disguised executables | generate names, allow-list, check content |
| Tests that depend on each other or on real time | flaky | reset state, inject `Clock` |
| `@SpringBootTest` for everything | slow suites | unit and slice tests first |
| Using `@MockBean` on Boot 4 | removed | `@MockitoBean` |
| Missing `@AutoConfigureMockMvc` on Boot 4 | no `MockMvc` bean | add it |

---

## 13. Output Prediction Puzzles

All answers below were confirmed by the project's tests.

**Puzzle 1: self-invocation and an aspect**

```java
// @Timed on place(); an @Around aspect records each timed call.
public String placeTwice(String item) { return place(item) + ", " + place(item); }
// How many timings are recorded after orders.placeTwice("pen")?
```

**Puzzle 2: caching**

```java
@Cacheable("prices") public int priceOf(String sku) { lookups++; return sku.length() * 100; }
priceOf("shoe"); priceOf("shoe"); priceOf("shoe");
// What is lookups?
```

**Puzzle 3: `@CachePut` and `@CacheEvict`**

```java
priceOf("hat");                 // 300, cached
updatePrice("hat", 999);        // @CachePut
int a = priceOf("hat");
discontinue("hat");             // @CacheEvict
int b = priceOf("hat");
// What are a and b, and how many times did the real lookup run?
```

**Puzzle 4: cache and self-invocation**

```java
public int cachedViaSelfCall(String sku) { return priceOf(sku); }   // priceOf is @Cacheable
cachedViaSelfCall("bag"); cachedViaSelfCall("bag");
// How many real lookups?
```

**Puzzle 5: profile inheritance**

```yaml
app.mail: { host: smtp.example.com, port: 587, retries: 3 }
--- (profile prod)
app.mail: { host: smtp.prod.example.com, retries: 5 }
```
With `prod` active, what are `host`, `port` and `retries`?

**Puzzle 6: override order**

```
application.yml has app.mail.port: 587; the app is started with --app.mail.port=2525
```
Which port does the bean see?

**Puzzle 7: advice order for a failing call**

```java
// @Before, @AfterReturning and @AfterThrowing on OrderService.*; place(" ") throws IllegalArgumentException("empty item")
```
Which events are recorded?

**Puzzle 8: uploads**

```java
// Upload a file named "../../evil name.PNG", then one named "run.exe", then an empty "a.png".
```
What are the three response statuses, and what is the stored name of the first?

**Puzzle 9: unstubbed mocks**

```java
@MockitoBean OrderService orders;
String s = orders.place("x");
```
What is `s`?

**Answers**

1. **None.** The two `place(...)` calls are plain calls on `this`; they never go through the proxy, so `@Timed` does not fire. (The aspect on `placeTwice` itself, if it had one, would fire once.)
2. **`1`.** The first call runs the body and stores the result; the next two are served from the cache.
3. **`a` is `999`, `b` is `300`, and the lookup ran twice** (once for the first `priceOf("hat")` and once after the eviction). `@CachePut` replaced the cached value without running `priceOf`.
4. **`2`.** Inside the same class the call is not intercepted, so nothing is cached.
5. **`host = smtp.prod.example.com`, `retries = 5`, `port = 587`.** The prod document overrides only the keys it names; the rest are inherited.
6. **`2525`.** Command-line arguments have higher priority than `application.yml`.
7. **`before place`, then `failed empty item`.** `@AfterReturning` does not run when the method throws.
8. **`201` (stored as `<random uuid>.png`, with no trace of "evil"), `415`, `400`.**
9. **`null`.** An unstubbed Mockito mock returns the default for the return type (`null` for objects).

---

## 14. Interview Questions

**1. What is AOP and why use it?**
Aspect-oriented programming separates cross-cutting concerns (logging, security, transactions) from business code, writing them once and applying them by rule instead of repeating them in every method.

**2. Explain aspect, join point, pointcut and advice.**
An aspect is a class with cross-cutting behaviour; a join point is a place where it can apply (a method call in Spring); a pointcut selects join points; advice is the code that runs (`@Before`, `@After`, `@AfterReturning`, `@AfterThrowing`, `@Around`).

**3. How does Spring AOP work?**
At run time Spring wraps beans in proxies (JDK dynamic or CGLIB). Calls from other beans go through the proxy, which runs the advice around the target method.

**4. Why does `@Transactional` (or `@Cacheable`) not work when called from the same class?**
The call is a plain method call on `this`, bypassing the proxy. Move the method to another bean.

**5. Which advice is the most powerful?**
`@Around`: it controls whether and how the target runs, can change arguments and results, and must call `proceed()`.

**6. What is the difference between `@Cacheable`, `@CachePut` and `@CacheEvict`?**
`@Cacheable` skips the method when a value is cached; `@CachePut` always runs it and refreshes the cache; `@CacheEvict` removes entries.

**7. What are the risks of caching?**
Stale data, unbounded growth, caching mutable objects or errors, wrong keys, and stampedes. Use TTLs, evict on writes and measure the benefit.

**8. `@Value` vs `@ConfigurationProperties`?**
`@Value` injects one value; `@ConfigurationProperties` binds a group into a typed, validated, immutable object and fails fast on bad config.

**9. How does Spring Boot decide which value wins when a property is in several places?**
A fixed order: command line, system properties, environment variables, profile-specific files, `application.yml`, defaults. Higher sources override lower ones.

**10. What are profiles used for, and how do you activate one?**
Environment-specific configuration and beans; `SPRING_PROFILES_ACTIVE=prod`, `--spring.profiles.active=prod`, or `@ActiveProfiles` in tests.

**11. `fixedRate` vs `fixedDelay`?**
`fixedRate` measures from start to start; `fixedDelay` waits after the previous run finishes.

**12. What is different about Spring's cron expression?**
It has six fields (the first is seconds), unlike Unix cron's five.

**13. How do you secure Actuator?**
Expose only what is needed, move management to an internal port, require authentication for sensitive endpoints, and never expose `env`, `heapdump` or `shutdown` publicly.

**14. How do you test a controller without starting the whole application?**
`@WebMvcTest` with `MockMvc` and `@MockitoBean` for the services (Boot 3: `@MockBean`).

**15. What are the main rules for a safe file upload?**
Generate your own file name, allow-list types and check content, limit size, store outside the web root, serve through a controller that checks permission, and never trust `Content-Type`.

**16. What changed in Boot 4 for tests?**
Per-technology test starters, `@MockitoBean` replaces `@MockBean`, and `@AutoConfigureMockMvc` is required for `MockMvc` in `@SpringBootTest`.

---

## 15. Cheat Sheet

| Topic | Remember |
|---|---|
| Config priority | command line > system props > env vars > profile files > `application.yml` > defaults |
| Typed config | `@ConfigurationProperties` + `@Validated` record; bad values stop start-up |
| Profiles | `SPRING_PROFILES_ACTIVE`; `on-profile` documents inherit unspecified keys |
| Secrets | environment or secret store, never git |
| Logging | SLF4J `{}` placeholders, exception last, no secrets, level per package |
| Proxy rule | features run **around** a bean; self-invocation, `private`, `final`, fields bypass or break it |
| AOP starter | Boot 3 `starter-aop`; **Boot 4 `starter-aspectj`** |
| Advice | `@Before`, `@After`, `@AfterReturning`, `@AfterThrowing`, `@Around` (call `proceed()`) |
| Pointcut | `execution(...)`, `@annotation(...)`, `within(...)`, `bean(...)` |
| Cache | `@EnableCaching`; `@Cacheable`, `@CachePut`, `@CacheEvict`; add a TTL |
| Async | `@EnableAsync`; `void` or `CompletableFuture`; own executor |
| Schedule | `@EnableScheduling`; cron has 6 fields; pin the `zone` |
| Upload | random name, allow-list, size limits, outside the web root |
| Actuator | expose `health`, `info` only; probes for Kubernetes |
| Tests | unit > slice > integration; `@MockitoBean` (Boot 4), `@AutoConfigureMockMvc` |

---

## 16. Practice Tasks

1. **Typed configuration.** Create `PaymentProperties(String provider, Duration timeout, DataSize maxAmount)` bound from `app.payment`, with validation. Start the app with a missing provider and read the failure message.
2. **Profiles.** Add `application-dev.yml` and `application-prod.yml` with different log levels and a different database URL. Start with each profile and print `environment.getActiveProfiles()`.
3. **Audit aspect.** Write an `@Around` aspect that logs the method name, the arguments and the duration of every public method in `..service..`, **without** logging any argument named `password`. Add a unit test with a fake service.
4. **Retry aspect.** Write `@Retry(times = 3)` and an aspect that retries a method that throws `IllegalStateException`, rethrowing after the last attempt. Test with a service that fails twice and then succeeds.
5. **Cache measurement.** Cache a method that sleeps 200 ms. Measure 10 calls with and without the cache. Add `@CacheEvict` on the update method and write the test that proves a stale value is never returned.
6. **Scheduler.** Add a task that runs every minute and another at 09:00 on weekdays in your time zone. Change `spring.task.scheduling.pool.size` and show how a slow task delays the other when the pool size is 1.
7. **Safe upload.** Extend the upload controller to check the first bytes of the file (PNG signature `89 50 4E 47`), reject files over 1 MB, and write tests for each rejection.
8. **Actuator.** Expose `health`, `info` and `metrics`, add a custom `HealthIndicator` that reports `DOWN` when a folder is not writable, and test the three responses with `MockMvc`.
9. **Test pyramid.** For a `UserService` with a repository, write (a) a pure Mockito unit test, (b) a `@WebMvcTest` for its controller with a mocked service, and (c) one `@SpringBootTest` that goes through the real stack.

*End of notes.*
