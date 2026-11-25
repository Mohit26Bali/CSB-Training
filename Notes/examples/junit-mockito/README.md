# JUnit and Mockito examples

The runnable project behind [`JUnit_and_Mockito_Testing_Notes.md`](../../JUnit_and_Mockito_Testing_Notes.md). It is a tiny shop (`Cart`, `PriceCalculator`, `OrderService` with a payment gateway, an inventory client and an email service) and **68 tests** that show every technique in the note.

| Test class | Shows |
| --- | --- |
| `JUnitBasicsTest` | assertions, AssertJ, lifecycle, parameterized tests, `@Nested`, `@TempDir`, dynamic tests, assumptions |
| `LifecycleTest` | the exact order of `@BeforeAll`, `@BeforeEach`, `@AfterEach`, `@AfterAll`, and a new instance per test |
| `OrderServiceTest` | Mockito: stubbing, `verify`, `InOrder`, captors, BDD style, `mockStatic`, `doThrow`, answers, spies, a fixed `Clock` |
| `StrictStubsTest` | what strict stubs report: unused stubs, argument mismatches, `lenient()` |
| `PuzzlesTest` | the answers to the output puzzles in the note |

## Run

Needs JDK 17 or newer and Maven (internet access the first time, to download dependencies):

```bash
mvn test        # run the tests
mvn verify      # also writes a JaCoCo coverage report to target/site/jacoco/index.html
```

Run one class or one method:

```bash
mvn -Dtest=OrderServiceTest test
mvn -Dtest=OrderServiceTest#thingsHappenInTheRightOrder test
```

The Spring Boot parent in `pom.xml` is only used for its tested versions of JUnit (6.0.x), Mockito (5.23) and AssertJ; the project contains no Spring code.
