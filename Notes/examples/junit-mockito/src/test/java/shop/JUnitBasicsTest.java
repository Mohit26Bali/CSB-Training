package shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("JUnit 5/6 basics, on the shop classes")
class JUnitBasicsTest {

    // ---------- lifecycle: the order in which the callbacks run ----------
    static final List<String> EVENTS = new ArrayList<>();

    @BeforeAll static void beforeAll() { EVENTS.add("beforeAll"); }
    @AfterAll  static void afterAll()  { EVENTS.add("afterAll"); }

    Cart cart;

    @BeforeEach void beforeEach() {
        EVENTS.add("beforeEach");
        cart = new Cart();                          // a fresh object for every test: tests never share state
        cart.add("PEN", 1000, 2);
        cart.add("BOOK", 25000, 1);
    }
    @AfterEach void afterEach() { EVENTS.add("afterEach"); }

    @Test
    void junitCreatesANewTestClassInstanceForEveryTest() {
        // beforeEach has run, and the cart field holds exactly what beforeEach built
        assertEquals(2, cart.items().size());
        assertEquals(27000, cart.total());
    }

    // ---------- assertions ----------
    @Test
    @DisplayName("assertAll reports every failure, not only the first")
    void groupedAssertions() {
        assertAll("cart",
            () -> assertEquals(27000, cart.total()),
            () -> assertFalse(cart.isEmpty()),
            () -> assertEquals("PEN", cart.items().get(0).sku()));
    }

    @Test
    void expectedExceptionsAreAssertedWithAssertThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> cart.add("", 100, 1));
        assertEquals("sku is required", ex.getMessage());
        assertThrows(IllegalArgumentException.class, () -> cart.add("X", 100, 0));
    }

    @Test
    void assertJReadsLikeEnglishAndExplainsFailures() {
        assertThat(cart.items()).hasSize(2).extracting(Cart.Item::sku).containsExactly("PEN", "BOOK");
        assertThat(cart.total()).isEqualTo(27000).isBetween(1, 100_000);
        assertThatThrownBy(() -> PriceCalculator.apply(100, "NOPE"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unknown code");
    }

    @Test
    void theItemListCannotBeChangedFromOutside() {
        assertThrows(UnsupportedOperationException.class, () -> cart.items().clear());
        assertIterableEquals(List.of("PEN", "BOOK"), cart.items().stream().map(Cart.Item::sku).toList());
    }

    @Test
    void aSlowTestCanBeCutOff() {
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> cart.total());
    }

    // ---------- parameterized tests: one test, many inputs ----------
    @ParameterizedTest(name = "{0} with total {1} gives {2}")
    @CsvSource({
        "SAVE10,  100000, 90000",
        "save10,  100000, 90000",     // the code is case-insensitive
        "SAVE20,  100000, 80000",
        "FLAT500, 300,    0",         // never below zero
        "FLAT500, 1000,   500"
    })
    void discountCodes(String code, int total, int expected) {
        assertEquals(expected, PriceCalculator.apply(total, code));
    }

    @ParameterizedTest
    @ValueSource(strings = {"racecar", "A man, a plan, a canal: Panama", "Was it a car or a cat I saw"})
    void palindromes(String text) {
        assertTrue(TextUtils.isPalindrome(text));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t"})
    void blankCodesLeaveTheTotalUnchanged(String code) {
        assertEquals(500, PriceCalculator.apply(500, code));
    }

    enum Code { SAVE10, SAVE20, FLAT500 }

    @ParameterizedTest
    @EnumSource(value = Code.class, names = {"SAVE10", "FLAT500"})
    void everyCodeWorksOnABigTotal(Code code) {
        assertThat(PriceCalculator.apply(200_000, code.name())).isLessThan(200_000);
    }

    static Stream<Arguments> slugCases() {
        return Stream.of(
            Arguments.of("Hello, World!", "hello-world"),
            Arguments.of("  Spring  Boot 4  ", "spring-boot-4"),
            Arguments.of("Crème brûlée", "creme-brulee"));
    }

    @ParameterizedTest
    @MethodSource("slugCases")
    void slugs(String input, String expected) {
        assertEquals(expected, TextUtils.slugify(input));
    }

    // ---------- other JUnit features ----------
    static final AtomicInteger REPEATS = new AtomicInteger();

    @RepeatedTest(3)
    void aRepeatedTestRunsThreeTimes() {
        REPEATS.incrementAndGet();
        assertNotNull(cart);
    }

    @Test
    void assumptionsSkipATestInsteadOfFailingIt() {
        assumeTrue(System.getenv("RUN_SLOW_TESTS") != null, "set RUN_SLOW_TESTS to run this");
        throw new AssertionError("never reached unless RUN_SLOW_TESTS is set");
    }

    @Test
    @Tag("slow")
    void tagsLetYouSelectGroupsOfTests() {
        assertTrue(true);
    }

    @Test
    void tempDirGivesAFreshFolderThatIsDeletedAfterwards(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("receipt.txt");
        Files.writeString(file, "paid 27000");
        assertEquals("paid 27000", Files.readString(file));
    }

    @TestFactory
    Stream<DynamicTest> testsGeneratedAtRunTime() {
        return Stream.of("SAVE10", "SAVE20", "FLAT500").map(code ->
            DynamicTest.dynamicTest("code " + code + " exists", () -> assertDoesNotThrowFor(code)));
    }

    private void assertDoesNotThrowFor(String code) {
        assertThat(PriceCalculator.apply(150_000, code)).isPositive();
    }

    // ---------- @Nested: group tests by situation ----------
    @Nested
    @DisplayName("when the cart is empty")
    class EmptyCart {
        Cart empty;
        @BeforeEach void emptyCart() { empty = new Cart(); }

        @Test void totalIsZero() { assertEquals(0, empty.total()); }
        @Test void isEmpty() { assertTrue(empty.isEmpty()); }
        @Test void removingAnUnknownItemReturnsFalse() { assertFalse(empty.remove("NOPE")); }
    }

    @Nested
    @DisplayName("SAVE20")
    class Save20 {
        @Test void needsAMinimumTotal() {
            assertThrows(IllegalArgumentException.class, () -> PriceCalculator.apply(99_999, "SAVE20"));
        }
        @Test void takesTwentyPercentOffAtTheMinimum() {
            assertEquals(80_000, PriceCalculator.apply(100_000, "SAVE20"));
        }
    }

    // ---------- verifying the lifecycle and repeat results at the very end of this class ----------
    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)          // one instance for all tests of this nested class
    class SharedInstance {
        int counter;
        @Test void first()  { counter++; }
        @Test void second() { counter++; }
        @AfterAll void bothTestsSawTheSameInstance() { assertEquals(2, counter); }
    }
}
