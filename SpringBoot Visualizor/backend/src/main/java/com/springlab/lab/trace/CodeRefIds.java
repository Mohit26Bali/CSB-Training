package com.springlab.lab.trace;

import java.util.Map;
import java.util.Set;

/**
 * Ids of the entries in {@code lab/code-references.json} that trace steps point at. A test verifies that every id
 * listed here exists and that its excerpt still matches the real SpringLab source file.
 */
public final class CodeRefIds {

    // REST Request Lifecycle module
    public static final String SECURITY_FILTER_CHAIN = "security-filter-chain";
    public static final String AUTH_CONVERTER = "auth-converter";
    public static final String ENTRY_POINT = "entry-point";
    public static final String ACCESS_DENIED_HANDLER = "access-denied-handler";
    public static final String TOPIC_CONTROLLER = "topic-controller";
    public static final String TOPIC_SERVICE = "topic-service";
    public static final String TOPIC_REPOSITORY = "topic-repository";
    public static final String ME_CONTROLLER = "me-controller";
    public static final String ME_SERVICE = "me-service";
    public static final String CHALLENGE_GET_CONTROLLER = "challenge-get-controller";
    public static final String CHALLENGE_GET_SERVICE = "challenge-get-service";
    public static final String NOT_FOUND_HANDLER = "not-found-handler";
    public static final String ADMIN_CONTROLLER = "admin-controller";
    public static final String SQL_CAPTURE = "sql-capture";
    public static final String OPEN_IN_VIEW_CONFIG = "open-in-view-config";

    // JWT Security Simulator module
    public static final String JWT_DECODER_CONFIG = "jwt-decoder-config";
    public static final String JWT_ISSUING = "jwt-issuing";
    public static final String LAB_SECURE_CHAIN = "lab-secure-chain";
    public static final String LAB_JWT_SERVICE = "lab-jwt-service";
    public static final String LAB_SECURE_CONTROLLER = "lab-secure-controller";
    public static final String LOGIN_SERVICE = "login-service";
    public static final String USER_DETAILS_SERVICE = "user-details-service";
    public static final String SECURITY_BEANS = "security-beans";


    // JPA Relationship Lab module
    public static final String JPA_EMPLOYEE_OWNING = "jpa-employee-owning";
    public static final String JPA_DEPARTMENT_INVERSE = "jpa-department-inverse";
    public static final String JPA_DEPARTMENT_HELPER = "jpa-department-helper";
    public static final String JPA_PROFILE_ONE_TO_ONE = "jpa-profile-one-to-one";
    public static final String JPA_ACCOUNT_INVERSE = "jpa-account-inverse";
    public static final String JPA_STUDENT_JOIN_TABLE = "jpa-student-join-table";
    public static final String JPA_COURSE_INVERSE = "jpa-course-inverse";
    public static final String JPA_PROJECT_CASCADE = "jpa-project-cascade";
    public static final String JPA_TEAM_ORPHAN_REMOVAL = "jpa-team-orphan-removal";
    public static final String JPA_TASK_EAGER_DEFAULT = "jpa-task-eager-default";
    public static final String JPA_DEPARTMENT_REPOSITORY = "jpa-department-repository";
    public static final String JPA_LAB_SQL_CAPTURE = "jpa-lab-sql-capture";
    public static final String JPA_RECORDER_LIVE = "jpa-recorder-live";


    // JDBC Lab module
    public static final String JDBC_CONNECTION_ACQUIRE = "jdbc-connection-acquire";
    public static final String JDBC_PREPARED_STATEMENT = "jdbc-prepared-statement";
    public static final String JDBC_COMMIT_ROLLBACK = "jdbc-commit-rollback";
    public static final String JDBC_SAVEPOINT_SET = "jdbc-savepoint-set";
    public static final String JDBC_SAVEPOINT_ROLLBACK = "jdbc-savepoint-rollback";
    public static final String JDBC_BATCH = "jdbc-batch";
    public static final String JDBC_LAB_POOL = "jdbc-lab-pool";
    public static final String SPRING_JDBC_TEMPLATE = "spring-jdbc-template";
    public static final String SPRING_TX_TEMPLATE = "spring-tx-template";
    public static final String JDBC_PATH_PLAIN = "jdbc-path-plain";
    public static final String JDBC_PATH_SPRING = "jdbc-path-spring";
    public static final String JDBC_PATH_JPA = "jdbc-path-jpa";
    public static final String HIBERNATE_DO_WORK = "hibernate-do-work";
    public static final String JDBC_CAPTURE_EXECUTE = "jdbc-capture-execute";
    public static final String JDBC_SERVICE_RUN = "jdbc-service-run";


