# 1. IoC Container: Bean Creation

**Concept:** Spring Core. `ProductService` is registered with `@Configuration` + `@Bean`, created by an `AnnotationConfigApplicationContext`, and retrieved from it. The application code never calls `new ProductService()`.

## Tech

Java 17 · Maven · Spring Framework 6.1.14 (`spring-context`) · no database

## Structure

```text
src/main/java/com/company/spring
├── config/   AppConfig     @Configuration with @Bean productService() and discountService()
│             NoBeanConfig  same recipe WITHOUT @Bean (failure evidence)
├── model/    Product       plain data class
├── service/  ProductService   plain class (no Spring annotations), displayProduct()
│             DiscountService  second, independent bean (optional extension)
└── app/      ProductApp       main: start container, get bean, call displayProduct()
              MissingBeanDemo  main: shows the failure when @Bean is missing
```

## How to run (Eclipse)

1. **File → Import → Maven → Existing Maven Projects**, then select `01-ioc-bean-creation`.
2. Make sure the project uses **JDK 17**: right-click the project → **Build Path → Configure Build Path → Libraries → JRE System Library → JavaSE-17**.
3. Right-click `ProductApp` → **Run As → Java Application**.
4. Right-click `MissingBeanDemo` → **Run As → Java Application** (failure evidence).

## Observed output: `ProductApp`

```text
1. Starting the Spring container (AnnotationConfigApplicationContext)...
   [ProductService] constructor called - object created (identity 32eff876)
   [DiscountService] constructor called - object created (identity 8dbdac1)
2. Container started.

3. Asking the container for ProductService...
-------------------------
Product ID   : 101
Product Name : Wireless Mouse
Category     : Electronics
Price        : Rs. 799.0
-------------------------

4. Proof that the object comes from Spring:
   Beans registered by AppConfig : [appConfig, productService, discountService]
   containsBean("productService") : true
   Same object by type and by name : true
   Same object on a second request : true  (singleton scope - created once, shared)
   Constructor ran BEFORE step 3   : see the [ProductService] line above

5. Optional extension - a second, independent bean:
Festival offer: 10% off on all products
   Wireless Mouse after discount : Rs. 719.1

6. Container closed.
```

(The identity numbers differ on every run.)

### Proof that the object is supplied by Spring

1. **No `new ProductService()` in the application.** `ProductApp` only calls `context.getBean(...)`.
2. **The constructor runs during container startup** (between steps 1 and 2), *before* `main` asks for the bean. The container created it on its own.
3. **The container knows the bean by name.** `productService` is listed in the container's bean definitions, and `containsBean` is `true`.
4. **The same instance is returned every time**, whether requested by type or by name. Spring created it once and manages it (singleton scope). `new` would make a different object each time.

## Failure evidence: removing `@Bean`

`NoBeanConfig` is identical to `AppConfig` except that `@Bean` is commented out. Observed output of `MissingBeanDemo`:

```text
Starting the container with NoBeanConfig (no @Bean)...
Container started. Notice: no [ProductService] constructor line.

containsBean("productService") : false
Asking the container for ProductService...

FAILED as expected:
  org.springframework.beans.factory.NoSuchBeanDefinitionException
  No qualifying bean of type 'com.company.spring.service.ProductService' available
```

**Why it fails:**
- Without `@Bean`, `productService()` is just an ordinary Java method, and Spring never calls it.
- So the container has no bean definition for `ProductService`. The constructor never runs, and `containsBean` is `false`.
- `getBean(ProductService.class)` therefore throws `NoSuchBeanDefinitionException`.

You can reproduce this in `AppConfig` too: comment out the `@Bean` above `productService()` and run `ProductApp`. The program stops at step 3 with the same exception.

## Explanation

**IoC (Inversion of Control):** normally your code creates its objects (`new ProductService()`) and decides how they are wired together. With IoC, that control is handed to a container. You describe *what* objects you need and *how* to build them, and the container creates them, connects them, and gives them to you.

**ApplicationContext:** Spring's IoC container. `AnnotationConfigApplicationContext` reads `@Configuration` classes and, at startup, creates every singleton bean they declare. Your code then gets them with `getBean(...)`. Closing the context, done here with try-with-resources, destroys the beans.

**@Configuration:** marks a class whose methods define beans.

**@Bean:** marks a method as a bean recipe. The container calls it once, stores the returned object under the method name (`productService`), and returns that same object to anyone who asks.

**Why the container manages the object:**
- **One place decides how objects are built and wired.** Classes like `ProductService` stay plain Java and don't need to know how their dependencies are created.
- **Lifecycle and scope come for free:** one shared instance (singleton), creation at startup, cleanup at shutdown.
- **Implementations can be swapped by changing configuration**, for example a different service or a test double, without touching the code that uses them.
- **Lifecycle features need container ownership.** Dependency injection, transactions and AOP (used in later assignments) only work on objects the container owns.

**Optional extension:** `DiscountService` is a second, independent bean in the same container. Both are created at startup and retrieved separately with `getBean`.
