# 9. Spring Profiles

**Concept:** Spring Boot. One jar behaves differently per environment.

**Properties per profile.** `application-dev.properties` and `application-prod.properties` hold different messages and settings. `GET /api/profile` exposes the **active** configuration.

**Beans per profile (optional extension).** `@Profile` registers a different `NotificationSender` bean per profile:

| Profile | Bean |
|---|---|
| `dev` | console |
| `prod` | e-mail |
| `test` | mock |
| none | default |

**A dev-only endpoint.** A debug endpoint is marked `@Profile("!prod")`, so it is never available in production.

## Tech

Java 17 · Maven · **Spring Boot 3.5.6** (starter-web)

**Database:** none. This assignment is about configuration only, so no `aNN_` table is created in `Day_007_Assigments`.

## Structure

```text
09-spring-profiles/
├── pom.xml
└── src/main/
    ├── java/com/company/boot/
    │   ├── ProfilesApplication.java              @SpringBootApplication (run this)
    │   ├── config/AppSettings.java               @ConfigurationProperties("app")
    │   ├── notification/
    │   │   ├── NotificationSender.java           interface
    │   │   ├── DefaultNotificationSender.java    @Profile("default")  - no profile active
    │   │   ├── ConsoleNotificationSender.java    @Profile("dev")
    │   │   ├── EmailNotificationSender.java      @Profile("prod")
    │   │   └── MockNotificationSender.java       @Profile("test")     - optional extension
    │   └── controller/
    │       ├── ProfileController.java            GET /api/profile, /api/profile/notify
    │       └── DebugController.java              GET /api/debug  - @Profile("!prod")
    └── resources/
        ├── application.properties                common + fallback values (always loaded)
        ├── application-dev.properties            dev overrides (DEBUG logging)
        ├── application-prod.properties           prod overrides (port 9090, WARN logging)
        └── application-test.properties           test overrides (optional extension)
```

## How to run

1. **Eclipse:** **File → Import → Maven → Existing Maven Projects**, select `09-spring-profiles`.
2. Right-click `ProfilesApplication` → **Run As → Java Application**. This runs with **no profile** on port 8080.
3. **To choose a profile in Eclipse:** go to **Run → Run Configurations… → ProfilesApplication**, then use **one** of these:
   - **Arguments tab → Program arguments:** `--spring.profiles.active=dev`
   - **Arguments tab → VM arguments:** `-Dspring.profiles.active=prod`
   - **Environment tab:** add `SPRING_PROFILES_ACTIVE` = `test`
4. Open http://localhost:8080/api/profile, `/api/profile/notify` and `/api/debug`. **prod runs on port 9090.**

From a terminal, after `mvn package`:

```text
java -jar target/spring-profiles-0.0.1-SNAPSHOT.jar                                  (no profile)
java -jar target/spring-profiles-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev     (program argument)
java -Dspring.profiles.active=prod -jar target/spring-profiles-0.0.1-SNAPSHOT.jar    (JVM system property)
set SPRING_PROFILES_ACTIVE=test  then  java -jar target/spring-profiles-0.0.1-SNAPSHOT.jar   (environment variable)
```

A fourth way exists but is **not used**: `spring.profiles.active=dev` inside `application.properties`. It is left commented out there. Fixing the profile inside the jar defeats the purpose of choosing it per environment.

## Observed output: each profile, and how it was activated

### No profile: `java -jar target/spring-profiles-0.0.1-SNAPSHOT.jar`

```text
No active profile set, falling back to 1 default profile: "default"
Tomcat started on port 8080 (http) with context path '/'

GET /api/profile -> 200
{"activeProfiles":[],"defaultProfiles":["default"],"message":"Hello! No profile is active - using application.properties defaults.","environment":"none","showSqlLogs":false,"cacheTtlSeconds":60,"serverPort":"8080","notificationSender":"DefaultNotificationSender"}
GET /api/profile/notify -> 200
{"result":"[DEFAULT] logged only, nothing sent to customer@example.com: Hello! No profile is active - using application.properties defaults."}
GET /api/debug -> 200
{"warning":"debug endpoint - not available in prod","javaVersion":"17.0.14","appEnvironment":"none"}
```

### dev, activated by a program argument: `--spring.profiles.active=dev`

```text
DEBUG ... com.company.boot.ProfilesApplication : Running with Spring Boot v3.5.6, Spring v6.2.11   <- DEBUG logging from the dev file
The following 1 profile is active: "dev"

GET /api/profile -> 200
{"activeProfiles":["dev"],"defaultProfiles":["default"],"message":"Hello from DEVELOPMENT - debug features are ON.","environment":"development","showSqlLogs":true,"cacheTtlSeconds":5,"serverPort":"8080","notificationSender":"ConsoleNotificationSender"}
GET /api/profile/notify -> 200
{"result":"[DEV-CONSOLE] to customer@example.com: Hello from DEVELOPMENT - debug features are ON."}
GET /api/debug -> 200
{"warning":"debug endpoint - not available in prod","javaVersion":"17.0.14","appEnvironment":"development"}

console:  [DEV-CONSOLE] to customer@example.com: Hello from DEVELOPMENT - debug features are ON.
```

### prod, activated by a JVM system property: `java -Dspring.profiles.active=prod -jar ...`

```text
GET /api/profile -> 200            (on port 9090)
{"activeProfiles":["prod"],"defaultProfiles":["default"],"message":"Welcome to the PRODUCTION system.","environment":"production","showSqlLogs":false,"cacheTtlSeconds":3600,"serverPort":"9090","notificationSender":"EmailNotificationSender"}
GET /api/profile/notify -> 200
{"result":"[PROD-EMAIL] e-mail queued for customer@example.com: Welcome to the PRODUCTION system."}
GET /api/debug -> 404              <- DebugController is @Profile("!prod"): the bean does not exist
{"timestamp":"2026-10-11T06:06:54.612+00:00","status":404,"error":"Not Found","path":"/api/debug"}
```

