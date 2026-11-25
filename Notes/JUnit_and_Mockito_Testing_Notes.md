# JUnit and Mockito: Detailed Testing Notes

How to write tests you can trust: the shape of a good test, **JUnit** (assertions, lifecycle, parameterized tests, nesting, temporary files), **AssertJ** (readable assertions), **Mockito** (stubbing, verifying, capturing, spying, static mocking, strict stubs), how to test time and failures, what **code coverage** really tells you, and the mistakes that make test suites slow, flaky or useless.

> **Everything here was run.** The examples are in `examples/junit-mockito` in this repository: **68 tests, all passing**, on JUnit Jupiter **6.0.3**, Mockito **5.23.0** and AssertJ (the versions managed by Spring Boot 4.1.1; the project uses Boot only for its tested version list and has no Spring code). The annotations and packages (`org.junit.jupiter.api...`) are the same ones used since JUnit 5, so the code also works on JUnit 5. The Spring-specific side (`@WebMvcTest`, `@SpringBootTest`, `@MockitoBean`) is in the Spring configuration and testing notes.

---

## Table of Contents

1. [Why Test, and What a Good Test Looks Like](#1-why-test-and-what-a-good-test-looks-like)
2. [Setup and Running Tests](#2-setup-and-running-tests)
3. [The Code Under Test](#3-the-code-under-test)
4. [Your First Tests and Assertions](#4-your-first-tests-and-assertions)
5. [Lifecycle: What Runs When](#5-lifecycle-what-runs-when)
6. [Parameterized Tests](#6-parameterized-tests)
7. [More JUnit: Nested, Tags, Assumptions, Temp Files, Dynamic Tests](#7-more-junit-nested-tags-assumptions-temp-files-dynamic-tests)
8. [Test Doubles: Dummy, Stub, Fake, Mock, Spy](#8-test-doubles-dummy-stub-fake-mock-spy)
9. [Mockito: Creating Mocks and Stubbing](#9-mockito-creating-mocks-and-stubbing)
10. [Mockito: Verifying Behaviour](#10-mockito-verifying-behaviour)
11. [Argument Matchers and Captors](#11-argument-matchers-and-captors)
12. [Spies, Void Methods, Answers and Static Mocking](#12-spies-void-methods-answers-and-static-mocking)
13. [Strict Stubs: Mockito Catches Your Mistakes](#13-strict-stubs-mockito-catches-your-mistakes)
14. [Testing Time, Randomness, Files and Failures](#14-testing-time-randomness-files-and-failures)
15. [Designing Good Tests](#15-designing-good-tests)
16. [Code Coverage and Mutation Testing](#16-code-coverage-and-mutation-testing)
17. [Common Mistakes](#17-common-mistakes)
18. [Output Prediction Puzzles](#18-output-prediction-puzzles)
19. [Interview Questions](#19-interview-questions)
20. [Cheat Sheet](#20-cheat-sheet)
21. [Practice Tasks](#21-practice-tasks)

---

## 1. Why Test, and What a Good Test Looks Like

Tests are **executable specifications**. They let you change code (refactor, upgrade a library) and learn within seconds whether you broke something. The cheapest bug is the one a test catches before you commit.

### The test pyramid

| Level | Count | Speed | Example |
|---|---|---|---|
| **Unit** | many (hundreds to thousands) | milliseconds | `PriceCalculator.apply(...)` with plain inputs |
| **Integration / slice** | some | seconds | a repository against a real database; a controller with `MockMvc` |
| **End to end** | few | slow | a browser clicking through checkout |

Most of your tests should be unit tests: fast, precise about what failed, and cheap to write. This note is about those.

### The F.I.R.S.T. properties

| Letter | Property | Meaning |
|---|---|---|
| **F** | Fast | the whole suite runs in seconds, so you run it all the time |
| **I** | Independent | no test depends on another or on their order |
| **R** | Repeatable | the same result on every machine and every day (no real clock, network or randomness) |
| **S** | Self-validating | pass or fail by itself, no reading logs |
| **T** | Timely | written with the code (or before it), not months later |

### Arrange, Act, Assert (AAA)

```java
@Test
void aTenPercentCodeTakesTenPercentOff() {
    // Arrange: the situation
    int total = 100_000;
    // Act: the one thing being tested
    int result = PriceCalculator.apply(total, "SAVE10");
    // Assert: the expected outcome
    assertEquals(90_000, result);
}
```

One test, one behaviour. A name that reads as a sentence (`aDeclinedPaymentGivesTheStockBack`) documents the rule and tells you what broke when it fails.

**TDD** (test-driven development): write a failing test first (red), make it pass with the simplest code (green), then clean up (refactor). Even if you do not do it strictly, writing the test first for a bug fix proves the fix works.

---

## 2. Setup and Running Tests

```xml
<dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId><scope>test</scope></dependency>
<dependency><groupId>org.mockito</groupId><artifactId>mockito-core</artifactId><scope>test</scope></dependency>
<dependency><groupId>org.mockito</groupId><artifactId>mockito-junit-jupiter</artifactId><scope>test</scope></dependency>
<dependency><groupId>org.assertj</groupId><artifactId>assertj-core</artifactId><scope>test</scope></dependency>
```

In a Spring Boot project, `spring-boot-starter-test` (Boot 3) or the per-technology test starters (Boot 4) already bring all four. Versions come from the parent; do not hard-code them.

| Fact | Detail |
|---|---|
| JUnit is three parts | **Platform** (launches tests), **Jupiter** (the programming model and annotations), **Vintage** (runs old JUnit 4 tests) |
| Where tests live | `src/test/java`, same package as the class under test (so package-private members are reachable) |
| Naming | Maven Surefire runs `*Test`, `Test*`, `*Tests`, `*TestCase` classes |
| Run all | `mvn test` |
| Run one class / method | `mvn -Dtest=OrderServiceTest test`, `mvn -Dtest=OrderServiceTest#thingsHappenInTheRightOrder test` |
| Run by tag | `mvn -Dgroups=slow test` (and `-DexcludedGroups=slow`) |
| Reports | `target/surefire-reports` |
| Verify | `mvn verify` also runs integration tests and plugins such as JaCoCo |

In IntelliJ or VS Code, the green arrow next to a test or class runs it; always **also** run `mvn test` before you commit, because the IDE's classpath can hide mistakes.

---

## 3. The Code Under Test

The examples test a small shop (all in `examples/junit-mockito/src/main/java/shop`):

- `Cart`: items with a SKU, a price in **paise** (integers, so no floating-point money) and a quantity; `total()`, `remove`, `totalWithDiscount(code)`.
- `PriceCalculator.apply(total, code)`: `SAVE10` (10% off), `SAVE20` (20% off, only for totals of 100000 paise or more), `FLAT500`, unknown codes are an error.
- `OrderService.placeOrder(...)`: reserve stock for each item (undo on failure), charge the payment (give the stock back if declined), send the email, return a `Receipt` (order id, amount, payment reference, time).
- `PaymentGateway`, `InventoryClient`, `EmailService`: the outside world, **interfaces** so they can be replaced in tests.
- `Clock` is **injected** into `OrderService` (see section 14).

`OrderService` is the kind of class where mocks earn their keep: it coordinates collaborators that you cannot run for real in a unit test (a payment provider, an email server).

---

## 4. Your First Tests and Assertions

```java
class JUnitBasicsTest {
    Cart cart;

    @BeforeEach
    void beforeEach() {
        cart = new Cart();
        cart.add("PEN", 1000, 2);
        cart.add("BOOK", 25000, 1);          // total 27000
    }

    @Test
    void totalAddsUpTheLines() {
        assertEquals(27000, cart.total());
    }
}
```

A test method is `@Test`, not `private`, returns `void`, and takes no parameters (unless a parameter resolver supplies them). JUnit 5+ methods and classes do **not** need to be `public`.

### JUnit assertions

```java
assertEquals(expected, actual);                 // expected FIRST: the failure message reads "expected: <5> but was: <6>"
assertTrue(cond);  assertFalse(cond);
assertNull(x);     assertNotNull(x);
assertSame(a, b);  assertNotSame(a, b);        // identity (==), not equals
assertArrayEquals(a, b);  assertIterableEquals(a, b);
assertEquals(0.3, 0.1 + 0.2, 1e-9);            // doubles need a tolerance (delta)

IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> cart.add("", 100, 1));
assertEquals("sku is required", ex.getMessage());

assertAll("cart",                               // runs ALL, reports EVERY failure (not just the first)
    () -> assertEquals(27000, cart.total()),
    () -> assertFalse(cart.isEmpty()),
    () -> assertEquals("PEN", cart.items().get(0).sku()));

assertTimeoutPreemptively(Duration.ofSeconds(2), () -> cart.total());   // fails (and interrupts) if too slow
assertDoesNotThrow(() -> cart.total());
```

Add a message as the last argument (or a `Supplier<String>`) when the failure alone will not explain itself.

### AssertJ: assertions that read like English

```java
import static org.assertj.core.api.Assertions.*;

assertThat(cart.items()).hasSize(2).extracting(Cart.Item::sku).containsExactly("PEN", "BOOK");
assertThat(cart.total()).isEqualTo(27000).isBetween(1, 100_000);
assertThat("Hello").startsWith("He").endsWithIgnoringCase("LO").hasSize(5);
assertThat(List.of(1, 2, 3)).contains(2).doesNotContain(9).allMatch(n -> n > 0);
assertThat(Optional.of("x")).isPresent().contains("x");
assertThat(map).containsEntry("a", 1).containsKeys("a", "b");

assertThatThrownBy(() -> PriceCalculator.apply(100, "NOPE"))
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessageContaining("unknown code");
```

Why many teams prefer it: auto-complete shows what you can check, one fluent chain replaces several JUnit calls, and failure messages are detailed (`Expecting list: [...] to contain exactly [...] but could not find: [...]`).

Hamcrest (`assertThat(x, is(5))`) is the older matcher library you will still meet inside Spring's `MockMvc` (`jsonPath(...).value(is(...))`).

---

## 5. Lifecycle: What Runs When

| Annotation | Runs | Must be |
|---|---|---|
| `@BeforeAll` | once, before all tests of the class | `static` (unless `PER_CLASS`) |
| `@BeforeEach` | before **every** test | instance method |
| `@AfterEach` | after every test (even a failing one) | instance method |
| `@AfterAll` | once, after all tests | `static` (unless `PER_CLASS`) |

Verified order for two tests (`LifecycleTest`):

```
beforeAll
  beforeEach, test1, afterEach
  beforeEach, test2, afterEach
afterAll
```

**JUnit creates a new instance of the test class for every test method.** So a field changed by one test is fresh again in the next: tests cannot leak state through fields (the verified `puzzle5` below). This is the opposite of old JUnit 3 habits and the main reason tests stay independent.

```java
@TestInstance(TestInstance.Lifecycle.PER_CLASS)   // one instance for the whole class: @BeforeAll/@AfterAll may be non-static,
class SharedState { ... }                          // but now fields DO leak between tests: use it deliberately
```

Do **not** rely on test method order (JUnit's default is deterministic but deliberately not obvious). If you must order steps, `@TestMethodOrder(MethodOrderer.OrderAnnotation.class)` plus `@Order(1)`; that is a smell outside integration and demonstration tests.

Use `@BeforeEach` to build the **fresh** object under test. Heavy setup (a database, a server) goes in `@BeforeAll` or in a shared extension.

---

## 6. Parameterized Tests

One test body, many inputs. Needs the `junit-jupiter-params` module (included in `junit-jupiter`).

```java
@ParameterizedTest(name = "{0} with total {1} gives {2}")
@CsvSource({
    "SAVE10,  100000, 90000",
    "save10,  100000, 90000",         // case-insensitive
    "SAVE20,  100000, 80000",
    "FLAT500, 300,    0",             // never below zero
    "FLAT500, 1000,   500"
})
void discountCodes(String code, int total, int expected) {
    assertEquals(expected, PriceCalculator.apply(total, code));
}
```

| Source | Supplies |
|---|---|
| `@ValueSource(strings = {...}, ints = {...})` | one argument per run |
| `@CsvSource({"a,1", "b,2"})` | several arguments per run, converted to the parameter types |
| `@CsvFileSource(resources = "/cases.csv")` | the same, from a file |
| `@EnumSource(Code.class)` (`names`, `mode`) | enum constants |
| `@MethodSource("cases")` | a static method returning `Stream<Arguments>`, for objects and complex cases |
| `@NullSource`, `@EmptySource`, `@NullAndEmptySource` | `null` and/or empty strings, lists, arrays |
| `@ArgumentsSource` | your own provider |

```java
static Stream<Arguments> slugCases() {
    return Stream.of(
        Arguments.of("Hello, World!", "hello-world"),
        Arguments.of("  Spring  Boot 4  ", "spring-boot-4"),
        Arguments.of("Crème brûlée", "creme-brulee"));
}

@ParameterizedTest
@MethodSource("slugCases")
void slugs(String input, String expected) { assertEquals(expected, TextUtils.slugify(input)); }

@ParameterizedTest
@NullAndEmptySource
@ValueSource(strings = {"  ", "\t"})                    // null, "", and two blank strings
void blankCodesLeaveTheTotalUnchanged(String code) { assertEquals(500, PriceCalculator.apply(500, code)); }
```

Why: edge cases (empty, `null`, boundaries, big values) become a table you can read and extend, and each row is reported separately (`name = "{0} with total {1} gives {2}"` makes the report readable). Aim for **boundary values**: just below, at and just above each limit (`SAVE20` at 99999 and at 100000).

---

## 7. More JUnit: Nested, Tags, Assumptions, Temp Files, Dynamic Tests

### `@Nested`: group tests by situation

```java
@Nested
@DisplayName("when the cart is empty")
class EmptyCart {
    Cart empty;
    @BeforeEach void emptyCart() { empty = new Cart(); }       // runs after the outer @BeforeEach

    @Test void totalIsZero() { assertEquals(0, empty.total()); }
    @Test void removingAnUnknownItemReturnsFalse() { assertFalse(empty.remove("NOPE")); }
}
```

Reads like a specification: "JUnit basics, when the cart is empty, total is zero". Outer `@BeforeEach` methods run first. (Nested classes must be non-static inner classes.)

### The rest of the toolbox

```java
@DisplayName("a readable name shown in reports")          // on classes and methods
@Disabled("waiting for ticket 123")                       // skip, with a reason (do not leave these forever)
@Tag("slow")                                              // select with -Dgroups=slow or -DexcludedGroups=slow
@RepeatedTest(3)                                          // run the same test three times (flakiness hunting)
@Timeout(2)                                               // fail if it takes more than 2 seconds

assumeTrue(System.getenv("RUN_SLOW_TESTS") != null, "set RUN_SLOW_TESTS to run this");   // SKIPS the test (not a failure)

@Test
void tempDirGivesAFreshFolderThatIsDeletedAfterwards(@TempDir Path dir) throws IOException {
    Path file = dir.resolve("receipt.txt");
    Files.writeString(file, "paid 27000");
    assertEquals("paid 27000", Files.readString(file));
}

@TestFactory                                              // tests generated at run time from data
Stream<DynamicTest> codesExist() {
    return Stream.of("SAVE10", "SAVE20", "FLAT500")
        .map(code -> DynamicTest.dynamicTest("code " + code, () -> assertThat(PriceCalculator.apply(150_000, code)).isPositive()));
}
```

`@TempDir` is the right way to test file code: no hard-coded paths, nothing left on disk, safe to run in parallel.

### Extensions

`@ExtendWith(SomeExtension.class)` adds behaviour around tests: `MockitoExtension` (creates mocks), `SpringExtension` (the Spring context), your own (timing, database cleanup, a fake server). It replaces JUnit 4's `@RunWith` and `@Rule`.

---

## 8. Test Doubles: Dummy, Stub, Fake, Mock, Spy

A **test double** replaces a real collaborator. The words are often mixed up:

| Double | What it does | Example |
|---|---|---|
| **Dummy** | fills a parameter and is never used | `new OrderService(null, null, null, clock)` for a test that does not reach them |
| **Stub** | returns canned answers | `when(inventory.reserve("PEN", 2)).thenReturn(true)` |
| **Fake** | a **working** lightweight implementation | an in-memory repository (a `HashMap`), H2 instead of PostgreSQL |
| **Mock** | records calls so you can **verify** them | `verify(email).send(...)` |
| **Spy** | wraps a **real** object, recording calls and optionally overriding some | `spy(new Cart())` |

Mockito mocks do both jobs: you stub what they return (stub role) and verify what was called (mock role).

**When to use which:**

- A collaborator that is **slow, non-deterministic or has side effects** (payment, email, clock, network): a mock or fake.
- A collaborator that is **cheap and deterministic** (a `Cart`, a value object, a pure function): use the **real thing**. Do not mock it.
- State-based assertions (check the returned value) are less brittle than interaction-based ones (check that method X was called): prefer them and verify interactions only for **side effects that matter** (a payment was taken, an email was sent).
- A **fake** often gives better tests than a pile of stubs, because it behaves consistently.

---

## 9. Mockito: Creating Mocks and Stubbing

```java
@ExtendWith(MockitoExtension.class)               // creates @Mock fields, injects @InjectMocks, checks unused stubs
class OrderServiceTest {

    @Mock PaymentGateway payments;
    @Mock InventoryClient inventory;
    @Mock EmailService email;

    OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(payments, inventory, email, FIXED_CLOCK);    // plain constructor injection
        ...
    }
}
```

Without the extension: `PaymentGateway payments = Mockito.mock(PaymentGateway.class);`. `@InjectMocks` can build the object for you, but a plain constructor call is clearer, fails to compile when you add a dependency, and avoids Mockito's silent field/setter-injection fallbacks. Prefer it.

### Stubbing

```java
when(inventory.reserve("PEN", 2)).thenReturn(true);                   // return a value
when(payments.charge(anyString(), anyInt())).thenThrow(new IllegalStateException("gateway timeout"));
when(stock.reserve("X", 1)).thenReturn(true, true, false);            // consecutive: true, true, then false forever
when(gateway.charge(anyString(), anyInt())).thenAnswer(call -> {      // compute from the arguments
    int amount = call.getArgument(1);
    return new PaymentResult(amount < 50_000, "REF-" + amount);
});

// BDD style (same thing, reads as given/when/then)
given(payments.charge(anyString(), anyInt())).willReturn(new PaymentResult(true, "PAY-9"));
```

### What an unstubbed mock returns (verified)

| Return type | Default |
|---|---|
| object (`PaymentResult`, `String`) | `null` |
| `boolean` | `false` |
| numbers | `0` |
| `List`, `Set`, `Map`, `Collection`, `Optional`, `Stream` | **empty** collection / `Optional.empty()` / empty stream |

An unstubbed `charge(...)` returning `null` then produces a `NullPointerException` in your code under test: stub what the test needs.

### Mock a method and the mock is only an outline

Calling a mock's methods does **nothing real**: `cart.add(...)` on a `mock(Cart.class)` adds nothing, and `total()` returns `0` unless stubbed (verified in the puzzles). If you find yourself stubbing the class you are testing, you are testing the mock.

---

## 10. Mockito: Verifying Behaviour

```java
verify(payments).charge("cust-1", 27000);                    // exactly once (the default)
verify(payments, times(2)).charge(anyString(), eq(27000));
verify(inventory, never()).release(anyString(), anyInt());
verify(email, atLeastOnce()).send(anyString(), anyString(), anyString());
verifyNoInteractions(payments, email);                       // the mock was not touched at all
verifyNoMoreInteractions(payments, email);                   // everything that happened was verified

// order
InOrder order = inOrder(inventory, payments, email);
order.verify(inventory).reserve("PEN", 2);
order.verify(inventory).reserve("BOOK", 1);
order.verify(payments).charge("cust-1", 27000);
order.verify(email).send(eq("a@b.c"), anyString(), anyString());

// BDD style
then(payments).should().charge("c1", 27000);
```

A typical test of a failure path (all verified in `OrderServiceTest`):

```java
@Test
void aDeclinedPaymentGivesTheStockBack() {
    when(inventory.reserve(anyString(), anyInt())).thenReturn(true);
    when(payments.charge(anyString(), anyInt())).thenReturn(new PaymentResult(false, null));

    assertThatThrownBy(() -> service.placeOrder("cust-1", "asha@example.com", cart, null))
        .isInstanceOf(PaymentDeclinedException.class);

    verify(inventory).release("PEN", 2);
    verify(inventory).release("BOOK", 1);
    verifyNoInteractions(email);                   // no "thank you" email for a failed payment
}
```

**Do not over-verify.** `verifyNoMoreInteractions` on everything and exact ordering of internal calls make tests break whenever you refactor harmlessly. Verify the interactions that are the **observable contract** (money taken, stock returned, email sent or not), and assert returned values for the rest.

Mockito's message when a count is wrong (verified): `verify(gateway).charge("c", 100)` after two calls fails with `TooManyActualInvocations`.

---

## 11. Argument Matchers and Captors

### Matchers

```java
when(inventory.reserve(anyString(), anyInt())).thenReturn(true);
verify(email).send(eq("asha@example.com"), anyString(), contains("24300"));
```

| Matcher | Matches |
|---|---|
| `any()`, `any(Cart.class)`, `anyString()`, `anyInt()`, `anyList()` | any value of the type (`any(X.class)` rejects `null`; `any()` accepts it) |
| `eq(v)` | equal to `v` |
| `isNull()`, `notNull()` | |
| `contains("x")`, `startsWith`, `endsWith`, `matches(regex)` | strings |
| `argThat(x -> x > 5)` | your own predicate |

> **The rule: if one argument uses a matcher, ALL must.** `when(stock.reserve(anyString(), 2))` throws `InvalidUseOfMatchersException`; write `when(stock.reserve(anyString(), eq(2)))` (verified).

### `ArgumentCaptor`: grab what was passed, then assert on it

```java
@Captor ArgumentCaptor<String> bodyCaptor;

@Test
void theEmailBodyIsCapturedAndInspected() {
    ...
    service.placeOrder("cust-1", "asha@example.com", cart, "SAVE10");

    verify(email).send(eq("asha@example.com"), anyString(), bodyCaptor.capture());
    assertThat(bodyCaptor.getValue()).contains("24300");        // 27000 minus 10%
}
```

Use a captor when the argument is a complex object built inside the code under test (an event, an entity, an HTTP request) and you want to check several of its fields.

---

## 12. Spies, Void Methods, Answers and Static Mocking

### Spy: a real object with some behaviour recorded or replaced

```java
Cart spied = spy(new Cart());
spied.add("A", 100, 3);
assertThat(spied.total()).isEqualTo(300);              // real behaviour
verify(spied).add("A", 100, 3);                        // recorded
doReturn(999).when(spied).total();                     // replace ONE method
assertThat(spied.total()).isEqualTo(999);
```

**Stub spies with `doReturn(...).when(spy)...`, not `when(spy.method())`.** The `when(spy.get(0))` form calls the **real** method while stubbing: on an empty list spy it throws `IndexOutOfBoundsException` (verified). Spies are a smell: they usually mean a class does too much. Prefer a real object or a fake.

### Void methods: `doThrow`, `doNothing`

```java
EmailService mail = mock(EmailService.class);
doThrow(new IllegalStateException("smtp down")).when(mail).send(eq("bad@x"), anyString(), anyString());
doNothing().when(mail).send(eq("good@x"), anyString(), anyString());     // the default for a mock, shown for clarity
```

`when(mail.send(...))` cannot be written for a `void` method (nothing to wrap), hence the `do...()` family.

### Static methods: `mockStatic` (Mockito 5)

```java
UUID fixed = UUID.fromString("00000000-0000-0000-0000-000000000042");
try (MockedStatic<UUID> uuid = mockStatic(UUID.class)) {      // active only inside this block and on this thread
    uuid.when(UUID::randomUUID).thenReturn(fixed);
    Receipt receipt = service.placeOrder("c1", "a@b.c", cart, null);
    assertThat(receipt.orderId()).isEqualTo("00000000-0000-0000-0000-000000000042");
}
assertThat(UUID.randomUUID()).isNotEqualTo(fixed);            // the real method is back
```

Always use try-with-resources: a static mock that is not closed leaks into other tests. Static mocking is a last resort for code you cannot change (a legacy call to `LocalDate.now()`); for your own code **inject** the dependency (`Clock`, an `IdGenerator`) instead.

### Final classes and records

Mockito 5 uses the **inline mock maker by default**, so `final` classes, records and `static` mocking work with no extra setup (verified: `mock(PaymentResult.class)` on a record). With Mockito 3 and 4 you needed `mockito-inline`.

---

## 13. Strict Stubs: Mockito Catches Your Mistakes

With `@ExtendWith(MockitoExtension.class)`, Mockito runs in **strict stubs** mode:

1. **Unnecessary stubbing fails the test.** If you stub `when(stock.reserve("X", 1)).thenReturn(true)` and the code never calls it, `finishMocking` throws `UnnecessaryStubbingException`: the test contains setup that does nothing (dead setup often hides a wrong assumption).
2. **Argument mismatches are reported.** A stub for `reserve("PENN", 2)` (typo) when the code calls `reserve("PEN", 2)` throws `PotentialStubbingProblem` with a message showing both: instead of returning `false` silently and sending you on a debugging hunt. (Mockito reports this when the call comes from the **code under test**, a different source file than the stub.)
3. **`lenient()` opts one stub out** when it is genuinely shared setup that not every test uses:

```java
lenient().when(stock.reserve("X", 1)).thenReturn(true);    // allowed to go unused
```

All three were verified with a hand-made `MockitoSession` in `StrictStubsTest`. Leave strictness on; it is one of the best features of modern Mockito. A failure "Unnecessary stubbings detected" usually means a stub belongs in a different test, or the test no longer exercises what you think.

---

## 14. Testing Time, Randomness, Files and Failures

### Time: inject a `Clock`

```java
static final Clock FIXED = Clock.fixed(Instant.parse("2026-11-25T10:00:00Z"), ZoneOffset.UTC);

service = new OrderService(payments, inventory, email, FIXED);
...
assertThat(receipt.placedAt()).isEqualTo(Instant.parse("2026-11-25T10:00:00Z"));
```

`Instant.now()` or `LocalDate.now()` inside the class makes the result depend on when the test runs (flaky around midnight, month ends and daylight saving). Production code uses `Clock.systemUTC()`; the test passes a fixed one. Never `Thread.sleep` to wait for time to pass in a unit test.

### Randomness and IDs

Inject the source (`Supplier<UUID>`, `Random` with a seed, or an `IdGenerator` interface) rather than calling `UUID.randomUUID()` inside business code. Where you cannot, `mockStatic` (section 12) or assert on the **shape** (`matches("[0-9a-f-]{36}")`).

### Files

`@TempDir` (section 7). Never write into the project folder or a fixed path like `C:\temp`.

### Failures and exceptions

```java
assertThatThrownBy(() -> service.placeOrder("cust-1", "a@b.c", new Cart(), null))
    .isInstanceOf(IllegalArgumentException.class);
verifyNoInteractions(payments, inventory, email);           // and nothing was touched before the check
```

Test the **sad paths** as carefully as the happy path: invalid input, a collaborator that throws, a timeout, a partial failure that needs undoing (the stock release above). Most production bugs live there.

### Concurrency and asynchronous code

Avoid `sleep`. Wait for a condition with **Awaitility** (`await().atMost(2, SECONDS).until(() -> queue.size() == 3)`), inject an `Executor` you can run synchronously in tests, or use `CompletableFuture.get(timeout)`. Test thread-safe classes with many threads and assert on invariants; passing once does not prove correctness.

### Databases and other infrastructure

Unit tests do not touch them. For repositories use `@DataJpaTest` with **Testcontainers** (a real PostgreSQL in a throwaway container) so SQL dialect and constraint behaviour match production. In-memory H2 is quick but can hide differences.

---

## 15. Designing Good Tests

| Principle | Why |
|---|---|
| **Test behaviour, not implementation** | a refactor that keeps behaviour should not break tests; avoid asserting private details and exact internal call sequences |
| **One reason to fail** | a test with ten assertions on ten rules does not say what broke; split it or use `assertAll` for one rule |
| **No logic in tests** | an `if`, a loop or a copy of the production formula in a test can hide the same bug in both; use literal expected values (`90_000`, not `total - total / 10`) |
| **Arrange only what matters** | irrelevant detail distracts; use small builders or helper methods (`aCartWith("PEN", 1000, 2)`) |
| **Independent and deterministic** | fresh objects per test, no shared mutable statics, no real clock or network |
| **Readable names** | `aDeclinedPaymentGivesTheStockBack` beats `test3` |
| **Fast** | slow suites are not run; mark slow ones `@Tag("slow")` |
| **Test the boundaries** | 0, 1, many, null, empty, just below / at / just above a limit, negative, maximum |
| **A failing test should explain itself** | good names, `assertAll`, AssertJ messages, a `because`/`as` description |
| **Fix flaky tests, do not retry them** | a flaky test is a bug in the test or the code, and it teaches the team to ignore red |
| **Treat test code as production code** | review it, refactor it, keep it tidy |

### What to test where

| Layer | Test with |
|---|---|
| Pure logic (calculations, validation, parsing) | plain unit tests, parameterized |
| Services coordinating collaborators | unit tests with mocks or fakes (`OrderService`) |
| Controllers | `@WebMvcTest` + `MockMvc` (Spring notes) |
| Repositories and queries | `@DataJpaTest` + a real database container |
| The whole flow | a few `@SpringBootTest` or end-to-end tests |

---

## 16. Code Coverage and Mutation Testing

**Coverage** measures which code the tests executed. **JaCoCo** is the standard Maven tool (`mvn verify`, report in `target/site/jacoco/index.html`).

Results for the example project (verified):

| Measure | Result |
|---|---|
| Lines | **54 of 54 covered (100%)** |
| Branches | 27 of 32 covered (84%) |

100% of lines is not 100% of confidence. The missed branches were real gaps:

- `PriceCalculator.apply` rejects a **negative total**: no test.
- `Cart.Item` has validation for negative prices and quantities that the tests never trigger.
- `TextUtils.slugify(null)` is not tested.

Use coverage to find **untested code**, not as a target to hit. A team forced to 90% writes assertion-free tests that execute code without checking it.

**Mutation testing** (PIT, `pitest-maven`) checks the tests themselves: it changes your code (flips a `>` to `>=`, removes a call) and runs the tests; if they still pass, the mutant "survived" and your tests do not really check that line. It is slow, so run it on important modules.

---

## 17. Common Mistakes

| Mistake | Why | Fix |
|---|---|---|
| Tests that depend on order or share state | break randomly, in CI only | fresh objects in `@BeforeEach`, no mutable statics |
| `assertEquals(actual, expected)` (swapped) | the failure message lies | expected first |
| `assertTrue(a.equals(b))` | message says only "expected true" | `assertEquals` / `assertThat(a).isEqualTo(b)` |
| Comparing doubles with `assertEquals(a, b)` | binary rounding | pass a delta, or use integers / `BigDecimal` |
| Mocking everything, including value objects | tests just restate the implementation | use real simple objects |
| Mixing matchers and raw values | `InvalidUseOfMatchersException` | all matchers (`eq(...)`) |
| `when(spy.method())` | calls the real method | `doReturn(x).when(spy).method()` |
| Stubbing the class under test | testing the mock | stub only collaborators |
| Unused stubs ("just in case") | strict stubs fail; setup is misleading | stub only what the test uses; `lenient()` for shared setup |
| Asserting nothing (a test that only runs code) | passes always | assert an outcome |
| Catching an exception in the test to assert (`try/catch` + `fail()`) | verbose, easy to get wrong | `assertThrows` / `assertThatThrownBy` |
| `Thread.sleep` to wait | slow and flaky | Awaitility, injected executors |
| Real clock, real random, real network in unit tests | flaky | inject `Clock`/`Random`; mock the network |
| Giant `@BeforeEach` for ten tests | hides what each test needs | small per-test setup, helper methods |
| Verifying every interaction | brittle against refactors | verify only meaningful side effects |
| Writing tests after the fact to hit a coverage number | no assertions | write tests to catch bugs; read the missed branches |
| Ignoring a failing or flaky test | red becomes normal | fix it or delete it |
| Forgetting `@ExtendWith(MockitoExtension.class)` | `@Mock` fields are `null`, so `NullPointerException` | add the extension |
| Mocks leaking from `mockStatic` | not closed | try-with-resources |
| Mixing JUnit 4 (`org.junit.Test`) and 5/6 (`org.junit.jupiter.api.Test`) imports | tests silently not run or fail to compile | use the `jupiter` package everywhere |

---

## 18. Output Prediction Puzzles

All answers are asserted by tests in `PuzzlesTest`.

**Puzzle 1: defaults**

```java
List<String> list = mock(List.class);
when(list.get(0)).thenReturn("a");
System.out.println(list.get(0));
System.out.println(list.get(1));
System.out.println(list.size());
```

**Puzzle 2: mixed matchers**

```java
InventoryClient stock = mock(InventoryClient.class);
when(stock.reserve(anyString(), 2)).thenReturn(true);
```

**Puzzle 3: stubbing a spy**

```java
List<String> spied = spy(new ArrayList<String>());
when(spied.get(0)).thenReturn("x");
```

**Puzzle 4: verify counts**

```java
gateway.charge("c", 100);
gateway.charge("c", 100);
verify(gateway).charge("c", 100);
```

**Puzzle 5: instance fields**

```java
int counter;
@Test void first()  { counter++; assertEquals(1, counter); }
@Test void second() { counter++; assertEquals(1, counter); }
```

**Puzzle 6: argument order**

```java
assertEquals(5, 3 + 3);
```
What is the failure message?

**Puzzle 7: mocking a record**

```java
PaymentResult result = mock(PaymentResult.class);   // PaymentResult is a final record
System.out.println(result.approved() + " " + result.reference());
```

**Puzzle 8: a mocked class**

```java
Cart cart = mock(Cart.class);
when(cart.total()).thenReturn(5);
cart.add("A", 100, 3);
System.out.println(cart.total() + " " + cart.items().size() + " " + cart.isEmpty());
```

**Answers**

1. **`a`**, **`null`**, **`0`**. Stubbed calls return the stubbed value; others return defaults (`null` for an object, `0` for an `int`).
2. **`InvalidUseOfMatchersException`.** If one argument is a matcher, all must be: use `eq(2)`.
3. **`IndexOutOfBoundsException`.** `when(spied.get(0))` runs the real `get(0)` on an empty list while stubbing. Use `doReturn("x").when(spied).get(0)`.
4. **`TooManyActualInvocations`.** `verify` means exactly once; use `times(2)`.
5. **Both pass.** JUnit creates a new test-class instance per test, so `counter` is `0` at the start of each.
6. **`expected: <5> but was: <6>`.** The first argument is the expected value; swapping them gives a misleading message.
7. **`false null`.** Mockito 5 mocks final classes and records; unstubbed methods return defaults.
8. **`5 0 false`.** `total()` is stubbed; `add` on a mock does nothing, so `items()` is an empty list (the default for a `List`) and `isEmpty()` is `false` (the `boolean` default), even though the real `Cart` would say otherwise.

---

## 19. Interview Questions

**1. What makes a good unit test?**
Fast, independent, repeatable, self-validating, readable; tests one behaviour; fails for one reason and says why.

**2. What is the difference between a unit test and an integration test?**
A unit test checks one class in isolation with its collaborators replaced; an integration test runs several real parts together (a repository with a database, a controller with the Spring context).

**3. Explain the JUnit lifecycle annotations.**
`@BeforeAll` once before, `@BeforeEach` before every test, `@AfterEach` after every test, `@AfterAll` once after. A new test instance is created per test method by default.

**4. `@Test`, `@ParameterizedTest`, `@RepeatedTest`, `@TestFactory`?**
A single test; one test over several inputs; the same test several times; tests generated dynamically at run time.

**5. What is the difference between JUnit assertions and AssertJ?**
JUnit's are static methods with `(expected, actual)`; AssertJ's are fluent (`assertThat(x).isEqualTo(...).isBetween(...)`), with richer checks and failure messages.

**6. Mock vs stub vs spy vs fake?**
A stub returns canned answers; a mock also records calls for verification; a spy wraps a real object; a fake is a simple working implementation (an in-memory repository).

**7. How do you test a method that throws an exception?**
`assertThrows` (JUnit) or `assertThatThrownBy` (AssertJ), then check the type and message.

**8. What does `@InjectMocks` do, and why might you avoid it?**
It builds the object under test and injects the mocks (constructor, then setters, then fields). Plain constructor calls are clearer and fail to compile when a dependency is added.

**9. How do you verify the order of calls, and how do you capture an argument?**
`InOrder` (`inOrder(a, b)` then `order.verify(...)`); `ArgumentCaptor` with `verify(mock).method(captor.capture())` then `captor.getValue()`.

**10. Why can't you mix `any()` with a literal in one call?**
Mockito records matchers on a stack and cannot tell which argument each belongs to; if any argument is a matcher, all must be (`eq(...)` for the literals).

**11. `when(...).thenReturn` vs `doReturn(...).when(...)`?**
`doReturn` does not call the real method while stubbing, so it is the correct form for spies and for methods that throw or have side effects; it is also needed for `void` methods (`doThrow`, `doNothing`).

**12. What are strict stubs?**
Mockito (with `MockitoExtension`) fails tests that contain unused stubs or stubs whose arguments do not match the real call, catching dead setup and typos. `lenient()` opts out.

**13. How would you test code that uses the current time?**
Inject a `java.time.Clock` and use `Clock.fixed(...)` in tests.

**14. What does 100% code coverage mean?**
Every line (or branch) was executed, not that behaviour was checked: a test with no assertions gives coverage too. Use it to find untested code; use mutation testing to check test quality.

**15. How do you deal with a flaky test?**
Find the cause (shared state, time, randomness, ordering, concurrency, an external service), make it deterministic, and never just retry or ignore it.

**16. What is TDD?**
Write a failing test, make it pass with the simplest code, refactor, repeat.

**17. What changed between JUnit 4 and 5, and does JUnit 6 change the style?**
JUnit 5 split into Platform, Jupiter and Vintage, added `@ParameterizedTest`, `@Nested`, extensions (replacing `@RunWith`/`@Rule`) and lambdas in assertions. JUnit 6 (what Spring Boot 4 manages) keeps the same `org.junit.jupiter.api` annotations and style, so JUnit 5 code carries over.

---

## 20. Cheat Sheet

| Topic | Remember |
|---|---|
| Structure | Arrange, Act, Assert; one behaviour per test |
| JUnit | `@Test`, `@BeforeEach`, `@AfterEach`, `@BeforeAll` (static), `@Nested`, `@DisplayName`, `@Tag`, `@Disabled` |
| Instance | a **new instance per test** method |
| Assertions | `assertEquals(expected, actual)`, `assertThrows`, `assertAll`; AssertJ `assertThat(...)` |
| Parameterized | `@CsvSource`, `@ValueSource`, `@MethodSource`, `@EnumSource`, `@NullAndEmptySource` |
| Temp files | `@TempDir Path dir` |
| Mockito extension | `@ExtendWith(MockitoExtension.class)`, `@Mock`, `@Captor` |
| Stub | `when(m.f()).thenReturn(x)`, `thenThrow`, `thenAnswer`; `doThrow/doNothing/doReturn` for void and spies |
| Verify | `verify(m).f()` (once), `times(n)`, `never()`, `verifyNoInteractions`, `inOrder` |
| Matchers | all arguments matchers or none: `eq("x")`, `anyInt()` |
| Defaults | null / 0 / false / empty collection |
| Strict stubs | unused stub fails; `lenient()` opts out |
| Spy | `doReturn(x).when(spy).f()` |
| Static | `try (MockedStatic<T> s = mockStatic(T.class)) { ... }` |
| Time | inject `Clock`, `Clock.fixed(...)` |
| Coverage | JaCoCo finds untested code; 100% lines is not 100% safe; PIT checks the tests |

---

## 21. Practice Tasks

1. **Boundaries.** Add the missing tests found by the coverage report: negative totals in `PriceCalculator`, negative price and quantity in `Cart.Item`, `TextUtils.slugify(null)`. Re-run `mvn verify` and confirm the branch coverage rises.
2. **Table-driven.** Rewrite five separate tests of your own into one `@ParameterizedTest` with a `@MethodSource` of named cases, including `null`, empty, boundary and invalid values.
3. **A fake instead of mocks.** Replace the `InventoryClient` mock in one test with an in-memory fake class that tracks stock levels, and assert on the final stock instead of on `verify` calls. Compare how the two tests react when you refactor `OrderService`.
4. **A new rule, test first.** Add a rule "an order over 500000 paise needs a manager email copied" to `OrderService`. Write the failing test first, then the code. Use an `ArgumentCaptor` to check the recipient list.
5. **Partial failure.** Make the `EmailService` throw after the payment succeeded. Decide what `placeOrder` should do (refund? retry? return the receipt anyway?), encode the decision in a test, and implement it.
6. **Strict stubs.** Write a test with an extra, unused stub and read the exact error text. Then fix it by removing the stub, and by making it `lenient()`, and explain which is right.
7. **Static and time.** Take a class of yours that calls `LocalDate.now()` or `UUID.randomUUID()` directly. Write a test with `mockStatic`, then refactor the class to inject a `Clock` / `Supplier<UUID>` and rewrite the test without `mockStatic`.
8. **Mutation testing.** Add `pitest-maven` to the example, run it against `PriceCalculator` and `Cart`, list the surviving mutants, and write tests that kill each one.
9. **Flaky hunt.** Write a deliberately flaky test (it uses `System.nanoTime` or a shared static list), run it with `@RepeatedTest(50)` to make it fail, then fix the cause.

*End of notes.*
