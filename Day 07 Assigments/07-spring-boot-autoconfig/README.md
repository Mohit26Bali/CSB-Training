# 7. Spring Boot Auto-Configuration

**Concept:** Spring Boot. A minimal web application built from `spring-boot-starter-web` and one `@RestController`.

It runs on an **embedded Tomcat that we never configure**. With `spring-boot-starter-jdbc` added, Boot also builds the database connection pool and `JdbcTemplate` from three lines in `application.properties`, with no `@Bean` code. Actuator (the optional extension) is used to inspect what Boot created.

## Tech

Java 17 · Maven · **Spring Boot 3.5.6** (Spring Framework 6.2.11, Tomcat 10.1.46, HikariCP) · MySQL 8

**Database:** shared Day 07 database `Day_007_Assigments`. Table **`a07_products`** uses the `a07_` prefix, so it is uniquely identifiable as Assignment 7.

## Structure

```text
07-spring-boot-autoconfig/
├── pom.xml                       spring-boot-starter-parent + 3 starters (web, jdbc, actuator)
└── src/main/
    ├── java/com/company/boot/
    │   ├── BootAutoConfigApplication.java   @SpringBootApplication + main()  (the whole configuration)
    │   └── product/
    │       ├── ProductController.java       the one @RestController
    │       ├── ProductRepository.java       uses the auto-configured JdbcTemplate
    │       └── Product.java                 record (serialised to JSON automatically)
    └── resources/
        ├── application.properties           port, datasource URL/user, actuator settings
        ├── db-secret.properties             datasource password (git-ignored; copy db-secret.properties.example)
        └── schema.sql                       a07_products (run manually in MySQL)
```

No server class, no `DataSource`, no `JdbcTemplate`, and no JSON converter is written anywhere.

## How to run

1. Run `src/main/resources/schema.sql` in MySQL. It drops/recreates only `a07_products`.
2. **Eclipse:** **File → Import → Maven → Existing Maven Projects**, select `07-spring-boot-autoconfig`, and let it download the dependencies. Then right-click `BootAutoConfigApplication` → **Run As → Java Application** (or **Spring Boot App** if Spring Tools is installed).
3. **Or from a terminal:** `mvn spring-boot:run`, or `mvn package` followed by `java -jar target/spring-boot-autoconfig-0.0.1-SNAPSHOT.jar`.
4. Open in a browser (or with `curl`):
   - http://localhost:8080/api/hello
   - http://localhost:8080/api/products
   - http://localhost:8080/api/products/2
   - http://localhost:8080/actuator/health
5. Stop with the red **Terminate** button in Eclipse (or Ctrl+C).

## Observed output

### Startup log (built with `mvn package`, run with `java -jar`)

```text
 :: Spring Boot ::                (v3.5.6)

Starting BootAutoConfigApplication v0.0.1-SNAPSHOT using Java 17.0.14 with PID 4836
No active profile set, falling back to 1 default profile: "default"
Tomcat initialized with port 8080 (http)
Starting service [Tomcat]
Starting Servlet engine: [Apache Tomcat/10.1.46]
Initializing Spring embedded WebApplicationContext
Root WebApplicationContext: initialization completed in 3514 ms
HikariPool-1 - Starting...
HikariPool-1 - Added connection com.mysql.cj.jdbc.ConnectionImpl@2f9b21d6
HikariPool-1 - Start completed.
Exposing 7 endpoints beneath base path '/actuator'
Tomcat started on port 8080 (http) with context path '/'
Started BootAutoConfigApplication in 7.858 seconds (process running for 8.788)
```

Every line from Tomcat, HikariCP and Actuator comes from auto-configuration. Our code only calls `SpringApplication.run(...)`.

### REST endpoints

```text
GET /api/hello          -> 200
{"server":"embedded Tomcat, started by auto-configuration","message":"Hello from Spring Boot!"}

GET /api/products       -> 200
[{"productId":1,"productName":"Wireless Mouse","category":"Electronics","price":799.00},
 {"productId":2,"productName":"Mechanical Keyboard","category":"Electronics","price":3499.00},
 {"productId":3,"productName":"Laptop Stand","category":"Accessories","price":1599.00},
 {"productId":4,"productName":"USB-C Hub","category":"Accessories","price":1299.00}]

GET /api/products/2     -> 200
{"productId":2,"productName":"Mechanical Keyboard","category":"Electronics","price":3499.00}

GET /api/products/99    -> 404   (ResponseEntity.notFound())

GET /api/does-not-exist -> 404   (Boot's default JSON error page - also auto-configured)
{"timestamp":"2026-10-11T05:55:24.562+00:00","status":404,"error":"Not Found","path":"/api/does-not-exist"}
```

The Java objects (`Map`, `List<Product>`, `Product` record) were converted to JSON by the **auto-configured Jackson `ObjectMapper`**.

## Failure / edge-case evidence

**1. Port already in use.** A second copy was started while the first was still running:

```text
***************************
APPLICATION FAILED TO START
***************************

Description:

Web server failed to start. Port 8080 was already in use.

Action:

Identify and stop the process that's listening on port 8080 or configure this application to listen on another port.
```

Fix: stop the other process (often an earlier run still active in Eclipse), or set `server.port=8081`.

**2. Starter present but required settings missing.** Run with `--spring.datasource.url=` (empty):

