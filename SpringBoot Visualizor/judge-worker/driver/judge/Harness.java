package judge;

import java.util.Set;

/**
 * The exercises the worker can judge. The kind is chosen by the BACKEND (from the problem row in its database) and is passed to the
 * driver and to the sandbox runtime as a fixed, validated name; a learner never selects it.
 *
 * <p>For a scripted exercise, {@code operations} is the complete list of operations its adapter understands. The driver refuses any test
 * script that uses another word, so a malformed or tampered test never reaches the sandbox.
 */
public enum Harness {
    RETRY_EXECUTOR("RetryExecutor", Set.of()),
    CIRCUIT_BREAKER("CircuitBreaker", Set.of("new", "at", "allow", "ok", "fail", "state")),
    BULKHEAD("Bulkhead", Set.of("new", "call", "start", "finish", "inflight", "permits")),
    IDEMPOTENCY("IdempotencyHandler", Set.of("new", "at", "handle", "runs")),
    SAGA("SagaCoordinator", Set.of("new", "run", "calls", "pending")),
    OUTBOX("OutboxRelay", Set.of("events", "failpub", "failmark", "relay", "consume", "delivered", "applied", "unpublished")),

    // The everyday exercises (Phase F6) share one reflective adapter: a script builds the learner's object ("new") and calls its methods ("call").
    PASSWORD_VALIDATOR("PasswordValidator", Set.of("new", "call")),
    LOAN_ELIGIBILITY("LoanEligibility", Set.of("new", "call")),
    SALARY_CALCULATOR("SalaryCalculator", Set.of("new", "call")),
    BANK_ACCOUNT("BankAccount", Set.of("new", "call")),
    TRAINING_REGISTRY("TrainingRegistry", Set.of("new", "call")),
    GRADE_CALCULATOR("GradeCalculator", Set.of("new", "call")),
    REQUEST_VALIDATOR("RequestValidator", Set.of("new", "call")),
    PRODUCT_SERVICE("ProductService", Set.of("new", "call")),
    ERROR_MAPPER("ErrorMapper", Set.of("new", "call")),
    BEAN_REGISTRY("BeanRegistry", Set.of("new", "call"));

    private final String className;
    private final Set<String> operations;

    Harness(String className, Set<String> operations) {
        this.className = className;
        this.operations = operations;
    }

    /** The one class the learner's single source file must declare; also the file name. */
    public String className() {
        return className;
    }

    Set<String> operations() {
        return operations;
    }

    boolean scripted() {
        return this != RETRY_EXECUTOR;
    }
}