    // Microservices Lab module
    public static final String MICRO_BUS_CALL = "micro-bus-call";
    public static final String MICRO_BULKHEAD = "micro-bulkhead";
    public static final String MICRO_INVENTORY_RESERVE = "micro-inventory-reserve";
    public static final String MICRO_RUN_EXECUTE = "micro-run-execute";
    public static final String MICRO_SERVICE_BOUNDARY = "micro-service-boundary";
    public static final String MICRO_LOCAL_TRANSACTION = "micro-local-transaction";
    public static final String MICRO_MONOLITH_TX = "micro-monolith-tx";
    public static final String MICRO_ORDER_PLACE = "micro-order-place";
    public static final String MICRO_EVENT_CHANNEL = "micro-event-channel";
    public static final String MICRO_IDEMPOTENT_CONSUMER = "micro-idempotent-consumer";
    public static final String MICRO_GATEWAY_ROUTE = "micro-gateway-route";
    public static final String MICRO_RATE_LIMITER = "micro-rate-limiter";
    public static final String MICRO_REGISTRY_RESOLVE = "micro-registry-resolve";
    public static final String MICRO_INSTANCE_BEHAVIOUR = "micro-instance-behaviour";
    public static final String MICRO_OWNERSHIP_GUARD = "micro-ownership-guard";
    public static final String MICRO_OUTBOX_WRITE = "micro-outbox-write";
    public static final String MICRO_OUTBOX_RELAY = "micro-outbox-relay";
    public static final String MICRO_SAGA_ORCHESTRATOR = "micro-saga-orchestrator";
    public static final String MICRO_SAGA_CHOREOGRAPHY = "micro-saga-choreography";
    public static final String MICRO_CIRCUIT_BREAKER = "micro-circuit-breaker";
    public static final String MICRO_RETRY = "micro-retry";
    public static final String MICRO_IDEMPOTENT_CHARGE = "micro-idempotent-charge";
    public static final String MICRO_CORRELATION = "micro-correlation";
    public static final String MICRO_REGISTRY_TTL = "micro-registry-ttl";

    /** Handler / service / repository methods the assemblers know a source reference for ("Type#method"). */
    private static final Map<String, String> BY_METHOD = Map.ofEntries(
            Map.entry("TopicController#list", TOPIC_CONTROLLER),
            Map.entry("TopicService#list", TOPIC_SERVICE),
            Map.entry("TopicRepository#findAllWithActiveQuestionCount", TOPIC_REPOSITORY),
            Map.entry("AuthController#me", ME_CONTROLLER),
            Map.entry("AuthService#currentUser", ME_SERVICE),
            Map.entry("ChallengeController#get", CHALLENGE_GET_CONTROLLER),
            Map.entry("ChallengeService#get", CHALLENGE_GET_SERVICE),
            Map.entry("AdminUserController#list", ADMIN_CONTROLLER),
            Map.entry("UserRepository#findAuthInfoByUsername", AUTH_CONVERTER),
            Map.entry("LabSecureController#whoami", LAB_SECURE_CONTROLLER),
            Map.entry("LabSecureController#teacherArea", LAB_SECURE_CONTROLLER),
            Map.entry("LabSecureController#adminArea", LAB_SECURE_CONTROLLER));

    public static final Set<String> ALL = Set.of(SECURITY_FILTER_CHAIN, AUTH_CONVERTER, ENTRY_POINT,
            ACCESS_DENIED_HANDLER, TOPIC_CONTROLLER, TOPIC_SERVICE, TOPIC_REPOSITORY, ME_CONTROLLER, ME_SERVICE,
            CHALLENGE_GET_CONTROLLER, CHALLENGE_GET_SERVICE, NOT_FOUND_HANDLER, ADMIN_CONTROLLER, SQL_CAPTURE,
            OPEN_IN_VIEW_CONFIG, JWT_DECODER_CONFIG, JWT_ISSUING, LAB_SECURE_CHAIN, LAB_JWT_SERVICE,
            LAB_SECURE_CONTROLLER, LOGIN_SERVICE, USER_DETAILS_SERVICE, SECURITY_BEANS,
            JPA_EMPLOYEE_OWNING, JPA_DEPARTMENT_INVERSE, JPA_DEPARTMENT_HELPER, JPA_PROFILE_ONE_TO_ONE, JPA_ACCOUNT_INVERSE, JPA_STUDENT_JOIN_TABLE, JPA_COURSE_INVERSE, JPA_PROJECT_CASCADE, JPA_TEAM_ORPHAN_REMOVAL, JPA_TASK_EAGER_DEFAULT, JPA_DEPARTMENT_REPOSITORY, JPA_LAB_SQL_CAPTURE, JPA_RECORDER_LIVE,
            JDBC_CONNECTION_ACQUIRE, JDBC_PREPARED_STATEMENT, JDBC_COMMIT_ROLLBACK, JDBC_SAVEPOINT_SET, JDBC_SAVEPOINT_ROLLBACK, JDBC_BATCH, JDBC_LAB_POOL, SPRING_JDBC_TEMPLATE, SPRING_TX_TEMPLATE, JDBC_PATH_PLAIN, JDBC_PATH_SPRING, JDBC_PATH_JPA, HIBERNATE_DO_WORK, JDBC_CAPTURE_EXECUTE, JDBC_SERVICE_RUN,
            MICRO_BUS_CALL, MICRO_BULKHEAD, MICRO_INVENTORY_RESERVE, MICRO_RUN_EXECUTE, MICRO_SERVICE_BOUNDARY, MICRO_LOCAL_TRANSACTION,
            MICRO_MONOLITH_TX, MICRO_ORDER_PLACE, MICRO_EVENT_CHANNEL, MICRO_IDEMPOTENT_CONSUMER, MICRO_GATEWAY_ROUTE, MICRO_RATE_LIMITER, MICRO_REGISTRY_RESOLVE, MICRO_INSTANCE_BEHAVIOUR,
            MICRO_OWNERSHIP_GUARD, MICRO_OUTBOX_WRITE, MICRO_OUTBOX_RELAY, MICRO_SAGA_ORCHESTRATOR, MICRO_SAGA_CHOREOGRAPHY, MICRO_CIRCUIT_BREAKER, MICRO_RETRY, MICRO_IDEMPOTENT_CHARGE, MICRO_CORRELATION, MICRO_REGISTRY_TTL);

    private CodeRefIds() {
    }

    /** Returns the reference for "Type#method", or null when no verified excerpt exists (no code is then claimed). */
    public static String forMethod(String type, String method) {
        return BY_METHOD.get(type + "#" + method);
    }
}
