# 8. External Configuration (properties)

**Concept:** Spring Boot. `app.name`, `app.version` and `app.owner` are defined in `application.properties`, read in **two ways**, and exposed through REST:

- **`@ConfigurationProperties`**: `AppProperties` binds the whole `app.*` group, typed and validated.
- **`@Value`**: `AppValueSettings` injects each key one by one.

Changing a value needs **no Java change**: edit the properties file, pass a command-line argument, set an environment variable, or switch profile (`dev` / `test`).

## Tech

Java 17 · Maven · **Spring Boot 3.5.6** (starter-web, starter-validation)

**Database:** none. This assignment is about configuration only, so no `aNN_` table is created in `Day_007_Assigments`.

## Structure

```text
08-external-config/
├── pom.xml
├── config-samples/
│   └── missing-owner.properties        failure sample (NOT on the classpath)
└── src/main/
    ├── java/com/company/boot/
    │   ├── ExternalConfigApplication.java     @SpringBootApplication + @EnableConfigurationProperties
    │   ├── config/
    │   │   ├── AppProperties.java             @ConfigurationProperties(prefix = "app") + @Validated
    │   │   └── AppValueSettings.java          @Value("${app.name}") ... one key at a time
    │   └── controller/
    │       └── AppInfoController.java         GET /api/app-info  and  /api/app-info/value
    └── resources/
        ├── application.properties             defaults (port 8080)
        ├── application-dev.properties         dev profile  (port 8081)
        └── application-test.properties        test profile (port 8082)
```

## How to run

1. **Eclipse:** **File → Import → Maven → Existing Maven Projects**, select `08-external-config`, then right-click `ExternalConfigApplication` → **Run As → Java Application**.
2. Open http://localhost:8080/api/app-info and http://localhost:8080/api/app-info/value.
3. **To change a value without Java changes**, use any one of these:
   - Edit `application.properties`, then stop and re-run.
   - **Run → Run Configurations… → Arguments → Program arguments:** `--app.owner=Priya`, or `--spring.profiles.active=dev` (then use port **8081**).
   - **Run Configurations → Environment tab:** add `APP_OWNER` = `Ops Team`.
4. **From a terminal:** run `mvn package`, then `java -jar target/external-config-0.0.1-SNAPSHOT.jar [--spring.profiles.active=dev] [--app.owner=...]`.

## Observed output

### 1. Default run

```text
No active profile set, falling back to 1 default profile: "default"
Tomcat started on port 8080 (http) with context path '/'

GET /api/app-info
{"source":"@ConfigurationProperties(prefix = \"app\")","name":"Inventory Service","version":"1.0.0","owner":"Mohit","maxUsers":50,"environment":"local","activeProfiles":[]}

GET /api/app-info/value
{"source":"@Value(\"${...}\")","name":"Inventory Service","version":"1.0.0","owner":"Mohit","supportEmail":"support@company.com"}
```

`supportEmail` is not in any file, so the `@Value` default `${app.support-email:support@company.com}` was used.

### 2. Acceptance: properties changed, Java untouched, response changed

Only these two lines of `application.properties` were edited:

```properties
app.version=1.2.0
app.owner=Inventory Platform Team
```

The rebuild proves no Java was recompiled:

```text
Copying 3 resources from src\main\resources to target\classes
Nothing to compile - all classes are up to date.
BUILD SUCCESS
```

The response after a restart:

```text
GET /api/app-info
{"source":"@ConfigurationProperties(prefix = \"app\")","name":"Inventory Service","version":"1.2.0","owner":"Inventory Platform Team","maxUsers":50,"environment":"local","activeProfiles":[]}

GET /api/app-info/value
{"source":"@Value(\"${...}\")","name":"Inventory Service","version":"1.2.0","owner":"Inventory Platform Team","supportEmail":"support@company.com"}
```

The file was then put back to its original values.

### 3. Changing values *without even rebuilding*: the same jar each time

| How | Command / setting | Response (`/api/app-info`) |
|---|---|---|
| Command-line argument | `--app.owner=Priya --app.version=2.0.0` | `"version":"2.0.0","owner":"Priya"` |
| Environment variable | `APP_OWNER=Ops Team`, `APP_MAXUSERS=75` | `"owner":"Ops Team","maxUsers":75` |
| Env var **and** command line | the env vars above plus `--app.owner=CommandLineWins` | `"owner":"CommandLineWins","maxUsers":75` (command line wins) |
| External file next to the jar | `./config/application.properties` containing `app.owner=External File Owner`, `app.version=3.0.0` | `"version":"3.0.0","owner":"External File Owner"` |

`APP_MAXUSERS` reaches `app.max-users` through **relaxed binding**: upper-case with `_` maps to lower-case with `.`, and dashes are dropped. (The temporary `config/` folder was deleted after the test. If you leave one in the project folder, it silently overrides your values when you run from Eclipse.)

## Optional extension: dev / test profiles

`application-dev.properties` and `application-test.properties` are loaded **on top of** `application.properties`. Only the keys they contain are overridden.

**`--spring.profiles.active=dev`**

