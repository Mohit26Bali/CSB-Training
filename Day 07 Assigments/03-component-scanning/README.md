# 3. Component Scanning: Stereotypes

**Concept:** Spring Core. `CustomerRepository` (`@Repository`), `CustomerService` (`@Service`) and `CustomerComponent` (`@Component`) are discovered automatically by `@ComponentScan`. The repository is injected into the service, and the service into the component. The repository reads real data from MySQL.

## Tech

Java 17 · Maven · Spring Framework 6.1.14 · MySQL 8 (`mysql-connector-j`)

**Database:** shared Day 07 database `Day_007_Assigments`. Table **`a03_customers`** uses the `a03_` prefix, so it is uniquely identifiable as Assignment 3.

## Structure

```text
src/main/java/com/company/spring
├── config/
│   ├── AppConfig                @ComponentScan("com.company.spring.customer") + @Bean DataSource
│   └── MissingRepositoryConfig  same scan but CustomerRepository excluded (edge case)
├── customer/                    <- THE SCANNED PACKAGE (and everything below it)
│   ├── model/Customer           plain class, no stereotype -> NOT a bean
│   ├── repository/CustomerRepository   @Repository - SQL on a03_customers
│   ├── service/CustomerService         @Service    - business rules
│   └── component/CustomerComponent     @Component  - prints the report
├── outside/LoyaltyComponent     @Component but OUTSIDE the scan package (optional extension)
└── app/
    ├── CustomerApp              main program
    └── OutsideScanDemo          edge cases
src/main/resources
├── schema.sql                   creates Day_007_Assigments (if needed) + a03_customers (5 rows)
└── db.properties                connection settings (git-ignored)
```

**Dependency chain** (all constructor injection):

```text
CustomerComponent -> CustomerService -> CustomerRepository -> DataSource -> MySQL
```

## How to run

1. Run `src/main/resources/schema.sql` in MySQL. It creates the database if it doesn't exist and only drops/recreates `a03_customers`.
2. Copy `src/main/resources/db.properties.example` to `db.properties` and set your MySQL password (git-ignored).
3. In Eclipse: **File → Import → Maven → Existing Maven Projects**, select `03-component-scanning`, and set the JRE to **JavaSE-17**.
4. Run `CustomerApp`, then `OutsideScanDemo` (**Run As → Java Application**).

## Observed output: `CustomerApp`

```text
1. Starting the container - scanning com.company.spring.customer ...
   [@Repository CustomerRepository] created, DataSource injected
   [@Service    CustomerService]    created, CustomerRepository injected
   [@Component  CustomerComponent]  created, CustomerService injected
2. Container started.

3. Beans in the container and how each was registered:
   ---------------------------------------------------------------------
   Bean name            Registered by  Class
   ---------------------------------------------------------------------
   appConfig            @Configuration com.company.spring.config.AppConfig
   customerComponent    @Component     com.company.spring.customer.component.CustomerComponent
   customerRepository   @Repository    com.company.spring.customer.repository.CustomerRepository
   customerService      @Service       com.company.spring.customer.service.CustomerService
   dataSource           @Bean method   com.mysql.cj.jdbc.MysqlDataSource
   ---------------------------------------------------------------------
   Not a bean: Customer (no stereotype), LoyaltyComponent (outside the scan package)

4. Running the report: CustomerComponent -> CustomerService -> CustomerRepository

ALL CUSTOMERS (from table a03_customers)
-----------------------------------------------------------------
ID   Name            Email                        City
-----------------------------------------------------------------
1    Ravi Kumar      ravi.kumar@example.com       Pune
2    Priya Sharma    priya.sharma@example.com     Mumbai
3    Amit Verma      amit.verma@example.com       Pune
4    Neha Iyer       neha.iyer@example.com        Bengaluru
5    Karan Patel     karan.patel@example.com      Pune

CUSTOMERS IN PUNE
-----------------------------------------------------------------
ID   Name            Email                        City
-----------------------------------------------------------------
3    Amit Verma      amit.verma@example.com       Pune
5    Karan Patel     karan.patel@example.com      Pune
1    Ravi Kumar      ravi.kumar@example.com       Pune

CUSTOMER 2: Priya Sharma
TOTAL CUSTOMERS: 5

5. Container closed.
```

### Proof that Spring discovered the classes automatically

