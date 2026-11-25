# Notes

Study notes written during the CSB training: Java from the basics to concurrency and the JVM, then SQL, REST, Spring Boot, security and microservices. Each file has a table of contents, worked examples, common mistakes, tricky output puzzles and interview questions.

**How they were checked:** every Java code block was compiled on JDK 21 (fragments that are only illustrations were left out of that check), the output-prediction puzzles were run and their answers compared with the real output, the Spring notes are backed by a runnable Spring Boot 4 project whose tests pass, and the SQL notes were run on MySQL 8.4 and PostgreSQL 17. `node check-notes.js` checks the links and code fences.

## Suggested reading order

| # | Notes | What it covers |
| --- | --- | --- |
| 1 | [Java_Fundamentals_Notes](Java_Fundamentals_Notes.md) | Program layout, primitive types, casting, every operator, overflow and floating point, `BigDecimal`, control flow, arrays, methods and varargs, wrappers, console input, `Math` |
| 2 | [Java_Static_Constructors_This_Notes](Java_Static_Constructors_This_Notes.md) | Classes and objects, where things live in memory, static vs non-static, constructors, `this` and `this()` |
| 3 | [Java_OOP_and_Inheritance_Notes](Java_OOP_and_Inheritance_Notes.md) | The four pillars, inheritance, `super`, overriding, polymorphism, abstract classes, interfaces, composition vs inheritance |
| 4 | [Java_Generics_Enums_and_Nested_Classes_Notes](Java_Generics_Enums_and_Nested_Classes_Notes.md) | Generic classes and methods, bounds, wildcards and PECS, type erasure, enums with data and behaviour, `EnumMap`, static nested, inner, local and anonymous classes |
| 5 | [Java_Strings_Notes](Java_Strings_Notes.md) | Immutability, the string pool, `==` vs `equals`, `StringBuilder`, formatting, text blocks, regex basics |
| 6 | [Java_Exception_Handling_Notes](Java_Exception_Handling_Notes.md) | Hierarchy, checked vs unchecked, `try`/`catch`/`finally`, try-with-resources, custom exceptions, best practices |
| 7 | [Java_Collections_Framework_Notes](Java_Collections_Framework_Notes.md) | List, Set, Queue, Deque, iterators, `Comparable` and `Comparator`, immutable collections, complexity |
| 8 | [Java_Map_Interface_Notes](Java_Map_Interface_Notes.md) | `Map`, how `HashMap` works inside, `compute` / `merge`, the implementations and when to pick each |
| 9 | [Java_Lambda_Expressions_Notes](Java_Lambda_Expressions_Notes.md) | Lambdas, simplification rules, the predefined functional interfaces |
| 10 | [Java_Streams_API_Notes](Java_Streams_API_Notes.md) | Stream pipelines, `Collectors`, primitive streams, `Optional`, parallel streams, interview favourites |
| 11 | [Java_Modern_Language_Features_Notes](Java_Modern_Language_Features_Notes.md) | Records, sealed types, pattern matching, `var`, newer `String` methods (JDK 21 recommended) |
| 12 | [Java_IO_NIO_Serialization_and_DateTime_Notes](Java_IO_NIO_Serialization_and_DateTime_Notes.md) | Streams, readers and writers, `Path` and `Files`, encodings, serialization and why it is dangerous, `java.time`, time zones, `Clock` |
| 13 | [Java_Reflection_and_Annotations_Notes](Java_Reflection_and_Annotations_Notes.md) | The `Class` object, inspecting and calling by name, `setAccessible` and modules, custom annotations, a tiny validator and a tiny DI container |
| 14 | [Java_Multithreading_and_Concurrency_Notes](Java_Multithreading_and_Concurrency_Notes.md) | Threads, thread safety, `synchronized` and `volatile`, `java.util.concurrent`, `CompletableFuture`, virtual threads |
| 15 | [Java_JVM_Internals_Notes](Java_JVM_Internals_Notes.md) | JDK vs JRE vs JVM, class loading, memory areas, the JIT compiler, garbage collectors, tuning and tools |
| 16 | [Java_Design_Patterns_Notes](Java_Design_Patterns_Notes.md) | SOLID, singleton, factory, builder, strategy, observer, decorator, adapter, facade, proxy, template method, command, chain, state, dependency injection, anti-patterns |
| 17 | [SQL_Transactions_Locks_and_Cascade_Notes](SQL_Transactions_Locks_and_Cascade_Notes.md) | Core SQL, foreign keys and `CASCADE`, normalization, indexes, then transactions and ACID, concurrency anomalies, isolation levels, locking vs MVCC, deadlocks |
| 18 | [SQL_Advanced_Joins_Windows_Views_and_Procedures_Notes](SQL_Advanced_Joins_Windows_Views_and_Procedures_Notes.md) | Joins and the `NULL` traps, `ROLLUP`, recursive CTEs, window functions, views, stored procedures, triggers, `EXPLAIN`, MySQL vs PostgreSQL, NoSQL |
| 19 | [JDBC_JPA_Spring_Data_Notes](JDBC_JPA_Spring_Data_Notes.md) | JDBC, ORM, JPA and Hibernate, entity mapping, Spring Data JPA |
| 20 | [REST_API_Notes](REST_API_Notes.md) | REST principles, URL design, HTTP methods and status codes, building APIs in Spring Boot (DTOs, validation, errors, pagination), CORS, testing, versioning |
| 21 | [Spring_Boot_and_MVC_Fundamentals_Notes](Spring_Boot_and_MVC_Fundamentals_Notes.md) | The Spring container, beans, dependency injection, Spring MVC (Spring Boot 3, `jakarta.*`) |
| 22 | [Spring_Boot_Configuration_AOP_Caching_and_Testing_Notes](Spring_Boot_Configuration_AOP_Caching_and_Testing_Notes.md) | Configuration and profiles, logging, proxies, AOP, caching, `@Async` and `@Scheduled`, safe file upload, Actuator, JUnit, Mockito and `MockMvc` |
| 23 | [Spring_Security_and_JWT_Notes](Spring_Security_and_JWT_Notes.md) | Authentication and authorization, `SecurityFilterChain`, BCrypt, JWT (Spring Security 6) |
| 24 | [Microservices_Notes](Microservices_Notes.md) | Concepts and patterns with the Spring Cloud tools: Eureka, Feign, API Gateway, Resilience4j, Config Server, messaging, observability, deployment |
| 25 | [Whats_New_Java_22_to_25_and_Spring_Boot_4_Notes](Whats_New_Java_22_to_25_and_Spring_Boot_4_Notes.md) | Java 22 to 25 (unnamed variables, stream gatherers, scoped values, `void main()`), Spring Framework 7, Spring Boot 4 and how to upgrade from Boot 3 |
| 26 | [JUnit_and_Mockito_Testing_Notes](JUnit_and_Mockito_Testing_Notes.md) | JUnit 5/6, assertions with AssertJ, lifecycle, parameterized tests, Mockito (stubbing, verification, strict stubs, static mocks), testing time and exceptions, coverage with JaCoCo |
| 27 | [JPA_and_Hibernate_Deep_Dive_Notes](JPA_and_Hibernate_Deep_Dive_Notes.md) | The persistence context and dirty checking, relationships and the owning side, lazy loading, the N+1 problem with real SQL and four fixes, projections, paging, locking, transactions, batching, schema management |