```text
The following 1 profile is active: "dev"
Tomcat started on port 8081 (http) with context path '/'

GET /api/app-info
{"source":"@ConfigurationProperties(prefix = \"app\")","name":"Inventory Service (DEV)","version":"1.1.0-SNAPSHOT","owner":"Mohit","maxUsers":5,"environment":"development","activeProfiles":["dev"]}
GET /api/app-info/value
{"source":"@Value(\"${...}\")","name":"Inventory Service (DEV)","version":"1.1.0-SNAPSHOT","owner":"Mohit","supportEmail":"dev-team@company.com"}
```

`owner` is still `Mohit`: the dev file does not set it, so it is inherited from `application.properties`.

**`--spring.profiles.active=test`**

```text
The following 1 profile is active: "test"
Tomcat started on port 8082 (http) with context path '/'

GET /api/app-info
{"source":"@ConfigurationProperties(prefix = \"app\")","name":"Inventory Service (TEST)","version":"1.0.0-RC1","owner":"QA Team","maxUsers":20,"environment":"test","activeProfiles":["test"]}
GET /api/app-info/value
{"source":"@Value(\"${...}\")","name":"Inventory Service (TEST)","version":"1.0.0-RC1","owner":"QA Team","supportEmail":"qa@company.com"}
```

## Failure / edge-case evidence

**1. Missing key: `@Value` fails at startup.** Run with `--spring.config.location=file:config-samples/missing-owner.properties`. This replaces `application.properties`, and that file has no `app.owner`:

```text
Error creating bean with name 'appValueSettings' ...: Unexpected exception during bean creation
Caused by: org.springframework.util.PlaceholderResolutionException: Could not resolve placeholder 'app.owner' in value "${app.owner}"
Application run failed
```

A `@Value` without a default is a hard requirement. Adding a default (`${app.owner:Unknown}`), like `support-email` has, would avoid the failure.

**2. Blank value: only `@ConfigurationProperties` + `@Validated` catches it.** Run with `--app.owner=`:

```text
***************************
APPLICATION FAILED TO START
***************************

Description:

Binding to target com.company.boot.config.AppProperties failed:

    Property: app.owner
    Value: ""
    Origin: "app.owner" from property source "commandLineArgs"
    Reason: must not be blank

Action:

Update your application's configuration
```

`@Value("${app.owner}")` would accept `""` happily, because the key *exists*. The `@NotBlank` on `AppProperties` is what stops the start. Note that Boot also reports **where** the bad value came from (`commandLineArgs`).

**3. Wrong type.** Run with `--app.max-users=abc`:

```text
Description:

Failed to bind properties under 'app.max-users' to int:

    Property: app.max-users
    Value: "abc"
    Origin: "app.max-users" from property source "commandLineArgs"
    Reason: failed to convert java.lang.String to @jakarta.validation.constraints.Min int (caused by java.lang.NumberFormatException: For input string: "abc")
```

**4. Out-of-range value.** Run with `--app.max-users=0`:

```text
Binding to target com.company.boot.config.AppProperties failed:

    Property: app.maxUsers
    Value: "0"
    Origin: "app.max-users" from property source "commandLineArgs"
    Reason: must be greater than or equal to 1
```

**5. Typo in a key: silently ignored (no error!).** Run with `--app.max-user=99 --app.ownr=Typo`:

```text
{"...","owner":"Mohit","maxUsers":50, ...}      <- unchanged, the app started normally
```

Spring does not know `app.max-user` is a typo; it is just an unused key. Always check the response after changing configuration.

**6. Unknown profile: silently falls back.** Run with `--spring.profiles.active=prod` (there is no `application-prod.properties`):

```text
The following 1 profile is active: "prod"
{"...","version":"1.0.0","owner":"Mohit","maxUsers":50,"environment":"local","activeProfiles":["prod"]}
```

The profile is "active", but there is no file for it, so only the defaults apply.

## Explanation

**External configuration.** Values that change between environments are kept **outside the code**:
- URLs, ports, owners, limits, feature flags
- passwords, which never belong in code

The same jar then runs in dev, test and production with different settings. Boot collects properties from many sources into one `Environment`. When a key appears in several, the **higher** source wins. Here is a simplified order, highest first, as shown in section 3:

1. command-line arguments (`--app.owner=...`)
2. OS environment variables (`APP_OWNER`)
3. `config/application.properties` next to the jar (outside it)
4. `application-{profile}.properties` inside the jar
5. `application.properties` inside the jar
6. defaults in code (`${key:default}`, field initialisers)

**Profiles** (`spring.profiles.active`) select an extra file per environment, which overrides only the keys it contains.

**`@ConfigurationProperties` vs `@Value`:**

| | `@ConfigurationProperties(prefix = "app")` | `@Value("${app.owner}")` |
|---|---|---|
| Scope | binds a whole **group** into one typed object | one key per annotation |
| Key matching | **relaxed**: `app.max-users`, `APP_MAXUSERS` and `app.maxUsers` all match | exact key, written as a string |
| Missing key | field keeps its Java default (or `null`); combine with `@Validated` to make it required | startup fails unless a `:default` is given (failure 1) |
| Validation | yes, `@Validated` + `@NotBlank`/`@Min`... (failures 2 and 4) | no |
| Type conversion | yes, with a clear "Failed to bind" report (failure 3) | yes, but a less readable error |
| SpEL (`#{...}`) | no | yes |
| Best for | structured application settings shared by many classes | a single quick value in one class |

**Rule of thumb:** use `@ConfigurationProperties` for your own groups of settings, and `@Value` for one-off values.
