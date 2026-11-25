# Spring Boot features example

A small Spring Boot **4.1.1** application that backs the snippets in
[`Spring_Boot_Configuration_AOP_Caching_and_Testing_Notes.md`](../../Spring_Boot_Configuration_AOP_Caching_and_Testing_Notes.md).
It exists to **prove the notes are right**: every behaviour they describe is checked by a test.

| Area | Code | Test |
| --- | --- | --- |
| Typed, validated configuration | `config/MailProperties` | `ProfilesTest`, `FeaturesTest` |
| Profiles and override order | `application.yml` (a `prod` document) | `ProfilesTest` |
| AOP: `@Around`, `@Before`, `@AfterReturning`, `@AfterThrowing`, `@Order` | `aop/TimingAspect`, `aop/AuditAspect` | `FeaturesTest` |
| The self-invocation trap | `service/OrderService`, `cache/PriceService` | `FeaturesTest` |
| Caching: `@Cacheable`, `@CachePut`, `@CacheEvict` | `cache/PriceService` | `FeaturesTest` |
| `@Async`, `@Scheduled` | `service/ReportService` | `FeaturesTest` |
| Safe file upload and download | `web/UploadController` | `FeaturesTest` |
| Actuator (`health` exposed, `env` not) | `application.yml` | `FeaturesTest` |
| Slice test with `@WebMvcTest` and `@MockitoBean` | `web/HelloController` | `SliceAndMockTest` |

## Run the tests

Needs JDK 17 or newer and Maven (internet access the first time, to download dependencies):

```bash
mvn test
```

All 22 tests should pass. One of them starts the application with deliberately invalid settings, so an `APPLICATION FAILED TO START` message in the log is expected.

## Run the application

```bash
mvn spring-boot:run
# then try:
#   http://localhost:8080/mail-host
#   http://localhost:8080/order?item=book
#   http://localhost:8080/actuator/health
```

To see the `prod` profile: `mvn spring-boot:run -Dspring-boot.run.profiles=prod`.

## Boot 3 or Boot 4?

The code targets Boot 4. To try it on Boot 3.5 change the parent version, use `spring-boot-starter-web` and `spring-boot-starter-aop`, `spring-boot-starter-test` instead of the `*-test` starters, and `@MockBean` and the Boot 3 test imports (`org.springframework.boot.test.autoconfigure.web.servlet.*`).
