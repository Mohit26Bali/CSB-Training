# Java Design Patterns: Detailed Notes

A **design pattern** is a named, proven solution to a problem that keeps coming back in object-oriented design. Patterns give teams a shared vocabulary ("use a strategy here") and show how to write code that is easy to extend. These notes cover the patterns that matter most in everyday Java and Spring work, each with the problem, a runnable example, where the JDK or Spring already uses it, and when **not** to use it.

> Every example was compiled and run on Java 21. Patterns are tools, not goals: many are needed less in modern Java (lambdas, records, enums, dependency injection) than the original 1994 book assumed, and the notes say so.

---

## Table of Contents

1. [What Patterns Are and the Principles Behind Them](#1-what-patterns-are-and-the-principles-behind-them)
2. [Singleton](#2-singleton)
3. [Factory Method and Static Factories](#3-factory-method-and-static-factories)
4. [Builder](#4-builder)
5. [Strategy](#5-strategy)
6. [Observer](#6-observer)
7. [Decorator](#7-decorator)
8. [Adapter and Facade](#8-adapter-and-facade)
9. [Proxy](#9-proxy)
10. [Template Method](#10-template-method)
11. [Command and Chain of Responsibility](#11-command-and-chain-of-responsibility)
12. [State](#12-state)
13. [Dependency Injection and Inversion of Control](#13-dependency-injection-and-inversion-of-control)
14. [Patterns in the JDK and Spring](#14-patterns-in-the-jdk-and-spring)
15. [Anti-Patterns](#15-anti-patterns)
16. [Common Mistakes](#16-common-mistakes)
17. [Interview Questions](#17-interview-questions)
18. [Cheat Sheet](#18-cheat-sheet)
19. [Practice Tasks](#19-practice-tasks)

---

## 1. What Patterns Are and the Principles Behind Them

The classic catalogue ("Gang of Four", 1994) has 23 patterns in three groups:

| Group | Concern | Examples |
|---|---|---|
| **Creational** | how objects are created | Singleton, Factory Method, Abstract Factory, Builder, Prototype |
| **Structural** | how objects are composed | Adapter, Decorator, Facade, Proxy, Composite, Bridge |
| **Behavioural** | how objects interact and share work | Strategy, Observer, Template Method, Command, State, Chain of Responsibility, Iterator |

Patterns rest on a few principles. **SOLID** is the best-known set:

| Letter | Principle | In one line |
|---|---|---|
| **S** | Single Responsibility | a class has one reason to change |
| **O** | Open/Closed | open for extension, closed for modification: add behaviour by adding code, not editing old code |
| **L** | Liskov Substitution | a subclass must work wherever its parent is expected |
| **I** | Interface Segregation | many small interfaces beat one fat one |
| **D** | Dependency Inversion | depend on abstractions (interfaces), not concrete classes |

Two more guidelines: **program to an interface, not an implementation**, and **favour composition over inheritance** (build behaviour by combining objects instead of deep class hierarchies).

---

## 2. Singleton

**Problem:** exactly one instance of a class (a configuration, a connection pool, a registry), reachable globally.

### The best form in Java: an enum

```java
enum AppConfig {
    INSTANCE;
    private final Map<String, String> values = new ConcurrentHashMap<>();
    void set(String k, String v) { values.put(k, v); }
    String get(String k) { return values.get(k); }
}

AppConfig.INSTANCE.set("env", "prod");
System.out.println(AppConfig.INSTANCE.get("env"));   // prod
```

The JVM guarantees one instance, creates it **thread-safely**, and an enum is immune to the two classic breakers: **serialization** (a deserialized enum is the same constant) and **reflection** (you cannot construct an enum).

### Lazy and thread-safe: the holder idiom

```java
class Registry {
    private Registry() { }
    private static class Holder { static final Registry INSTANCE = new Registry(); }   // loaded on first use
    static Registry getInstance() { return Holder.INSTANCE; }
}
```

The nested class is initialized only when `getInstance()` first touches it, and class initialization is thread-safe.

### Double-checked locking (only with `volatile`)

```java
class Lazy {
    private static volatile Lazy instance;                // volatile is REQUIRED
    private Lazy() { }
    static Lazy getInstance() {
        Lazy local = instance;
        if (local == null) {
            synchronized (Lazy.class) {
                local = instance;
                if (local == null) instance = local = new Lazy();
            }
        }
        return local;
    }
}
```

Without `volatile` another thread can see a half-constructed object (the memory-model issue in the concurrency notes). Prefer the holder or the enum.

### Problems with singletons

- They are **global state**: hidden dependencies, hard to test (you cannot swap the instance for a fake).
- They tangle with class loaders (one per loader, not per JVM) and with serialization/reflection unless you use an enum.
- **In Spring you almost never write one**: every bean is a singleton **per container** by default (`@Scope("singleton")`), created and injected for you, and tests can replace it. Use that.

---

## 3. Factory Method and Static Factories

**Problem:** code should not depend on *which* concrete class it creates, or creation logic is complex.

### Simple (static) factory

```java
interface Notifier { void send(String message); }
class EmailNotifier implements Notifier { public void send(String m) { System.out.println("email: " + m); } }
class SmsNotifier   implements Notifier { public void send(String m) { System.out.println("sms: " + m); } }

class NotifierFactory {
    static Notifier create(String type) {
        return switch (type) {
            case "email" -> new EmailNotifier();
            case "sms"   -> new SmsNotifier();
            default -> throw new IllegalArgumentException("unknown type: " + type);
        };
    }
}

NotifierFactory.create("sms").send("hello");        // sms: hello
```

The caller depends on `Notifier`, not on the classes. Adding "push" changes only the factory.

### Factory Method (subclasses decide)

```java
abstract class Report {
    abstract Exporter createExporter();                 // the factory method
    void export(String data) { createExporter().write(data); }
}
class PdfReport extends Report { Exporter createExporter() { return new PdfExporter(); } }
```

### Static factory methods in the JDK (and your own classes)

`List.of(...)`, `Optional.of(...)`, `LocalDate.of(...)`, `Integer.valueOf(...)`, `Map.entry(...)`. Compared with constructors, static factories have **names** (`LocalDate.now()`), may **return a cached instance** (`Integer.valueOf`), may return a **subtype** (`List.of` returns a private class), and can **hide the implementation**. This is "Effective Java" Item 1.

**Abstract Factory** is a factory of related factories (a UI toolkit producing matching buttons and menus for each look and feel). It is rare in application code; Spring's `@Configuration` classes and profiles do the same job.

---

## 4. Builder

**Problem:** a class with many optional parameters. A constructor with eight arguments is unreadable (`new Pizza("L", true, false, true, ...)`), and telescoping constructors multiply.

```java
class Pizza {
    private final String size;
    private final boolean cheese;
    private final List<String> toppings;
    private Pizza(Builder b) { this.size = b.size; this.cheese = b.cheese; this.toppings = List.copyOf(b.toppings); }

    static Builder builder(String size) { return new Builder(size); }

    static class Builder {
        private final String size;                       // required
        private boolean cheese = true;                   // optional, with a default
        private final List<String> toppings = new ArrayList<>();
        private Builder(String size) { this.size = size; }
        Builder cheese(boolean c) { this.cheese = c; return this; }
        Builder topping(String t) { this.toppings.add(t); return this; }
        Pizza build() {
            if (toppings.size() > 5) throw new IllegalStateException("too many toppings");   // validate here
            return new Pizza(this);
        }
    }
    @Override public String toString() { return size + " cheese=" + cheese + " " + toppings; }
}

Pizza p = Pizza.builder("large").topping("olives").topping("corn").cheese(false).build();
System.out.println(p);                                   // large cheese=false [olives, corn]
```

Benefits: readable call sites, required vs optional fields, **validation in one place**, and an **immutable** result.

In practice:

- **Lombok** `@Builder` generates all of the above. Records with many fields often get one.
- The JDK has `StringBuilder`, `HttpRequest.newBuilder()`, `Stream.builder()`.
- For two or three parameters just use a constructor or a record: a builder would be noise.

---

## 5. Strategy

**Problem:** several interchangeable ways to do one thing (shipping cost, discounts, sorting, payment), selected at run time, instead of a long `if / else` or `switch`.

```java
interface DiscountStrategy { double apply(double price); }

class Checkout {
    private final DiscountStrategy strategy;
    Checkout(DiscountStrategy strategy) { this.strategy = strategy; }
    double total(double price) { return strategy.apply(price); }
}

DiscountStrategy none   = price -> price;
DiscountStrategy tenOff = price -> price * 0.90;
DiscountStrategy flat50 = price -> Math.max(0, price - 50);

System.out.println(new Checkout(none).total(200));       // 200.0
System.out.println(new Checkout(tenOff).total(200));     // 180.0
System.out.println(new Checkout(flat50).total(200));     // 150.0
```

Because a strategy with one method is a **functional interface**, a lambda is the strategy: no class per variant. A `Map<String, DiscountStrategy>` replaces the `switch` and follows the Open/Closed principle (add a strategy, change no existing code).

**JDK and Spring:** `Comparator` (sorting strategy), `Collections.sort(list, comparator)`, `Predicate`/`Function` everywhere, Spring Security's `PasswordEncoder` and `AuthenticationProvider`, Spring's `ResourceLoader`.

---

## 6. Observer

**Problem:** when one object changes, many others must react, without the subject knowing who they are (events, notifications, UI updates).

```java
interface OrderListener { void onPlaced(String orderId); }

class OrderService {
    private final List<OrderListener> listeners = new ArrayList<>();
    void subscribe(OrderListener l) { listeners.add(l); }
    void place(String orderId) {
        System.out.println("order " + orderId + " saved");
        listeners.forEach(l -> l.onPlaced(orderId));          // tell everyone
    }
}

OrderService service = new OrderService();
service.subscribe(id -> System.out.println("email sent for " + id));
service.subscribe(id -> System.out.println("stock reduced for " + id));
service.place("A1");
// order A1 saved
// email sent for A1
// stock reduced for A1
```

The service does not import the email or stock code: add a listener, change nothing else.

**Spring:** the built-in event system is this pattern.

```java
record OrderPlaced(String orderId) { }

@Service class OrderService {
    private final ApplicationEventPublisher events;
    OrderService(ApplicationEventPublisher events) { this.events = events; }
    void place(String id) { /* save */ events.publishEvent(new OrderPlaced(id)); }
}

@Component class EmailSender {
    @EventListener void on(OrderPlaced e) { /* send mail */ }
    // @TransactionalEventListener(phase = AFTER_COMMIT) waits until the transaction commits
}
```

Beware: listeners run **synchronously** in the publisher's thread unless made `@Async`, an exception in one can stop the rest, order is not guaranteed unless `@Order`, and a leaked listener keeps its object alive (a memory leak). For cross-service events use a message broker (Kafka, RabbitMQ). The JDK's old `Observable`/`Observer` classes are deprecated: use your own interface, `PropertyChangeListener`, or `java.util.concurrent.Flow`.

---

## 7. Decorator

**Problem:** add behaviour to an object **dynamically**, in layers, without subclassing for every combination (coffee with milk, with sugar, with milk and sugar...).

```java
interface Coffee { double cost(); String description(); }

class Espresso implements Coffee {
    public double cost() { return 80; }
    public String description() { return "espresso"; }
}

abstract class CoffeeDecorator implements Coffee {            // wraps another Coffee and implements the same interface
    protected final Coffee inner;
    CoffeeDecorator(Coffee inner) { this.inner = inner; }
}
class Milk extends CoffeeDecorator {
    Milk(Coffee c) { super(c); }
    public double cost() { return inner.cost() + 15; }
    public String description() { return inner.description() + " + milk"; }
}
class Sugar extends CoffeeDecorator {
    Sugar(Coffee c) { super(c); }
    public double cost() { return inner.cost() + 5; }
    public String description() { return inner.description() + " + sugar"; }
}

Coffee order = new Sugar(new Milk(new Espresso()));
System.out.println(order.description() + " = " + order.cost());    // espresso + milk + sugar = 100.0
```

**JDK:** the whole `java.io` library: `new BufferedReader(new InputStreamReader(new FileInputStream(f)))` is three decorators. `Collections.unmodifiableList(list)` and `Collections.synchronizedList(list)` wrap a list. **Spring:** `HttpServletRequestWrapper`, the security filter chain wrapping requests.

Decorator vs inheritance: with inheritance you need a class per combination (`MilkSugarEspresso`); with decorators you combine at run time.

---

## 8. Adapter and Facade

### Adapter: make an incompatible interface fit

**Problem:** you have a class with the wrong interface (a legacy or third-party API) and code that expects another.

```java
interface PaymentGateway { void pay(int rupees); }

class LegacyBank {                                          // cannot be changed
    void makePayment(double dollars, String currency) { System.out.println("paid " + dollars + " " + currency); }
}

class LegacyBankAdapter implements PaymentGateway {
    private final LegacyBank bank;
    LegacyBankAdapter(LegacyBank bank) { this.bank = bank; }
    public void pay(int rupees) { bank.makePayment(rupees / 83.0, "USD"); }     // translate
}

PaymentGateway gateway = new LegacyBankAdapter(new LegacyBank());
gateway.pay(830);                                           // paid 10.0 USD
```

**JDK:** `Arrays.asList(array)` adapts an array to `List`; `InputStreamReader` adapts a byte stream to a character stream; `Collections.enumeration`. **Spring:** `HandlerAdapter`, `WebMvcConfigurer` adapters, ports-and-adapters architecture (hexagonal).

### Facade: one simple door to a complicated subsystem

```java
class OrderFacade {
    private final Inventory inventory = new Inventory();
    private final Payments payments = new Payments();
    private final Shipping shipping = new Shipping();

    String checkout(String sku, int amount) {            // callers use ONE method
        inventory.reserve(sku);
        payments.charge(amount);
        return shipping.schedule(sku);
    }
}
```

A Spring `@Service` that coordinates several repositories and clients is a facade for the controller. Do not let a facade grow business rules of its own, or it becomes a "god class".

---

## 9. Proxy

**Problem:** control access to an object: add checks, logging, lazy loading, caching, or remote calls **without changing the object or its callers**. The proxy implements the same interface and forwards to the real object.

### A hand-written proxy

```java
interface Account { void withdraw(int amount); }

class RealAccount implements Account {
    public void withdraw(int amount) { System.out.println("withdrew " + amount); }
}

class SecureAccount implements Account {                    // the proxy
    private final Account real;
    private final boolean admin;
    SecureAccount(Account real, boolean admin) { this.real = real; this.admin = admin; }
    public void withdraw(int amount) {
        if (!admin && amount > 1000) throw new SecurityException("limit exceeded");
        real.withdraw(amount);
    }
}
```

### A dynamic proxy: one handler for every method

```java
Account logged = (Account) Proxy.newProxyInstance(
    Account.class.getClassLoader(),
    new Class<?>[] { Account.class },
    (proxy, method, args) -> {
        System.out.println("calling " + method.getName());
        return method.invoke(new RealAccount(), args);          // forward
    });
logged.withdraw(50);
// calling withdraw
// withdrew 50
```

`java.lang.reflect.Proxy` builds the proxy class at run time from an interface; **CGLIB/ByteBuddy** do the same for classes without an interface.

**This is how Spring works.** `@Transactional`, `@Cacheable`, `@Async`, `@PreAuthorize`, Spring AOP and Spring Data repositories are all proxies: the bean you inject is a proxy that runs the extra behaviour around your method. That is also why calling a method on `this` skips it (see the Spring configuration and AOP notes). **JPA/Hibernate** returns lazy proxies for associations. **RMI** and Feign clients proxy remote calls.

| | Proxy | Decorator |
|---|---|---|
| Intent | **control access** to the same object | **add behaviour** |
| Creates the target | often itself (lazy load) | receives it from outside |
| Stacked in layers | rarely | typically |

---

## 10. Template Method

**Problem:** several algorithms share the same **steps in the same order** but differ in some steps. Put the skeleton in a base class and let subclasses fill the gaps.

```java
abstract class DataImporter {
    final void run() {                                       // the template: final, so the order cannot be changed
        String raw = read();
        String cleaned = clean(raw);
        save(cleaned);
    }
    abstract String read();                                  // each subclass supplies this
    String clean(String raw) { return raw.trim(); }          // a default that may be overridden (a "hook")
    abstract void save(String data);
}

class CsvImporter extends DataImporter {
    String read() { return "  a,b,c  "; }
    void save(String data) { System.out.println("saved " + data); }
}

new CsvImporter().run();                                     // saved a,b,c
```

**Spring** is full of templates: `JdbcTemplate`, `RestTemplate`, `TransactionTemplate`, `JmsTemplate` (the template handles connections, errors and cleanup; you pass the varying part as a callback or lambda). `HttpServlet.service()` calls `doGet`/`doPost`. The inheritance version is rigid; the **callback/lambda** form (pass the changing step in) often replaces it.

---

## 11. Command and Chain of Responsibility

### Command: turn a request into an object

**Problem:** queue, log, undo or schedule operations.

```java
interface Command { void execute(); void undo(); }

class AddText implements Command {
    private final StringBuilder doc; private final String text;
    AddText(StringBuilder doc, String text) { this.doc = doc; this.text = text; }
    public void execute() { doc.append(text); }
    public void undo() { doc.setLength(doc.length() - text.length()); }
}

StringBuilder doc = new StringBuilder();
Deque<Command> history = new ArrayDeque<>();
for (String word : List.of("Hello", " world")) {
    Command c = new AddText(doc, word);
    c.execute();
    history.push(c);
}
System.out.println(doc);                 // Hello world
history.pop().undo();
System.out.println(doc);                 // Hello
```

`Runnable` and `Callable` are commands; so are jobs submitted to an `ExecutorService`, and the `Command` objects of CQRS architectures.

### Chain of Responsibility: pass a request along handlers until one handles it

```java
abstract class Handler {
    private Handler next;
    Handler then(Handler next) { this.next = next; return next; }
    void handle(int amount) {
        if (canHandle(amount)) process(amount);
        else if (next != null) next.handle(amount);
        else System.out.println("nobody can approve " + amount);
    }
    abstract boolean canHandle(int amount);
    abstract void process(int amount);
}
class Manager extends Handler {
    boolean canHandle(int a) { return a <= 1_000; }
    void process(int a) { System.out.println("manager approved " + a); }
}
class Director extends Handler {
    boolean canHandle(int a) { return a <= 10_000; }
    void process(int a) { System.out.println("director approved " + a); }
}

Handler chain = new Manager();
chain.then(new Director());
chain.handle(500);       // manager approved 500
chain.handle(5_000);     // director approved 5000
chain.handle(50_000);    // nobody can approve 50000
```

**This is the servlet and Spring Security filter chain**: each `Filter` can handle the request, pass it on with `chain.doFilter(...)`, or stop it (return `401`). Also logging handlers, middleware, and interceptors.

---

## 12. State

**Problem:** an object behaves differently depending on its internal state, and `if (state == ...)` chains spread through every method.

```java
interface OrderState {
    OrderState pay();
    OrderState ship();
    String name();
}

enum Order implements OrderState {                           // an enum is a neat way to write a state machine
    NEW {
        public OrderState pay()  { return PAID; }
        public OrderState ship() { throw new IllegalStateException("cannot ship an unpaid order"); }
    },
    PAID {
        public OrderState pay()  { throw new IllegalStateException("already paid"); }
        public OrderState ship() { return SHIPPED; }
    },
    SHIPPED {
        public OrderState pay()  { throw new IllegalStateException("already paid"); }
        public OrderState ship() { throw new IllegalStateException("already shipped"); }
    };
    public String toString() { return name(); }
}

OrderState s = Order.NEW;
s = s.pay();
s = s.ship();
System.out.println(s);                                       // SHIPPED
```

Illegal moves are rejected by the state itself, not by scattered checks. Real systems use the **Spring State Machine** library or a status column plus a transition table. (The Enums notes show the `OrderState` enum with `next()`.)

---

## 13. Dependency Injection and Inversion of Control

**Problem:** a class that creates its own collaborators (`new EmailSender()`) is welded to them and cannot be tested in isolation.

```java
// Tightly coupled: cannot test without sending a real email
class SignupService {
    private final EmailSender sender = new EmailSender();
}

// Dependency injection: the collaborator is GIVEN to the class
class SignupService {
    private final Notifier notifier;
    SignupService(Notifier notifier) { this.notifier = notifier; }        // constructor injection
    void signUp(String user) { notifier.send("welcome " + user); }
}

new SignupService(message -> System.out.println("fake: " + message)).signUp("asha");   // fake: welcome asha
```

**Inversion of Control:** instead of your code controlling object creation, a **container** (Spring) creates the objects and wires them together. DI is the technique; IoC is the principle.

| Injection style | Notes |
|---|---|
| **Constructor** (recommended) | dependencies are mandatory and `final`; the object is never half-built; easy to test with `new` |
| Setter | optional dependencies |
| Field (`@Autowired` on a field) | short but hides dependencies, cannot be `final`, harder to test: avoid |

This is the Dependency Inversion Principle in practice and the foundation of everything in the Spring notes.

---

## 14. Patterns in the JDK and Spring

| Pattern | JDK | Spring |
|---|---|---|
| Singleton | `Runtime.getRuntime()`, enums | every default bean |
| Factory | `List.of`, `Calendar.getInstance`, `NumberFormat.getInstance` | `BeanFactory`, `FactoryBean`, `@Bean` methods |
| Builder | `StringBuilder`, `HttpRequest.newBuilder`, `Stream.builder` | `UriComponentsBuilder`, `HttpSecurity` (fluent configuration), `MockMvcRequestBuilders` |
| Strategy | `Comparator`, `Collectors` | `PasswordEncoder`, `HandlerMapping`, `ViewResolver` |
| Observer | `PropertyChangeListener`, `Flow` | `ApplicationEvent` and `@EventListener` |
| Decorator | `java.io` wrappers, `Collections.unmodifiableX` | `HttpServletRequestWrapper`, `BeanPostProcessor` wrapping beans |
| Adapter | `Arrays.asList`, `InputStreamReader` | `HandlerAdapter`, `HttpMessageConverter` |
| Facade | `java.sql.DriverManager` hides drivers | `JdbcTemplate`, `RestClient` |
| Proxy | `java.lang.reflect.Proxy` | `@Transactional`, `@Cacheable`, AOP, Spring Data repositories |
| Template Method | `AbstractList`, `HttpServlet` | `JdbcTemplate`, `TransactionTemplate`, `RestTemplate` |
| Command | `Runnable`, `Callable` | `CommandLineRunner`, scheduled tasks |
| Chain of Responsibility | servlet `FilterChain` | Spring Security filter chain, `HandlerInterceptor` |
| Iterator | `Iterator`, enhanced `for` | `Page`/`Slice` iteration |
| Prototype | `Object.clone()` | `@Scope("prototype")` beans |
| Dependency Injection | (none) | the whole framework |

---

## 15. Anti-Patterns

Common bad solutions that look like solutions:

| Anti-pattern | Symptom | Better |
|---|---|---|
| **God class** | one class that knows and does everything (`Utils`, `Manager`, `Helper`) | split by responsibility |
| **Singleton everywhere** | hidden global state, untestable | dependency injection |
| **Anemic domain model** | entities are only getters/setters; all logic in services | put behaviour next to the data it guards |
| **Spaghetti code** | no structure, jumping control flow | layers, small methods |
| **Copy-paste programming** | the same bug fixed in five places | extract a method or a class |
| **Magic numbers / strings** | `if (status == 3)` | enums and constants |
| **Premature optimisation** | clever code with no measured need | measure first |
| **Golden hammer** | using one favourite tool (or pattern) for everything | choose for the problem |
| **Over-engineering** | an `AbstractFactoryProviderManager` for two cases | the simplest design that works |
| **Service locator** | classes look up their dependencies from a global registry | constructor injection |
| **Catch-and-ignore** | empty `catch` blocks | handle or rethrow, log with context |
| **Leaky abstraction** | callers must know the implementation | clear interfaces |

---

## 16. Common Mistakes

| Mistake | Why | Fix |
|---|---|---|
| Using a pattern because it exists | adds classes and indirection with no gain | start simple; refactor to a pattern when duplication or rigidity shows up |
| Singleton with mutable state | shared state, race conditions | stateless, or make it immutable/thread-safe, or let Spring manage it |
| Double-checked locking without `volatile` | another thread sees a half-built object | holder idiom or enum |
| Strategy as a big `switch` inside the class | the pattern's whole point lost | inject the strategy or use a `Map` of strategies |
| Observer without unsubscribing | memory leak, callbacks to dead objects | provide `unsubscribe`, use weak references or scoped listeners |
| Builder for a two-field class | noise | constructor or record |
| Decorator that changes the interface | callers cannot treat it like the original | implement exactly the same interface |
| Field injection (`@Autowired` on a field) | hidden dependencies, hard to test | constructor injection |
| Calling `this.method()` and expecting a proxy's behaviour | self-invocation skips the proxy | move to another bean |
| Facade that grows business rules | becomes a god class | keep it a thin coordinator |
| Inheritance for reuse | tight coupling, fragile base class | composition and interfaces |

---

## 17. Interview Questions

**1. What is a design pattern, and why use them?**
A reusable, named solution to a common design problem. They give a shared vocabulary and proven structure.

**2. Name the SOLID principles.**
Single responsibility, open/closed, Liskov substitution, interface segregation, dependency inversion.

**3. How do you write a thread-safe singleton in Java?**
Prefer an enum; otherwise the initialization-on-demand holder idiom, or double-checked locking with a `volatile` field. In Spring let the container manage it.

**4. How can a singleton be broken, and how does an enum prevent it?**
By reflection (calling the private constructor), serialization (deserializing creates a new object) and cloning. An enum cannot be instantiated reflectively and is deserialized to the same constant.

**5. Factory vs Abstract Factory vs Builder?**
A factory creates one product and hides its class; an abstract factory creates families of related products; a builder constructs one complex object step by step.

**6. When would you use Strategy, and how do lambdas change it?**
When behaviour varies and should be chosen at run time. A single-method strategy is a functional interface, so each variant is a lambda.

**7. Observer in Spring?**
`ApplicationEventPublisher.publishEvent(...)` and `@EventListener` methods; use `@TransactionalEventListener` to react after commit.

**8. Decorator vs Proxy vs Adapter vs Facade?**
Decorator adds behaviour in layers; proxy controls access to the same interface; adapter converts one interface to another; facade simplifies a subsystem behind one interface.

**9. How does Spring implement `@Transactional`?**
With a proxy (JDK dynamic or CGLIB) that opens and commits or rolls back a transaction around your method; hence self-invocation does not work.

**10. What is the Template Method pattern, and where is it in Spring?**
A base class fixes the algorithm's steps and lets subclasses (or callbacks) supply some of them. `JdbcTemplate`, `RestTemplate` and `TransactionTemplate` handle the boilerplate and call your code.

**11. What is Chain of Responsibility in web applications?**
Filters: each can handle, modify, pass on or reject a request. The servlet filter chain and Spring Security's filter chain.

**12. Inheritance vs composition?**
Inheritance couples a subclass to its parent's implementation; composition (holding another object and delegating) is more flexible and testable. Favour composition.

**13. What is dependency injection, and why prefer constructor injection?**
Providing an object's collaborators from outside. Constructor injection makes dependencies explicit and `final`, prevents half-built objects and makes testing easy.

**14. What is the difference between Dependency Injection and Dependency Inversion?**
Inversion is the design principle (depend on abstractions); injection is one technique to supply the concrete implementation.

**15. What is an anemic domain model?**
Entities with only data and getters/setters, with all rules in services; it loses encapsulation. Put rules with the data they protect.

---

## 18. Cheat Sheet

| Pattern | One-liner | Java hint |
|---|---|---|
| Singleton | one instance | `enum`; Spring beans are already singletons |
| Factory | hide which class is created | static factories (`List.of`) |
| Builder | readable construction of complex objects | fluent `build()`; Lombok `@Builder` |
| Strategy | interchangeable algorithms | lambda or injected interface |
| Observer | notify many listeners | `@EventListener`, `ApplicationEventPublisher` |
| Decorator | wrap to add behaviour | `java.io` stack |
| Adapter | convert an interface | `Arrays.asList` |
| Facade | one simple entry point | a `@Service` over several collaborators |
| Proxy | stand-in that controls access | `@Transactional`, `@Cacheable`, JDK `Proxy` |
| Template Method | fixed skeleton, variable steps | `JdbcTemplate` |
| Command | request as an object | `Runnable`, undo stacks |
| Chain of Responsibility | pass along handlers | servlet filters |
| State | behaviour depends on state | enum state machine |
| DI | receive collaborators | constructor injection |

---

## 19. Practice Tasks

1. **Singleton audit.** Write the same configuration holder three ways (enum, holder idiom, double-checked locking). Break the non-enum versions with reflection and with serialization, and show that the enum survives.
2. **Strategy and maps.** Replace a `switch` over `"ECONOMY" / "EXPRESS" / "OVERNIGHT"` shipping types with a `Map<String, ShippingStrategy>` of lambdas. Add a fourth type without editing existing code.
3. **Builder with validation.** Write an immutable `HttpRequest`-like class (method, url, headers, body, timeout) with a builder that rejects a body on `GET` and a negative timeout.
4. **Decorator stack.** Write `Text` with `Uppercase`, `Trim` and `Censor` decorators and apply them in different orders. Show that order matters.
5. **Dynamic proxy.** Write a `Proxy` that measures the time of every method of any interface and counts calls. Then explain, using your proxy, why a call from inside the same class would not be counted.
6. **Observer.** Publish a `UserRegistered` event in Spring and add two `@EventListener`s, one `@Async`. Make one throw and observe what happens to the other and to the publisher.
7. **Chain.** Build a request-validation chain (authenticated, authorised, rate-limited, valid body) where each link can stop the request with a reason. Write unit tests for each link alone.
8. **State.** Model a ticket with states `OPEN, IN_PROGRESS, RESOLVED, CLOSED` and a transition table. Write tests that every illegal transition is rejected.
9. **Refactor.** Take any `if / else if` ladder from your earlier projects (the Hackathon status checks, for instance) and replace it with the pattern that fits. Keep the tests green.

*End of notes.*
