package shop;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/** Records the order of the lifecycle callbacks. The last test (by explicit @Order) checks the recording. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LifecycleTest {

    static final List<String> LOG = new ArrayList<>();
    final int instanceId = System.identityHashCode(this);
    static final List<Integer> INSTANCES = new ArrayList<>();

    @BeforeAll static void beforeAll()  { LOG.add("beforeAll"); }
    @BeforeEach void beforeEach()       { LOG.add("beforeEach"); INSTANCES.add(instanceId); }
    @AfterEach void afterEach()         { LOG.add("afterEach"); }
    @AfterAll static void afterAll()    { LOG.add("afterAll"); }

    @Test @Order(1) void first()  { LOG.add("test1"); }
    @Test @Order(2) void second() { LOG.add("test2"); }

    @Test @Order(3)
    void theCallbacksRanInTheDocumentedOrder() {
        assertThat(LOG).containsExactly(
            "beforeAll",
            "beforeEach", "test1", "afterEach",
            "beforeEach", "test2", "afterEach",
            "beforeEach");                       // the third test's own beforeEach has run; its afterEach and afterAll are still to come
        assertThat(INSTANCES).doesNotHaveDuplicates();   // a NEW instance of the test class for every test
    }
}