```text
***************************
APPLICATION FAILED TO START
***************************

Description:

Failed to configure a DataSource: 'url' attribute is not specified and no embedded datasource could be configured.

Reason: Failed to determine a suitable driver class

Action:

Consider the following:
	If you want an embedded database (H2, HSQL or Derby), please put it on the classpath.
	If you have database settings to be loaded from a particular profile you may need to activate it (no profiles are currently active).
```

Because `spring-boot-starter-jdbc` is on the classpath, Boot *tries* to build a `DataSource`. With no URL and no embedded database it cannot, and it says exactly what to add. Auto-configuration is driven by what is on the classpath, so adding a starter is a request for that feature.

## Optional extension: Actuator

`spring-boot-starter-actuator` adds production-ready endpoints. They are exposed in `application.properties`; only `health` is exposed by default.

**`/actuator/health`**, which includes an automatic MySQL check:

```json
{"status":"UP","components":{"db":{"status":"UP","details":{"database":"MySQL","validationQuery":"isValid()"}},
 "diskSpace":{"status":"UP", ...},"ping":{"status":"UP"},"ssl":{"status":"UP", ...}}}
```

**`/actuator/info`:**

```json
{"app":{"name":"Spring Boot Auto-Configuration Demo","assignment":"Day 07 - Assignment 07"}}
```

**`/actuator/beans`: 324 beans in total**, although we wrote only 3 classes. Some of the auto-created beans:

```text
dataSource                       com.zaxxer.hikari.HikariDataSource
jdbcTemplate                     org.springframework.jdbc.core.JdbcTemplate
tomcatServletWebServerFactory    org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory
dispatcherServlet                org.springframework.web.servlet.DispatcherServlet
jacksonObjectMapper              com.fasterxml.jackson.databind.ObjectMapper
productController                com.company.boot.product.ProductController            <- ours
productRepository                com.company.boot.product.ProductRepository$$SpringCGLIB$$0  <- ours
```

**`/actuator/conditions`**, the auto-configuration report: 194 positive matches, 298 negative matches.

```text
MATCHED     DataSourceAutoConfiguration      : @ConditionalOnClass found required classes 'javax.sql.DataSource', ...
MATCHED     JdbcTemplateAutoConfiguration    : @ConditionalOnClass found required classes 'javax.sql.DataSource', 'org.springframework.jdbc.core.JdbcTemplate'
MATCHED     ServletWebServerFactoryAutoConfiguration : @ConditionalOnClass found required class 'jakarta.servlet.ServletRequest'
MATCHED     DispatcherServletAutoConfiguration : @ConditionalOnClass found required class 'org.springframework.web.servlet.DispatcherServlet'
MATCHED     JacksonAutoConfiguration         : @ConditionalOnClass found required class 'com.fasterxml.jackson.databind.ObjectMapper'
NOT MATCHED HibernateJpaAutoConfiguration    : @ConditionalOnClass did not find required class 'jakarta.persistence.EntityManager'
NOT MATCHED MongoAutoConfiguration           : @ConditionalOnClass did not find required class 'com.mongodb.client.MongoClient'
NOT MATCHED RedisAutoConfiguration           : @ConditionalOnClass did not find required class 'org.springframework.data.redis.core.RedisOperations'
NOT MATCHED SecurityAutoConfiguration        : @ConditionalOnClass did not find required class '...DefaultAuthenticationEventPublisher'
NOT MATCHED FlywayAutoConfiguration          : @ConditionalOnClass did not find required class 'org.flywaydb.core.Flyway'
```

**`/actuator/mappings`**, the controller's routes:

```text
{GET [/api/products]}   {GET [/api/products/{id}]}   {GET [/api/hello]}
```

Other exposed endpoints: `/actuator/metrics` (JVM, HTTP and pool metrics) and `/actuator/env` (configuration values, with secrets masked).

## Explanation

**Spring Boot.** A layer on top of the Spring Framework that removes setup work:
- It picks compatible library versions for you.
- It configures common infrastructure automatically.
- It packages the app as one runnable jar that **contains its own web server**.

You write business code; Boot provides sensible defaults that can be overridden.

**Starters.** A starter is one dependency that pulls in a tested set of libraries for one capability:
- `spring-boot-starter-web`: Spring MVC + embedded Tomcat + Jackson
- `spring-boot-starter-jdbc`: spring-jdbc + HikariCP
- `spring-boot-starter-actuator`: monitoring endpoints

The `spring-boot-starter-parent` manages all versions, so the `pom.xml` has **no version numbers** on dependencies.

**Auto-configuration.** At startup Boot evaluates hundreds of auto-configuration classes, each guarded by conditions:
- `@ConditionalOnClass`: is the library on the classpath?
- `@ConditionalOnMissingBean`: did the developer already define one?
- `@ConditionalOnProperty`: is a setting present?

If the conditions hold, the beans are created with defaults; otherwise they are skipped. The conditions report above shows both outcomes. **The developer always wins:** declare your own `DataSource` `@Bean`, or set a property such as `server.port`, and Boot backs off or adjusts.

**`@SpringBootApplication`** is three annotations in one:

| Part | Effect |
|---|---|
| `@SpringBootConfiguration` | the class is a `@Configuration` (it can declare `@Bean`s) |
| `@EnableAutoConfiguration` | switches on auto-configuration (everything described above) |
| `@ComponentScan` | scans **this class's package and sub-packages** (`com.company.boot.**`); that's why the main class sits in the root package |

`SpringApplication.run(...)`:
1. creates the `ApplicationContext`
2. applies auto-configuration
3. starts the embedded Tomcat
4. keeps the application running until it is stopped
