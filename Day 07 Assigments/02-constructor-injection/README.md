# 2. Dependency Injection: Constructor Injection

**Concept:** Spring Core. `EmailService` and `NotificationService` are annotated with `@Service`. Spring creates both and passes the `EmailService` bean into `NotificationService`'s constructor. The code never calls `new EmailService()`.

## Tech

Java 17 · Maven · Spring Framework 6.1.14 (`spring-context`) · no database (nothing to store in this assignment)

## Structure

```text
src/main/java/com/company/spring
├── config/
│   ├── AppConfig            @Configuration + @ComponentScan("...service")
│   └── MissingEmailConfig   registers NotificationService only (failure evidence)
├── service/
│   ├── EmailService         @Service - sendEmail()
│   └── NotificationService  @Service - EmailService injected through the constructor, notifyUser()
├── compare/                 optional extension
│   ├── ComparisonConfig     scans service + compare.beans
│   ├── SetterMissingConfig  setter-injected service with NO EmailService
│   └── beans/SetterNotificationService   @Autowired(required = false) setter injection
└── app/
    ├── NotificationApp          main program
    ├── MissingBeanStartupDemo   missing-bean startup error
    └── InjectionComparisonApp   constructor vs setter injection
```

## How to run (Eclipse)

1. **File → Import → Maven → Existing Maven Projects**, then select `02-constructor-injection`.
2. Set **JRE System Library → JavaSE-17** (Build Path → Configure Build Path → Libraries).
3. Right-click each class in `app/` → **Run As → Java Application**:
   - `NotificationApp` (main program)
   - `MissingBeanStartupDemo` (failure evidence)
   - `InjectionComparisonApp` (optional extension)

## Observed output: `NotificationApp`

```text
1. Starting the Spring container (component scan)...
   [EmailService] created by Spring (identity 7e07db1f)
   [NotificationService] created by Spring; constructor received EmailService (identity 7e07db1f)
2. Container started.

3. Calling notifyUser():
   Notifying ravi.kumar@example.com...
   EMAIL -> To: ravi.kumar@example.com
            Subject: Notification from CSB Bank
            Body: Your account statement for October is ready.

4. Proof that the constructor received a Spring-managed object:
   Beans found by component scan    : [appConfig, emailService, notificationService]
   EmailService bean in container   : 7e07db1f
   EmailService inside Notification : 7e07db1f
   Same object (==)                 : true
   EmailService created before NotificationService: see the order of lines in step 1

5. Container closed.
```

### Proof that the constructor receives a Spring-managed object

- **There is no `new EmailService()` or `new NotificationService()`** anywhere in the code.
- **Both classes were found by component scan:** `emailService` and `notificationService` appear in the container's bean list.
- **Spring worked out the creation order.** `EmailService` is created first, because `NotificationService` depends on it, and then passed into the constructor.
- **The object received by the constructor is the container's bean.** It has the same identity (`7e07db1f`) as the `EmailService` that `context.getBean(EmailService.class)` returns, and `==` is `true`.

## Failure evidence: missing bean at startup (`MissingBeanStartupDemo`)

`MissingEmailConfig` registers `NotificationService` but no `EmailService`, which is exactly what happens if `@Service` is removed from `EmailService`.

```text
Starting the container WITHOUT an EmailService bean...
WARNING: Exception encountered during context initialization - cancelling refresh attempt: ...

STARTUP FAILED as expected:
  Exception : org.springframework.beans.factory.UnsatisfiedDependencyException
  Message   : Error creating bean with name 'com.company.spring.service.NotificationService':
              Unsatisfied dependency expressed through constructor parameter 0:
              No qualifying bean of type 'com.company.spring.service.EmailService' available:
              expected at least 1 bean which qualifies as autowire candidate. Dependency annotations: {}
  Root cause: org.springframework.beans.factory.NoSuchBeanDefinitionException
              No qualifying bean of type 'com.company.spring.service.EmailService' available: ...
```

**What this shows:**
- The error happens while the container is **starting**. `getBean()` is never reached.
- The message says exactly what is missing (`EmailService`) and where it was needed (constructor parameter 0 of `NotificationService`).
- You can reproduce it in the real app: comment out `@Service` on `EmailService` and run `NotificationApp`.

## Optional extension: constructor vs setter injection (`InjectionComparisonApp`)

```text
PART A - both injection styles, EmailService available

   [EmailService] created by Spring (identity 16d04d3d)
   [NotificationService] created by Spring; constructor received EmailService (identity 16d04d3d)
   [SetterNotificationService] created (emailService is still null at this point)
   [SetterNotificationService] setter called by Spring with EmailService (identity 16d04d3d)
   ... both services send their email successfully ...

PART B - setter injection (required = false), NO EmailService bean

   [SetterNotificationService] created (emailService is still null at this point)
   Container started successfully - the missing dependency went unnoticed.
   Notifying amit.verma@example.com (setter-injected service)...

   FAILED LATER, at run time:
   java.lang.NullPointerException: Cannot invoke "com.company.spring.service.EmailService.sendEmail(...)"
   because "this.emailService" is null
```

| | Constructor injection | Setter injection |
|---|---|---|
| When the dependency arrives | While the object is being created | After the object exists (there is a moment where it is `null`) |
| Field | Can be `final`, so it never changes and is never null | Cannot be `final`; can be changed later by calling the setter |
| Missing dependency | App fails at **startup** with a clear message | Required setter: also fails at startup. **Optional** setter (`required = false`): the app starts and fails **later** with a `NullPointerException` |
| Shows dependencies | Yes: the constructor lists everything the class needs | Hidden among the setters |
| Testing without Spring | `new NotificationService(fakeEmail)`, impossible to forget | Must remember to call every setter |
| Good for | **Required** dependencies (the normal case; Spring's recommendation) | Truly **optional** dependencies, or ones that must be changeable after creation |

Both styles received the **same** `EmailService` bean (`16d04d3d`). It is a singleton shared by everyone who needs it.

## Explanation

**Dependency injection (DI):** a class doesn't create the objects it depends on; they are handed to it from outside. `NotificationService` only says "I need an `EmailService`", and the Spring container supplies one. This is IoC applied to dependencies: the container, not the class, decides which object is used and creates it.

**Constructor injection:** dependencies are declared as constructor parameters. When Spring creates the bean it finds a matching bean for each parameter and passes it in. With a single constructor, `@Autowired` is optional (Spring 4.3+). The benefits:
- the object is never half-built
- the field can be `final`
- dependencies are visible in one place
- unit tests can pass a fake (`new NotificationService(fakeEmailService)`) without Spring

**`@Service` and component scanning:** `@Service` (a specialised `@Component`) marks a class as a bean. `@ComponentScan` makes the container search a package for such classes and register them automatically. No `@Bean` methods are needed, unlike Assignment 1.

**Missing-bean startup errors:**
- When a bean needs a dependency that no bean can satisfy, Spring throws `UnsatisfiedDependencyException`, caused by `NoSuchBeanDefinitionException`, and **refuses to start**.
- This is a good thing: the mistake is found immediately at startup, not hours later when a user triggers that code path.
- Typical causes:
  - a missing `@Service`/`@Component`
  - the class sits outside the `@ComponentScan` package
  - a typo in the type
  - the class was never registered