## Runnable examples

| Folder | Backs | Run |
| --- | --- | --- |
| [`examples/spring-boot-features`](examples/spring-boot-features) | note 22 (and the Boot 4 parts of note 25) | `mvn test` (22 tests) |
| [`examples/sql-advanced`](examples/sql-advanced) | note 18 | the scripts run on MySQL and PostgreSQL |
| [`examples/junit-mockito`](examples/junit-mockito) | note 26 | `mvn test` |
| [`examples/jpa-hibernate`](examples/jpa-hibernate) | note 27 | `mvn test` (31 tests) |

## A note on versions

The Java examples target **Java 17 and 21**; note 25 covers 22 to 25. The Spring notes 21, 23 and 24 target **Spring Boot 3 / Spring Security 6** (`jakarta.*` packages, no `WebSecurityConfigurerAdapter`); note 22 is written for Boot 4 and says where Boot 3 differs. Older tutorials use removed APIs, and the notes point out where they differ. The order of output from concurrent programs can vary between runs; the notes say where.

## Checking the notes

```bash
node check-notes.js
```

Needs only Node 18+. It checks that every table-of-contents link points to a real heading, that links to other files work, that every code block is closed, that no unfinished-work markers are left, and that nothing looks like a real key or token. It exits with an error if it finds a problem, so it can also be run in CI.