- **Nothing registers these classes by hand:** `AppConfig` has no `@Bean` method for any of them, and nothing calls `new`. The only instruction is `@ComponentScan("com.company.spring.customer")`.
- **All three were found and created** at startup, and the table shows which stereotype each was found by.
- **Spring worked out the creation order from the constructors:** repository first, then service, then component.
- **Only annotated classes become beans.** `Customer` is in the scanned package but has no stereotype, so it is not a bean.
- **`@Bean` and `@Configuration` beans appear as well.** `dataSource` comes from a `@Bean` method because `MysqlDataSource` is a library class we can't annotate, and `appConfig` is the configuration class itself.

## Edge cases: `OutsideScanDemo` (optional extension)

```text
CASE 1 - @Component class outside the scanned package

   [@Repository CustomerRepository] created, DataSource injected
   [@Service    CustomerService]    created, CustomerRepository injected
   [@Component  CustomerComponent]  created, CustomerService injected

   Notice: no [LoyaltyComponent] created line above.
   containsBean("loyaltyComponent") : false
   getBean(LoyaltyComponent.class) failed:
   NoSuchBeanDefinitionException: No qualifying bean of type 'com.company.spring.outside.LoyaltyComponent' available

CASE 2 - CustomerRepository outside the scan, but CustomerService needs it

   STARTUP FAILED:
   UnsatisfiedDependencyException: Error creating bean with name 'customerComponent' ...
     Unsatisfied dependency expressed through constructor parameter 0:
     Error creating bean with name 'customerService' ...
     Unsatisfied dependency expressed through constructor parameter 0:
     No qualifying bean of type 'com.company.spring.customer.repository.CustomerRepository' available:
     expected at least 1 bean which qualifies as autowire candidate.
```

**What we observe:**

1. **Case 1, a class nobody depends on.** `LoyaltyComponent` has `@Component` but lives in `com.company.spring.outside`. Spring never looks there, so the class is silently ignored: no error, no bean. The annotation alone does nothing without a scan that covers its package. You only notice when you ask for it (`NoSuchBeanDefinitionException`).
2. **Case 2, a class something depends on.** `MissingRepositoryConfig` excludes `CustomerRepository` from the scan, which is exactly what happens when the class is moved to an unscanned package. Because `CustomerService` needs it, the container **fails at startup**. The message shows the whole chain:
   ```
   customerComponent → customerService → (missing) CustomerRepository
   ```

**Fix:** move the class back under `com.company.spring.customer`, or widen the scan, e.g. `@ComponentScan({"com.company.spring.customer", "com.company.spring.outside"})`.

## Explanation

**Component scanning:**
- `@ComponentScan(basePackages = "...")` tells the container to search that package and **all its sub-packages** at startup.
- Every class annotated with `@Component`, or with an annotation built on it (`@Service`, `@Repository`, `@Controller`, `@Configuration`), becomes a bean.
- Its constructor dependencies are then injected automatically.
- **No list of beans has to be maintained by hand:** adding a new annotated class to the package is enough.
- **The scan is limited to the given packages.** Classes outside them are invisible, which is why package structure matters. Spring Boot scans the package of the main class and everything below it.

**Stereotype annotations.** All four register a bean. They differ in **meaning**, the layer the class belongs to, and some carry extra behaviour:

| Annotation | Layer / meaning | Extra behaviour |
|---|---|---|
| `@Component` | Generic Spring-managed class (helpers, printers, schedulers…) | none; it is the base annotation |
| `@Repository` | Data-access layer: talks to the database | Eligible for **persistence exception translation**: database-specific exceptions are converted to Spring's `DataAccessException` hierarchy (enabled automatically in Spring Boot) |
| `@Service` | Business layer: rules and use cases | none today; documents intent and is a natural target for transactions/AOP |
| `@Controller` / `@RestController` | Web layer: handles HTTP requests (Spring MVC) | Request mapping |

**Why use specific stereotypes instead of `@Component` everywhere?**
- **Readability:** anyone can see the layered architecture (repository → service → component/controller) from the annotations alone.
- **Tooling and AOP can target a layer**, e.g. "log every `@Service` method" or "translate exceptions of every `@Repository`".
- **Future-proofing:** Spring can attach more layer-specific behaviour to these annotations without code changes.
