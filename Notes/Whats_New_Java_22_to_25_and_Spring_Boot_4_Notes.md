# What Is New: Java 22 to 25, Spring Framework 7 and Spring Boot 4

The other notes target **Java 17/21** and **Spring Boot 3**. Newer projects (including the Hackathon and CI/CD projects in this repository) already use **Spring Boot 4**, and **Java 25** is the current long-term-support (LTS) release. These notes cover what changed, what breaks when you upgrade, and what to learn first.

> **How reliable is this?** Facts here come from the official Oracle "significant changes" pages for JDK 22 to 25, the Spring Boot 4.0 migration guide and release announcement, and the Spring blog. Where those sources were unclear or disagreed, the text says **"check"**. Code marked *(compiled)* was compiled and run on a real JDK (23, with preview flags where a feature was still in preview there); code marked *(not run)* could not be run locally. Always confirm exact names in the official reference for the version you use.

---

## Table of Contents

1. [Release Cadence and Which Version to Use](#1-release-cadence-and-which-version-to-use)
2. [Recap: What Java 21 Already Gave You](#2-recap-what-java-21-already-gave-you)
3. [Java 22: Unnamed Variables and Patterns](#3-java-22-unnamed-variables-and-patterns)
4. [Java 23 and 24: Smaller Steps with Big Effects](#4-java-23-and-24-smaller-steps-with-big-effects)
5. [Java 25 (LTS): The Language Gets Friendlier](#5-java-25-lts-the-language-gets-friendlier)
6. [Performance and Runtime Changes](#6-performance-and-runtime-changes)
7. [Removals and Things to Stop Using](#7-removals-and-things-to-stop-using)
8. [Spring Boot 4 and Spring Framework 7: The Big Picture](#8-spring-boot-4-and-spring-framework-7-the-big-picture)
9. [Upgrading from Boot 3 to Boot 4: What Breaks](#9-upgrading-from-boot-3-to-boot-4-what-breaks)
10. [New Spring Features Worth Learning](#10-new-spring-features-worth-learning)
11. [Reading a Boot 4 `pom.xml`](#11-reading-a-boot-4-pomxml)
12. [Common Mistakes](#12-common-mistakes)
13. [Interview Questions](#13-interview-questions)
14. [Cheat Sheet](#14-cheat-sheet)

---

## 1. Release Cadence and Which Version to Use

Java releases **every six months** (March and September). Only some releases are **LTS** (long-term support, with years of updates).

| Release | Date | LTS? | Notes |
|---|---|---|---|
| Java 17 | Sep 2021 | **Yes** | Spring Boot 3 and 4 minimum baseline |
| Java 21 | Sep 2023 | **Yes** | virtual threads, pattern matching for switch |
| Java 22 to 24 | 2024 to Mar 2025 | No | stepping stones; supported for 6 months |
| **Java 25** | **Sep 2025** | **Yes** | the current LTS |

- **For learning and new projects:** use the newest LTS (25) when your tools support it, or 21.
- **For employers:** many still run 17 or 21; know both.
- Spring Boot 4 requires **Java 17 or later** and has first-class support for Java 25.
- **Preview features** ship disabled: compile and run with `--enable-preview`. They can change or disappear. A feature that is "preview" in one release is often final two or three releases later.

---

## 2. Recap: What Java 21 Already Gave You

Covered in the earlier notes; listed here so you can see the story from 21 onward:

| Feature | Where to read |
|---|---|
| Virtual threads (JEP 444) | Multithreading notes, section 17 |
| Pattern matching for `switch`, record patterns | Modern Language Features notes |
| Sequenced collections: `getFirst()`, `getLast()`, `reversed()` on lists, deques, `LinkedHashSet`/`Map` | Collections notes |
| Generational ZGC | JVM notes |
| `Math.clamp`, `StringBuilder.repeat`, `String.indexOf(ch, from, to)` | Fundamentals notes |

---

## 3. Java 22: Unnamed Variables and Patterns

**Unnamed variables and patterns (JEP 456, final in Java 22).** Use `_` for a variable you must declare but never use.

```java
int count = 0;
for (var _ : List.of("a", "b", "c")) count++;              // loop variable not needed

Object o = new Point(3, 4);
if (o instanceof Point(int x, _)) System.out.println(x);   // only x matters

String kind = switch (shape) {
    case Circle _ -> "round";                              // do not need the matched value
    case Square _ -> "square";
    case Tri(var b, var h) -> "triangle " + b * h / 2;
};

try { Integer.parseInt(text); } catch (NumberFormatException _) { /* ignore */ }
```

*(compiled on JDK 23)*. Why it matters: the compiler and readers know the value is deliberately ignored, and linters stop warning about unused variables. Before Java 9 `_` was a legal name; from Java 9 it was reserved; from 22 it means "unnamed".

**Other Java 22 items** (from Oracle's release summary):

- **Foreign Function and Memory API (JEP 454)** became standard: call native code and use off-heap memory safely, replacing JNI for new work.
- **Launch multi-file source-code programs (JEP 458):** `java Main.java` can compile and run a program whose source is split across several files in the same folder.
- Several features continued in preview: statements before `super(...)` (JEP 447), implicitly declared classes (JEP 463), stream gatherers (JEP 461), structured concurrency (JEP 462), scoped values (JEP 464), the class-file API (JEP 457) and string templates (JEP 459).

---

## 4. Java 23 and 24: Smaller Steps with Big Effects

### Java 23

- **Markdown in documentation comments (JEP 467):** write Javadoc with Markdown (`///` comments) instead of HTML tags.
- **ZGC is generational by default (JEP 474).**
- **String templates were withdrawn.** Oracle states they will not be included; do not build on `STR."..."`. Use `String.format`, `formatted` or concatenation.
- More previews: primitive types in patterns (JEP 455), module import declarations (JEP 476), flexible constructor bodies (second preview, JEP 482).

### Java 24

| Feature | JEP | What it does |
|---|---|---|
| **Stream Gatherers** | 485 | custom intermediate operations for streams (windows, scans, de-duplication by key) |
| **Class-File API** | 484 | a standard API to read and write `.class` files (for frameworks and tools) |
| **Virtual threads without pinning** | 491 | a virtual thread blocked inside `synchronized` now releases its carrier thread; this removes the main practical reason to rewrite `synchronized` to `ReentrantLock` for virtual threads |
| **Remove non-generational ZGC** | 490 | ZGC is always generational |
| **Security Manager permanently disabled** | 486 | the old sandbox is gone (it will be removed) |
| **Quantum-resistant ML-KEM and ML-DSA** | 496, 497 | post-quantum key exchange and signatures in the JDK |
| **Ahead-of-time class loading and linking** | 483 | faster start-up by caching loaded and linked classes |
| Compact object headers | 450 | *experimental* in 24 (see Java 25) |

### Stream Gatherers in practice *(not run: needs JDK 24+)*

A **gatherer** is a custom intermediate operation. The built-ins in `java.util.stream.Gatherers` cover common needs:

```java
List<List<Integer>> windows = Stream.of(1, 2, 3, 4, 5, 6, 7)
        .gather(Gatherers.windowFixed(3))          // [[1,2,3],[4,5,6],[7]]
        .toList();

List<List<Integer>> sliding = Stream.of(1, 2, 3, 4)
        .gather(Gatherers.windowSliding(2))        // [[1,2],[2,3],[3,4]]
        .toList();
```

Before gatherers these needed a loop or a clumsy collector, because streams could not keep state between elements in a clean way.

---

## 5. Java 25 (LTS): The Language Gets Friendlier

Features stated as **final** in JDK 25 by Azul's guide (and in Oracle's pages where noted):

### 5.1 Compact source files and instance `main` (JEP 512)

The classic first program needs a class, `public static`, `String[] args` and `System.out`. Java 25 allows:

```java
void main() {
    IO.println("Hello, World!");
}
```

- No class declaration needed; the file is an implicit class.
- `main` can be an **instance** method without arguments (the launcher creates the object).
- A new `IO` class (in `java.lang`) offers `IO.println`, `IO.print` and `IO.readln` for simple console work, without importing anything.

This is aimed at **learning and scripts**. Real applications still use classes, packages and frameworks (Spring Boot's `main` is unchanged).

### 5.2 Flexible constructor bodies (JEP 513)

Code may run **before** `super(...)` or `this(...)`, as long as it does not use the object under construction. That removes the old workarounds (static helper methods) for validating arguments:

```java
static class Positive extends Base {
    final int v;
    Positive(int raw) {
        if (raw <= 0) throw new IllegalArgumentException("must be positive: " + raw);   // before super()
        super();
        this.v = raw;
    }
}
```

*(compiled on JDK 23 with `--enable-preview`; final in 25)*. The rules: you can validate, compute and assign **fields of this class** before `super()`, but cannot call instance methods or read `this`.

### 5.3 Scoped values (JEP 506)

An immutable value shared with a method and everything it calls, for the duration of that call, **without passing it as a parameter and without `ThreadLocal`**. They are cheaper and safer than `ThreadLocal` when you have **millions of virtual threads**, because the value cannot be changed by callees and is cleaned up automatically when the scope ends. Typical use: a request ID, the current user, a transaction context. *(Check the API for the exact `ScopedValue.where(...).run(...)` form in your JDK.)*

### 5.4 Others

- **Key Derivation Function API (JEP 510)**, final in 25 (derive keys from secrets).
- **Module import declarations (JEP 511)** (`import module java.base;` imports every package a module exports). The sources read for these notes **disagree** on whether it is final or in another preview in 25: check the JDK 25 release notes before using it.
- **Still preview in 25:** primitive types in patterns, `instanceof` and `switch` (JEP 507), stable values (JEP 502), structured concurrency (JEP 505, fifth preview). The Vector API is still an incubator module.

---

## 6. Performance and Runtime Changes

| Change | JEP | Effect |
|---|---|---|
| **Compact object headers** | 519 (product in 25) | the object header shrinks to 64 bits, saving memory when an application has many small objects (the saving depends on the workload, so measure it) |
| **Generational Shenandoah** | 521 (final in 25) | a young generation for the Shenandoah collector |
| **ZGC generational only** | 474, 490 | the default mode in 23; the old mode removed in 24 |
| **AOT cache and class loading** | 483, 514, 515 | faster start-up and warm-up for Spring-style applications |
| **JFR** (Flight Recorder) | 509, 518, 520 | CPU-time profiling (experimental), cooperative sampling, method timing and tracing |

Enabling compact headers (Java 25): `-XX:+UseCompactObjectHeaders`. Always measure with your own workload.

---

## 7. Removals and Things to Stop Using

- **Security Manager**: permanently disabled (Java 24). Code relying on `System.setSecurityManager` will not work.
- **32-bit x86 ports** removed (JEP 503, Java 25).
- **Non-generational ZGC** removed (Java 24).
- **String templates** withdrawn (Java 23); not coming back in the preview form.
- **Graal JIT (experimental)** removed in Java 25.
- Warnings that become errors later: use of `sun.misc.Unsafe` memory methods (JEP 498) and JNI without explicit permission (JEP 472).
- **`finalize()`** is deprecated for removal: use try-with-resources or `Cleaner`.

---

## 8. Spring Boot 4 and Spring Framework 7: The Big Picture

**Spring Boot 4.0 was released on 20 November 2025**, built on **Spring Framework 7**, **Jakarta EE 11** and a **Servlet 6.1** baseline. Boot 4.1.x is current in this repository's projects.

| Baseline | Value (from the migration guide) |
|---|---|
| Java | **17 or later** (Java 25 supported first-class) |
| Spring Framework | 7.x |
| Jakarta EE | 11 (Servlet 6.1) |
| Kotlin | 2.2+ |
| Native image | GraalVM 25+ |
| Upgrade path | move to the latest **3.5.x first**, fix deprecations, then go to 4.0 |

### The three headline changes

1. **Modularisation.** The big auto-configuration jar was split into **small, focused modules**, and starters follow a naming convention. You depend on exactly the technologies you use.
2. **Jackson 3.** Boot's preferred JSON library is now Jackson 3 (new group id `tools.jackson`).
3. **Null safety with JSpecify.** The Spring portfolio uses the standard `org.jspecify.annotations` (`@Nullable`, `@NonNull`) in place of Spring's own null annotations.

Also headline features: **API versioning**, **declarative HTTP service clients**, and **resilience features (retry, concurrency limits) in the core framework**.

### Dependency versions (from the migration guide's page on managed projects)

Spring Framework 7.0, Spring Security 7.0, Spring Batch 6.0, Spring Data 2025.1, Spring GraphQL 2.0, Spring AMQP 4.0, Spring Kafka 4.0, Spring Integration 7.0, Spring Session 4.0.

---

## 9. Upgrading from Boot 3 to Boot 4: What Breaks

### 9.1 Starters were renamed and split

Convention: module `spring-boot-<technology>`, starter `spring-boot-starter-<technology>`, and for tests `spring-boot-starter-<technology>-test`.

| Boot 3 | Boot 4 |
|---|---|
| `spring-boot-starter-web` | **`spring-boot-starter-webmvc`** |
| `spring-boot-starter-web-services` | `spring-boot-starter-webservices` |
| `spring-boot-starter-aop` | `spring-boot-starter-aspectj` |
| `spring-boot-starter-oauth2-client` | `spring-boot-starter-security-oauth2-client` |
| `spring-boot-starter-oauth2-resource-server` | `spring-boot-starter-security-oauth2-resource-server` |
| `spring-boot-starter-oauth2-authorization-server` | `spring-boot-starter-security-oauth2-authorization-server` |
| `spring-boot-starter-test` (one for everything) | **one test starter per technology**, e.g. `spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`, `spring-boot-starter-security-test` |
| Flyway / Liquibase came with the jar | need `spring-boot-starter-flyway` / `spring-boot-starter-liquibase` |

- The old names still work for now as **classic starters** (`spring-boot-starter-classic`, `spring-boot-starter-test-classic`) but are deprecated: they are a **migration aid**, not a destination.
- Each technology test starter brings `spring-boot-starter-test` transitively, so list the technology test starters you need.
- `@WithMockUser` and `@WithUserDetails` need `spring-boot-starter-security-test`.
- New starters include `spring-boot-starter-restclient`, `spring-boot-starter-webclient`, `spring-boot-starter-micrometer-metrics`, `spring-boot-starter-opentelemetry`.

### 9.2 Jackson 3

| Change | Detail |
|---|---|
| Group id | `com.fasterxml.jackson` → **`tools.jackson`** (imports change too) |
| Exception | `jackson-annotations` keeps `com.fasterxml.jackson.core` / `com.fasterxml.jackson.annotation` (`@JsonProperty`, `@JsonIgnore` imports are unchanged) |
| Mapper beans | Boot auto-configures a **`JsonMapper`** (and `XmlMapper`); to replace it, define a `JsonMapper` bean (an `ObjectMapper` bean is no longer enough) |
| Annotations | `@JsonComponent` → `@JacksonComponent`; `@JsonMixin` → `@JacksonMixin` |
| Classes | `Jackson2ObjectMapperBuilderCustomizer` → `JsonMapperBuilderCustomizer` |
| Properties | `spring.jackson.read.*` → `spring.jackson.json.read.*`; `spring.jackson.write.*` → `spring.jackson.json.write.*` |
| Modules | all modules on the classpath are registered; disable with `spring.jackson.find-and-add-modules=false` |
| Migration aid | Jackson 2 dependency management remains, a Jackson 2 `ObjectMapper` can sit beside Boot's Jackson 3; `spring.jackson.use-jackson2-defaults=true` helps keep old behaviour |

Libraries that still use Jackson 2 internally (for example **jjwt's** `jjwt-jackson` module, as used in the Hackathon projects) can run beside Boot's Jackson 3 because Jackson 2 dependency management remains; they simply use their own Jackson 2 mapper rather than Boot's.

### 9.3 Testing changes

Reported in the migration guide (summary text, **check the guide before relying on exact names**):

| Boot 3 | Boot 4 |
|---|---|
| `@MockBean` | **`@MockitoBean`** (Spring Framework's annotation) |
| `@SpyBean` | **`@MockitoSpyBean`** |
| `@SpringBootTest` gave you MockMvc, `WebClient`, `TestRestTemplate` automatically | add **`@AutoConfigureMockMvc`** / `@AutoConfigureTestRestTemplate` / `@AutoConfigureRestTestClient` explicitly |
| `TestRestTemplate` in `org.springframework.boot.test.web.client` | moved to `org.springframework.boot.resttestclient` |

In the Hackathon projects the tests use plain `@SpringBootTest` with `spring-boot-starter-test`; the CI/CD demo uses `spring-boot-starter-webmvc-test`.

### 9.4 Removed

- **Undertow** (incompatible with Servlet 6.1): use Tomcat or Jetty.
- Executable "fully executable" jar launch scripts and the classic uber-jar loader (plain `java -jar` jars still work).
- Spring Session Hazelcast and MongoDB direct support, Spock integration, reactive Pulsar auto-configuration.
- Spring Retry's dependency management: add a version yourself or move to the framework's own retry support.

### 9.5 Other renames and defaults

- `BootstrapRegistry` moved to `org.springframework.boot.bootstrap`.
- DevTools **live reload is off by default**: `spring.devtools.livereload.enabled=true` turns it on.
- Optional Maven dependencies are no longer included in uber jars (`<includeOptional>true</includeOptional>` restores that).
- Reported (check): `spring.dao.exceptiontranslation.enabled` → `spring.persistence.exceptiontranslation.enabled`; many `spring.data.mongodb.*` keys → `spring.mongodb.*`; liveness and readiness probes enabled by default; `HttpMessageConverters` deprecated in favour of customizer interfaces; `hibernate-jpamodelgen` → `hibernate-processor`.

### 9.6 A safe upgrade routine

1. Upgrade to the latest **3.5.x** and remove everything deprecated.
2. Switch the parent to 4.0.x and temporarily use the **classic starters**.
3. Fix compile errors: Jackson imports, `@MockBean` → `@MockitoBean`, renamed properties (add `spring-boot-properties-migrator` to get a report if it is available for your version).
4. Replace classic starters with the technology starters.
5. Run the whole test suite and the app, then read the startup log for deprecation warnings.

---

## 10. New Spring Features Worth Learning

Described at a high level from the Spring blog and release notes. **Check the reference documentation for exact annotation names and configuration** before using them.

| Feature | What it gives you |
|---|---|
| **API versioning** | built-in support in Spring MVC and WebFlux with path, header, query-parameter and media-type strategies, so versions (`/v1/...` or `API-Version: 1.1`) are handled by the framework instead of hand-written routing |
| **HTTP service clients** | declare an interface with HTTP exchange methods and register it (an annotation such as `@ImportHttpServices` groups them); the framework generates the client (a declarative alternative to `RestTemplate` and Feign) |
| **Retry in the core framework** | `RetryTemplate` and a declarative `@Retryable` (with back-off and jitter), no separate Spring Retry dependency |
| **Concurrency limit** | `@ConcurrencyLimit` throttles how many callers may enter a method at once |
| **JSpecify null-safety** | IDEs and tools can warn about null misuse across Spring's API |
| **Smaller, faster start-up** | modular jars and AOT improvements, helped by Java 25 features |
| **`RestClient`** | the modern synchronous HTTP client that replaces `RestTemplate` for new code |

> Practical advice for this training: finish the Boot 3 notes first (the concepts are identical), then learn the Boot 4 differences above. Controllers, services, JPA entities, Spring Security configuration and validation work the same way.

---

## 11. Reading a Boot 4 `pom.xml`

The Hackathon projects use this shape (trimmed):

```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>4.1.1</version>
</parent>
<properties><java.version>17</java.version></properties>

<dependencies>
  <dependency><artifactId>spring-boot-starter-webmvc</artifactId></dependency>        <!-- was starter-web -->
  <dependency><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
  <dependency><artifactId>spring-boot-starter-security</artifactId></dependency>
  <dependency><artifactId>spring-boot-starter-validation</artifactId></dependency>
  <dependency><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
</dependencies>
```

Points to notice: `webmvc` instead of `web`; the Java baseline is still **17** (a project can target 17 even though Boot 4 supports 25); and `spring-boot-starter-test` is still present, which works, but the per-technology test starters (`spring-boot-starter-webmvc-test`, as in the CI/CD demo) are the Boot 4 way.

---

## 12. Common Mistakes

| Mistake | Why | Fix |
|---|---|---|
| Copying a Boot 3 tutorial into a Boot 4 project and importing `com.fasterxml.jackson.databind.ObjectMapper` for the auto-configured mapper | Boot 4 auto-configures a Jackson 3 `JsonMapper` | use `tools.jackson...` types; define a `JsonMapper` bean |
| Using `spring-boot-starter-web` in a new Boot 4 project | deprecated name | `spring-boot-starter-webmvc` |
| `@MockBean` in a Boot 4 test | removed | `@MockitoBean` |
| `@SpringBootTest` with `MockMvc` and no `@AutoConfigureMockMvc` | no longer automatic | add the annotation |
| Building on string templates (`STR."..."`) | withdrawn in Java 23 | `formatted()` |
| Rewriting `synchronized` to locks only for virtual threads | pinning was fixed in Java 24 | measure first; keep `synchronized` if simple |
| Using preview features in production | they can change or vanish | wait until final |
| Jumping from Java 17 to 25 without reading the removals | Security Manager gone, warnings on JNI/Unsafe | run the test suite and check startup warnings |
| Assuming every Java 25 feature is final | several are preview | check the JEP status and use `--enable-preview` knowingly |

---

## 13. Interview Questions

**1. Which Java versions are LTS, and which one is current?**
8, 11, 17, 21 and 25. Java 25 (September 2025) is the current LTS.

**2. What is a preview feature?**
A complete feature released for feedback, disabled unless you pass `--enable-preview`; it can change or be removed in a later release.

**3. What do unnamed variables (`_`) give you?**
A way to declare a variable or pattern you intentionally do not use (`catch (Exception _)`, `case Circle _`), final since Java 22.

**4. What is new about `main` in Java 25?**
Compact source files and instance `main` methods: a file can be just `void main() { ... }` and uses `IO.println`, which lowers the entry barrier for learners.

**5. What do flexible constructor bodies allow?**
Statements before `super(...)`/`this(...)` that do not touch the object under construction, such as validating arguments.

**6. Why were scoped values added when `ThreadLocal` exists?**
They are immutable, bounded to a scope, and cheap with huge numbers of virtual threads, avoiding `ThreadLocal`'s mutability and clean-up problems.

**7. What changed for virtual threads in Java 24?**
Blocking inside `synchronized` no longer pins the carrier thread (JEP 491).

**8. Which Spring Framework and Jakarta EE versions does Boot 4 use?**
Spring Framework 7 and Jakarta EE 11 (Servlet 6.1); Java 17 is the minimum.

**9. What are the main breaking changes from Boot 3 to 4?**
Modular starters and renames (`starter-web` to `starter-webmvc`, per-technology test starters), Jackson 3 (`tools.jackson`), `@MockBean` to `@MockitoBean`, removed Undertow, renamed properties.

**10. What is the "classic starter" and when do you use it?**
A deprecated aggregate starter kept as a transition aid so an upgrade can compile first and be modularised later.

**11. How do you upgrade safely?**
Go to the latest 3.5.x, remove deprecations, switch to 4.0 with classic starters, fix imports and tests, then adopt the new starters.

**12. What does Spring Framework 7 add to the core?**
Declarative retry (`@Retryable`) and concurrency limits, built-in API versioning, HTTP service client registration, and JSpecify null-safety.

---

## 14. Cheat Sheet

| Topic | Remember |
|---|---|
| LTS line | 17, 21, **25** |
| Java 22 | `_` unnamed variables/patterns (final), FFM API (final) |
| Java 23 | Markdown Javadoc, generational ZGC default, string templates withdrawn |
| Java 24 | stream gatherers, class-file API, virtual-thread pinning fix, Security Manager disabled |
| Java 25 | `void main()` + `IO.println`, flexible constructor bodies, scoped values, compact object headers |
| Preview | needs `--enable-preview`; may change |
| Boot 4 | Spring Framework 7, Jakarta EE 11, Java 17+, modular starters, Jackson 3, JSpecify |
| Starters | `starter-webmvc`, per-technology `-test` starters |
| Tests | `@MockitoBean`, `@MockitoSpyBean`, explicit `@AutoConfigureMockMvc` |
| JSON | imports from `tools.jackson`; annotations stay in `com.fasterxml.jackson.annotation` |
| Upgrade | 3.5.x first, classic starters as a bridge |

*End of notes.*