The prod console showed **only the Spring banner**. `logging.level.root=WARN` in `application-prod.properties` hides every INFO line, including "profile is active" and "Tomcat started".

### test, activated by an environment variable (optional extension): `SPRING_PROFILES_ACTIVE=test`

```text
The following 1 profile is active: "test"

GET /api/profile -> 200
{"activeProfiles":["test"],"defaultProfiles":["default"],"message":"Hello from TEST - notifications are mocked.","environment":"test","showSqlLogs":false,"cacheTtlSeconds":0,"serverPort":"8080","notificationSender":"MockNotificationSender"}
GET /api/profile/notify -> 200
{"result":"[TEST-MOCK] recorded (total recorded: 1) for customer@example.com: Hello from TEST - notifications are mocked."}
GET /api/profile/notify?to=qa@company.com -> 200
{"result":"[TEST-MOCK] recorded (total recorded: 2) for qa@company.com: Hello from TEST - notifications are mocked."}
```

### Summary

| Profile | Activated by | message | port | NotificationSender | /api/debug |
|---|---|---|---|---|---|
| none | nothing | "Hello! No profile is active ..." | 8080 | DefaultNotificationSender | 200 |
| dev | `--spring.profiles.active=dev` | "Hello from DEVELOPMENT ..." | 8080 | ConsoleNotificationSender | 200 |
| prod | `-Dspring.profiles.active=prod` | "Welcome to the PRODUCTION system." | **9090** | EmailNotificationSender | **404** |
| test | `SPRING_PROFILES_ACTIVE=test` | "Hello from TEST ..." | 8080 | MockNotificationSender | 200 |

`showSqlLogs` (true only in dev) and `cacheTtlSeconds` (5 / 3600 / 0 / 60) differ in the same way. **Not a single line of Java changes** between these runs.

## Failure / edge-case evidence

**1. Unknown profile `staging`: no file, and no matching `@Profile` bean.** `--spring.profiles.active=staging`:

```text
The following 1 profile is active: "staging"
***************************
APPLICATION FAILED TO START
***************************

Description:

Parameter 2 of constructor in com.company.boot.controller.ProfileController required a bean of type 'com.company.boot.notification.NotificationSender' that could not be found.

Action:

Consider defining a bean of type 'com.company.boot.notification.NotificationSender' in your configuration.
```

A missing `application-staging.properties` alone is harmless; Boot just uses the defaults. The crash happens because **no bean is marked `@Profile("staging")`**. Also, once *any* profile is active, the `"default"` profile is switched off, so `DefaultNotificationSender` disappears too.

**2. Two profiles that both supply the same bean.** `--spring.profiles.active=dev,test`:

```text
The following 2 profiles are active: "dev", "test"
***************************
APPLICATION FAILED TO START
***************************

Description:

Parameter 2 of constructor in com.company.boot.controller.ProfileController required a single bean, but 2 were found:
	- consoleNotificationSender: defined in URL [...ConsoleNotificationSender.class]
	- mockNotificationSender: defined in URL [...MockNotificationSender.class]

Action:

Consider marking one of the beans as @Primary, updating the consumer to accept multiple beans, or using @Qualifier to identify the bean that should be consumed
```

Several profiles *can* be active together; for **properties**, the last one listed wins. But mutually exclusive `@Profile` beans must not be activated together. This is the same ambiguity as Assignment 5.

**3. Profile names are case-sensitive.** `--spring.profiles.active=DEV`:

```text
The following 1 profile is active: "DEV"
...
required a bean of type 'com.company.boot.notification.NotificationSender' that could not be found.
```

`"DEV"` is not `"dev"`. Neither `@Profile("dev")` nor `application-dev.properties` was used: the DEBUG line from the dev file is also missing. The result is the same failure as with `staging`.

**4. A bean can be *excluded* from a profile.** `DebugController` is `@Profile("!prod")`. It answers 200 for none/dev/test and **404 in prod** (shown above).

## Explanation

**Why profiles are used.** An application runs in several environments: a developer's PC, a test server, production. Each needs different settings:
- database URL, port, log level, cache times
- external services, such as real e-mail vs a console or mock

Profiles keep all of these **in one codebase and one jar**. The environment is chosen **at start-up**, without editing or rebuilding code. Profiles work at two levels:
- **Properties:** `application-{profile}.properties` is loaded **on top of** `application.properties` and overrides only the keys it contains.
- **Beans:** `@Profile("dev")` on a `@Component` / `@Configuration` / `@Bean` registers it only when that profile is active. `@Profile("!prod")` means "every profile except prod".

**Ways to activate a profile**, highest priority first:
1. a program argument: `--spring.profiles.active=dev`
2. a JVM property: `-Dspring.profiles.active=prod`
3. an environment variable: `SPRING_PROFILES_ACTIVE=test` (typical on servers and in Docker)
4. `spring.profiles.active` inside `application.properties`

**What happens when no profile is active:**
- Spring logs `No active profile set, falling back to 1 default profile: "default"`.
- Only `application.properties` is loaded.
- Beans **without** `@Profile` are created as always.
- Beans marked `@Profile("default")` are created as well, and only in this case. Here that is `DefaultNotificationSender`.

As soon as any profile is activated, the `"default"` profile is switched off.

**A safe pattern.** `application.properties` holds safe defaults. Profile files hold only differences. Production-only behaviour, and *anything that must never run in production* (`@Profile("!prod")`), is controlled by the profile.
