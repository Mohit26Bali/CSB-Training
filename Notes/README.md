# Notes

Study notes written during the CSB training: Java from the basics to concurrency and the JVM, then SQL, REST, Spring Boot, security and microservices. Each file has a table of contents, worked examples, common mistakes, tricky output puzzles and interview questions.

## Suggested reading order

| # | Notes | What it covers |
| --- | --- | --- |
| 1 | [Java_Static_Constructors_This_Notes](Java_Static_Constructors_This_Notes.md) | Classes and objects, where things live in memory, static vs non-static, constructors, `this` and `this()` |
| 2 | [Java_OOP_and_Inheritance_Notes](Java_OOP_and_Inheritance_Notes.md) | The four pillars, inheritance, `super`, overriding, polymorphism, abstract classes, interfaces, composition vs inheritance |
| 3 | [Java_Strings_Notes](Java_Strings_Notes.md) | Immutability, the string pool, `==` vs `equals`, `StringBuilder`, formatting, text blocks, regex basics |
| 4 | [Java_Exception_Handling_Notes](Java_Exception_Handling_Notes.md) | Hierarchy, checked vs unchecked, `try`/`catch`/`finally`, try-with-resources, custom exceptions, best practices |
| 5 | [Java_Collections_Framework_Notes](Java_Collections_Framework_Notes.md) | List, Set, Queue, Deque, iterators, `Comparable` and `Comparator`, immutable collections, complexity |
| 6 | [Java_Map_Interface_Notes](Java_Map_Interface_Notes.md) | `Map`, how `HashMap` works inside, `compute` / `merge`, the implementations and when to pick each |
| 7 | [Java_Lambda_Expressions_Notes](Java_Lambda_Expressions_Notes.md) | Lambdas, simplification rules, the predefined functional interfaces |
| 8 | [Java_Streams_API_Notes](Java_Streams_API_Notes.md) | Stream pipelines, `Collectors`, primitive streams, `Optional`, parallel streams, interview favourites |
| 9 | [Java_Modern_Language_Features_Notes](Java_Modern_Language_Features_Notes.md) | Records, sealed types, pattern matching, `var`, newer `String` methods (JDK 21 recommended) |
| 10 | [Java_Multithreading_and_Concurrency_Notes](Java_Multithreading_and_Concurrency_Notes.md) | Threads, thread safety, `synchronized` and `volatile`, `java.util.concurrent`, `CompletableFuture`, virtual threads |
| 11 | [Java_JVM_Internals_Notes](Java_JVM_Internals_Notes.md) | JDK vs JRE vs JVM, class loading, memory areas, the JIT compiler, garbage collectors, tuning and tools |
| 12 | [SQL_Transactions_Locks_and_Cascade_Notes](SQL_Transactions_Locks_and_Cascade_Notes.md) | Core SQL, foreign keys and `CASCADE`, normalization, indexes, then transactions and ACID, concurrency anomalies, isolation levels, locking vs MVCC, deadlocks, optimistic vs pessimistic locking |
| 13 | [JDBC_JPA_Spring_Data_Notes](JDBC_JPA_Spring_Data_Notes.md) | JDBC, ORM, JPA and Hibernate, entity mapping, Spring Data JPA |
| 14 | [REST_API_Notes](REST_API_Notes.md) | REST principles, URL design, HTTP methods and status codes, building APIs in Spring Boot (DTOs, validation, errors, pagination), CORS, testing, versioning |
| 15 | [Spring_Boot_and_MVC_Fundamentals_Notes](Spring_Boot_and_MVC_Fundamentals_Notes.md) | The Spring container, beans, dependency injection, Spring MVC (Spring Boot 3, `jakarta.*`) |
| 16 | [Spring_Security_and_JWT_Notes](Spring_Security_and_JWT_Notes.md) | Authentication and authorization, `SecurityFilterChain`, BCrypt, JWT (Spring Security 6) |
| 17 | [Microservices_Notes](Microservices_Notes.md) | Concepts and patterns with the Spring Cloud tools: Eureka, Feign, API Gateway, Resilience4j, Config Server, messaging, observability, deployment |

## A note on versions

The Java examples target **Java 17 and 21** and the Spring notes target **Spring Boot 3 / Spring Security 6** (`jakarta.*` packages, no `WebSecurityConfigurerAdapter`). Older tutorials use the removed APIs, and the notes point out where they differ. The order of output from concurrent programs can vary between runs; the notes say where.

## Checking the notes

```bash
node check-notes.js
```

Needs only Node 18+. It checks that every table-of-contents link points to a real heading, that links to other files work, that every code block is closed, that no unfinished-work markers are left, and that nothing looks like a real key or token. It exits with an error if it finds a problem, so it can also be run in CI.
