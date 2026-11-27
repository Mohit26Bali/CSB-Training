"""Regenerates backend/src/main/resources/lab/code-references.json from the REAL SpringLab source files.

Why a script: excerpts are copied from the files, never typed, and every note's token is checked to really be on its
line. CodeReferenceTest (backend) then re-verifies the JSON against the sources on every build. If you edit a file
that is listed below (or move lines in it), that test fails: adjust the line numbers here and run

    python docs/tools/gen_code_refs.py

Line numbers are 1-based and inclusive. Run from anywhere; paths are resolved relative to this file.
"""
import json
import sys
from pathlib import Path

BACKEND = Path(__file__).resolve().parents[2] / "backend"
J = "src/main/java/com/springlab/"

# (id, title, file, startLine, endLine, [(line, token, note), ...])
REFS = [
    ("topic-controller", "TopicController: the REST entry point", J + "controller/TopicController.java", 11, 24, [
        (11, "@RestController", "Marks a controller whose return values are written to the response body (it combines @Controller and @ResponseBody)."),
        (12, "@RequestMapping", "Maps every handler method of this class under /api/topics."),
        (17, "TopicService", "Constructor injection: the controller depends on a service, never on a repository or entity."),
        (21, "@GetMapping", "Maps HTTP GET /api/topics to this method; DispatcherServlet's HandlerMapping resolves this."),
        (23, "topicService.list()", "The controller only delegates; business logic and data access live in the service layer."),
    ]),
    ("topic-service", "TopicService: transaction boundary", J + "service/TopicService.java", 19, 22, [
        (19, "@Transactional(readOnly = true)", "Declares a read-only transaction around this method (applied through a Spring proxy). This line is a declaration; it does not prove a transaction was active."),
        (21, "findAllWithActiveQuestionCount", "The service delegates data access to the Spring Data repository."),
    ]),
    ("topic-repository", "TopicRepository: a JPQL query method", J + "repository/TopicRepository.java", 15, 20, [
        (15, "@Query", "Declares the query as JPQL. Hibernate translates it to SQL for the configured MySQL dialect."),
        (17, "select count(q)", "A correlated sub-select computes the active question count, so the whole listing needs one statement."),
        (20, "findAllWithActiveQuestionCount", "No implementation exists: Spring Data generates a proxy for this interface at startup."),
    ]),
    ("me-controller", "AuthController.me: reading the authenticated principal", J + "controller/AuthController.java", 40, 43, [
        (40, "@GetMapping", "GET /api/auth/me. The path is not public, so the security filter chain requires authentication first."),
        (41, "@AuthenticationPrincipal", "Spring Security injects the principal of the current Authentication; here it is the validated Jwt."),
    ]),
    ("me-service", "AuthService.currentUser: a lean lookup for the profile", J + "service/AuthService.java", 72, 78, [
        (72, "@Transactional(readOnly = true)", "Read-only transaction declaration for this lookup."),
        (74, "must not be honoured", "A valid token for a user that no longer exists is not accepted."),
        (76, "findProfileByUsername", "A projection query: only id, username, email and role are selected, never the password hash."),
        (77, "BadCredentialsException", "No such user: the request is refused rather than answered with empty data."),
    ]),
    ("auth-converter", "DatabaseBackedJwtAuthenticationConverter: authorities come from the database", J + "security/DatabaseBackedJwtAuthenticationConverter.java", 34, 46, [
        (35, "convert", "Called after the JwtDecoder has verified signature, expiry and issuer. It turns the Jwt into an Authentication."),
        (41, "findAuthInfoByUsername", "Reads the caller's current role and enabled flag from the database on every authenticated request."),
        (43, "InvalidBearerTokenException", "Unknown or disabled users are rejected with the same 401 as an invalid token."),
        (44, "ROLE_", "The authority comes from the database role, never from the token's role claim."),
    ]),
    ("security-filter-chain", "SecurityConfig: the SecurityFilterChain", J + "config/SecurityConfig.java", 27, 53, [
        (27, "SecurityFilterChain", "Spring Security 6 configures security by declaring a SecurityFilterChain bean (no WebSecurityConfigurerAdapter)."),
        (34, "csrf", "CSRF protection is disabled because the API is stateless and authenticates with an Authorization header, not cookies."),
        (35, "STATELESS", "No HTTP session is created or used to store the SecurityContext."),
        (42, "hasRole", "Coarse URL rule: /api/admin/** needs the ADMIN role. Controllers repeat it with @PreAuthorize."),
        (44, "anyRequest", "Everything not listed above requires an authenticated caller."),
        (45, "oauth2ResourceServer", "Adds BearerTokenAuthenticationFilter, which reads the Bearer token and validates it with the JwtDecoder."),
    ]),
    ("entry-point", "RestAuthenticationEntryPoint: the 401 response", J + "security/RestAuthenticationEntryPoint.java", 25, 33, [
        (26, "commence", "Called by ExceptionTranslationFilter when an unauthenticated request must be challenged."),
        (30, "UNAUTHORIZED", "Writes the 401 status as an application/problem+json body."),
    ]),
    ("access-denied-handler", "RestAccessDeniedHandler: the 403 response", J + "security/RestAccessDeniedHandler.java", 22, 28, [
        (23, "handle", "Called by ExceptionTranslationFilter when an authenticated caller is not allowed."),
        (25, "FORBIDDEN", "Writes the 403 status as an application/problem+json body."),
    ]),
    ("challenge-get-controller", "ChallengeController.get", J + "controller/ChallengeController.java", 50, 53, [
        (50, "@GetMapping", "GET /api/challenges/{id}."),
        (51, "@PathVariable", "Spring MVC converts the {id} URI segment to a Long before the method runs."),
    ]),
    ("challenge-get-service", "ChallengeService.ownedAttempt: a lookup that can fail", J + "service/ChallengeService.java", 154, 158, [
        (155, "currentUser", "Loads the caller (one query)."),
        (156, "findByIdAndUserId", "Ownership is part of the query, so someone else's attempt is simply not found."),
        (157, "ResourceNotFoundException", "Thrown when nothing matches; it is translated to HTTP 404 by the exception handler."),
    ]),
    ("not-found-handler", "GlobalExceptionHandler: ResourceNotFoundException becomes 404", J + "exception/GlobalExceptionHandler.java", 73, 78, [
        (73, "@ExceptionHandler", "Methods annotated like this are used by ExceptionHandlerExceptionResolver to translate exceptions thrown from controllers and services."),
        (75, "NOT_FOUND", "Builds an RFC 9457 ProblemDetail with status 404."),
    ]),
    ("admin-controller", "AdminUserController: a role-restricted controller", J + "controller/AdminUserController.java", 24, 38, [
        (25, "@PreAuthorize", "Method security: the class is only callable with the ADMIN role (needs @EnableMethodSecurity)."),
        (34, "@GetMapping", "GET /api/admin/users."),
    ]),
    # ---- JWT Security Simulator module ----
    ("jwt-decoder-config", "JwtConfig.jwtDecoder: the application's real decoder", J + "config/JwtConfig.java", 48, 56, [
        (50, "withSecretKey", "HS256 is symmetric: the decoder is built from the same secret key that signs tokens."),
        (51, "MacAlgorithm.HS256", "The decoder accepts only HS256. The server decides the algorithm; the token's header cannot."),
        (54, "createDefaultWithIssuer", "Adds the default validators (exp and nbf with a 60 second clock skew) plus an issuer check. A correct signature alone is not enough."),
    ]),
    ("jwt-issuing", "JwtService.issueFor: header, payload and signature", J + "security/JwtService.java", 31, 45, [
        (33, "JwtClaimsSet", "The payload is a set of claims."),
        (34, ".issuer(", "iss: who issued the token."),
        (35, ".subject(", "sub: who the token is about."),
        (37, ".expiresAt(", "exp: when the token stops being valid."),
        (39, ".claim(ROLE_CLAIM", "A custom role claim. It is informational: authorization ignores it and reads the database."),
        (42, "MacAlgorithm.HS256", "The header names the algorithm (alg)."),
        (43, "encode(", "The encoder signs header and payload with the secret key and returns header.payload.signature."),
    ]),
    ("lab-secure-chain", "LabSecurityConfig: the lab SecurityFilterChain", J + "lab/jwt/LabSecurityConfig.java", 30, 51, [
        (31, "@Order(1)", "This chain wins for its own paths; every other request still reaches the application's main chain."),
        (37, "securityMatcher", "This chain handles only /api/lab/secure/**."),
        (41, "hasRole", "Authorization rule: the ADMIN role is required."),
        (42, "hasAnyRole", "Authorization rule: TEACHER or ADMIN."),
        (43, "anyRequest", "Anything else under the lab path needs an authenticated caller."),
        (45, "jwt.decoder(lab.decoder())", "A decoder INSTANCE for the lab key is passed directly, so no second JwtDecoder bean exists."),
        (45, "jwtAuthenticationConverter", "The application's own converter: the database lookup of role and enabled flag."),
        (46, "authenticationEntryPoint", "The application's own handler writes the 401."),
        (47, "accessDeniedHandler", "The application's own handler writes the 403."),
    ]),
    ("lab-jwt-service", "LabJwtService: the separate lab key and decoder", J + "lab/jwt/LabJwtService.java", 59, 68, [
        (61, "randomKey", "The lab key is random, kept in memory, never configured, logged or returned, and changes on every restart."),
        (63, "foreignEncoder", "An unrelated second key signs the 'wrong key' experiment tokens."),
        (66, "withSecretKey", "The same decoder class and algorithm as the real decoder, built from the lab key."),
        (67, "createDefaultWithIssuer", "The same default validators plus an issuer check, with the lab issuer."),
    ]),
    ("lab-secure-controller", "LabSecureController.whoami: what Spring Security made of the token", J + "lab/jwt/LabSecureController.java", 29, 36, [
        (29, "@GetMapping", "GET /api/lab/secure/whoami."),
        (30, "@AuthenticationPrincipal", "The validated Jwt of the current Authentication."),
        (31, "getAuthorities", "The authorities that Spring Security built for this request (from the database)."),
        (33, "getClaimAsString", "The role claim the token carried: reported, not trusted."),
        (35, "authorities.contains", "Compares the stored role with the claim to show that they can differ."),
    ]),
    ("login-service", "AuthService.login: the username/password path", J + "service/AuthService.java", 62, 70, [
        (64, "authenticationManager.authenticate", "Login authenticates the submitted password through the AuthenticationManager. This is the login path only."),
        (65, "UsernamePasswordAuthenticationToken.unauthenticated", "An unauthenticated token that carries the submitted credentials."),
        (68, "jwtService.issueFor", "On success a JWT is issued. Later requests are authenticated from that token, never from the password."),
    ]),
    ("user-details-service", "AppUserDetailsService: loading the stored user at login", J + "security/AppUserDetailsService.java", 20, 29, [
        (21, "loadUserByUsername", "Called by DaoAuthenticationProvider during login to load the stored user."),
        (22, "findByUsername", "Reads the user, including the password hash, from the database."),
        (25, "getPasswordHash", "The stored hash is compared with the submitted password by the PasswordEncoder."),
        (26, "roles(", "Adds the ROLE_ prefix to the stored role."),
        (27, "disabled", "A disabled account cannot log in."),
    ]),
    ("security-beans", "SecurityConfig: PasswordEncoder and the login AuthenticationManager", J + "config/SecurityConfig.java", 56, 73, [
        (58, "passwordEncoder", "The encoder that verifies password hashes at login."),
        (59, "createDelegatingPasswordEncoder", "Delegating encoder: the hash carries its algorithm id, for example {bcrypt}."),
        (67, "AuthenticationManager", "The manager used by the login endpoint, declared explicitly."),
        (69, "DaoAuthenticationProvider", "The one provider: it checks a username and password."),
        (70, "setUserDetailsService", "The provider loads the stored user through the application's UserDetailsService."),
        (71, "setPasswordEncoder", "...and checks the submitted password with the application's PasswordEncoder."),
        (72, "new ProviderManager", "A plain ProviderManager, so its providers are public API and can be reported without reflection."),
    ]),
    ("sql-capture", "CapturingStatementInspector: how SQL text is captured", J + "lab/sql/CapturingStatementInspector.java", 19, 30, [
        (19, "StatementInspector", "A Hibernate SPI called with the SQL text of every statement Hibernate is about to prepare."),
        (27, "recordSql", "Recorded only while the current thread runs a traced request."),
        (29, "return sql", "The statement is returned unchanged, so application behaviour is never altered."),
    ]),
    ("open-in-view-config", "application.yml: open-in-view", "src/main/resources/application.yml", 11, 16, [
        (13, "open-in-view", "SpringLab sets spring.jpa.open-in-view to false, so no EntityManager stays bound to the thread after the service returns."),
        (16, "statement_inspector", "Registers the lab's SQL-text recorder with Hibernate."),
    ]),

    # ---- JPA Relationship Lab ----
    ("jpa-employee-owning", "Employee.department: the owning side of the foreign key", J + "lab/jpa/entity/Employee.java", 18, 20, [
        (18, "@ManyToOne", "Many employees reference one department, so this is the many side and the owning side."),
        (18, "fetch = FetchType.LAZY", "The department is not loaded with the employee; a proxy stands in for it until it is used."),
        (19, "@JoinColumn", "Names the foreign key column that lives in the employee table. Hibernate writes THIS field into department_id."),
    ]),
    ("jpa-department-inverse", "Department.employees: the inverse side (mappedBy)", J + "lab/jpa/entity/Department.java", 24, 25, [
        (24, "mappedBy", "Says the foreign key is owned by Employee.department. Changing this list never writes a foreign key."),
        (25, "employees", "A normal Java list. Hibernate loads it lazily and does not keep it in sync with the other side for you."),
    ]),
    ("jpa-department-helper", "Department.addEmployee: keeping both sides consistent", J + "lab/jpa/entity/Department.java", 55, 58, [
        (56, "employees.add(employee)", "Updates the inverse side so the object graph in memory is consistent."),
        (57, "employee.setDepartment(this)", "Updates the owning side, which is the only change that writes the foreign key."),
    ]),
    ("jpa-profile-one-to-one", "Profile.account: a one-to-one owning side", J + "lab/jpa/entity/Profile.java", 17, 19, [
        (17, "@OneToOne", "One profile belongs to exactly one account."),
        (18, "unique = true", "Creates the UNIQUE constraint on account_id; without it the schema would allow many profiles per account."),
        (18, "@JoinColumn", "The foreign key column is in the profile table, so Profile owns the association."),
    ]),
    ("jpa-account-inverse", "Account.profile: the inverse side of the one-to-one", J + "lab/jpa/entity/Account.java", 16, 17, [
        (16, "mappedBy", "Account has no column for the profile; it only mirrors the association owned by Profile.account."),
    ]),
    ("jpa-student-join-table", "Student.courses: the owning side of a many-to-many", J + "lab/jpa/entity/Student.java", 21, 25, [
        (21, "@ManyToMany", "Many students, many courses: stored in a third table."),
        (22, "@JoinTable", "Declares the join table and makes this side the owner."),
        (23, "joinColumns", "The join table column that references THIS entity (the student)."),
        (24, "inverseJoinColumns", "The join table column that references the OTHER entity (the course)."),
    ]),
    ("jpa-course-inverse", "Course.students: the inverse side of the many-to-many", J + "lab/jpa/entity/Course.java", 19, 20, [
        (19, "mappedBy", "Points at Student.courses. Changing this set alone writes nothing to the join table."),
    ]),
    ("jpa-project-cascade", "Project.tasks: cascade ALL without orphanRemoval", J + "lab/jpa/entity/Project.java", 20, 21, [
        (20, "cascade = CascadeType.ALL", "persist, merge, remove, refresh and detach on the project are applied to its tasks too."),
        (20, "mappedBy", "The collection is the inverse side: removing a task from the list writes no SQL by itself."),
    ]),
    ("jpa-team-orphan-removal", "Team.members: cascade ALL with orphanRemoval", J + "lab/jpa/entity/Team.java", 20, 21, [
        (20, "orphanRemoval = true", "A member removed from this collection is deleted. This is separate from cascade REMOVE, which reacts to deleting the team."),
        (20, "cascade = CascadeType.ALL", "Operations on the team also apply to its members."),
    ]),
    ("jpa-task-eager-default", "Task.project: the default fetch type of @ManyToOne", J + "lab/jpa/entity/Task.java", 20, 22, [
        (20, "@ManyToOne", "No fetch attribute is written, so the Jakarta Persistence default applies: EAGER."),
        (21, "@JoinColumn", "project_id is the foreign key in the task table."),
    ]),
    ("jpa-department-repository", "DepartmentRepository: three ways to load the same departments", J + "lab/jpa/DepartmentRepository.java", 14, 25, [
        (15, "@Query", "A plain JPQL query: the employees stay lazy."),
        (19, "join fetch", "A fetch join loads the departments and their employees in one statement."),
        (23, "@EntityGraph", "An entity graph asks for the same association to be fetched without changing the JPQL."),
    ]),
    ("jpa-lab-sql-capture", "JpaLabService.run: how the lab observes real Hibernate statements", J + "lab/jpa/JpaLabService.java", 98, 111, [
        (98, "new TraceCollector()", "The same collector the other modules use; one per run and never shared between threads."),
        (102, "TraceContext.bind(collector)", "From here, every statement Hibernate prepares on this thread is recorded by the StatementInspector."),
        (109, "TraceContext.clear()", "The collector is unbound before cleanup, so cleanup statements are never recorded."),
        (111, "cleanup(runKey)", "Deletes exactly the rows this run created, whatever happened."),
    ]),
    ("jpa-recorder-live", "ExperimentRecorder.live: attributing statements to the step that caused them", J + "lab/jpa/ExperimentRecorder.java", 54, 59, [
        (56, "mark = collector.sqlCount()", "Remembers how many statements existed before the action."),
        (57, "action.run()", "The real action runs here (persist, flush, a query, ...)."),
        (58, "Provenance.CAPTURED", "Statements prepared while the action ran are attached to this step and labelled LIVE."),
    ]),

    # ---- JDBC Lab ----
    ("jdbc-connection-acquire", "Getting a connection and asking MySQL which session it is", J + "lab/jdbc/JdbcFundamentalsExperiments.java", 35, 36, [
        (35, 'c.open("A")', "The experiment asks the application's pooled DataSource for a connection (captured, so its calls are recorded)."),
        (36, "sessionId", "A fixed query on the same physical session returns MySQL's connection_id(): it identifies the session, not the Java object."),
    ]),
    ("jdbc-prepared-statement", "PreparedStatement: placeholders, binding, execution", J + "lab/jdbc/JdbcStatementExperiments.java", 86, 96, [
        (86, "prepareStatement(sql)", "The SQL text contains ? placeholders and no values."),
        (92, "setString(1, c.runKey)", "Values are bound separately, by 1-based position. They are never part of the SQL text."),
        (95, "executeQuery()", "Executes the statement with the bound values and returns a ResultSet."),
    ]),
    ("jdbc-commit-rollback", "commit() or rollback() ends the transaction", J + "lab/jdbc/JdbcTransactionExperiments.java", 140, 144, [
        (141, "s.a.commit()", "Makes every statement of the transaction permanent together."),
        (143, "s.a.rollback()", "Discards every statement of the transaction, including ones whose executeUpdate returned normally."),
    ]),
    ("jdbc-savepoint-set", "setAutoCommit(false) and setSavepoint", J + "lab/jdbc/JdbcTransactionExperiments.java", 213, 216, [
        (213, "setAutoCommit(false)", "Starts a transaction scope: statements are now grouped until commit() or rollback()."),
        (216, "setSavepoint", "Names a point inside the transaction that can be rolled back to later."),
    ]),
    ("jdbc-savepoint-rollback", "rollback(savepoint)", J + "lab/jdbc/JdbcTransactionExperiments.java", 227, 227, [
        (227, "s.a.rollback(s.savepoint)", "Undoes only the work after the savepoint; the transaction stays open."),
    ]),
    ("jdbc-batch", "addBatch and executeBatch", J + "lab/jdbc/JdbcCrudExperiments.java", 144, 150, [
        (144, "prepareStatement", "One statement text, prepared once."),
        (148, "ps.addBatch()", "Stores the current parameter set; nothing is sent yet."),
        (150, "ps.executeBatch()", "Sends the stored parameter sets and returns one update count per set."),
    ]),
    ("jdbc-lab-pool", "A dedicated, bounded HikariCP pool for one experiment", J + "lab/jdbc/JdbcConnections.java", 45, 55, [
        (45, "newLabPool", "Creates a pool that belongs to ONE run and is closed when it ends; the application's own pool is never changed."),
        (51, "setMaximumPoolSize", "The ceiling on how many connections this pool may hold."),
        (53, "setConnectionTimeout", "How long a caller waits for a free connection before getting an exception."),
    ]),
    ("spring-jdbc-template", "JdbcTemplate with a RowMapper", J + "lab/jdbc/SpringJdbcExperiments.java", 75, 82, [
        (75, "RowMapper<CustomerRow> mapper", "Maps ONE row to a Java object; the template calls it once per row."),
        (82, "template.query(sql, mapper, c.runKey)", "One call: connection, statement, binding, execution, iteration and closing are done by the template."),
    ]),
    ("spring-tx-template", "TransactionTemplate over JdbcTemplate", J + "lab/jdbc/SpringJdbcExperiments.java", 187, 190, [
        (187, "tx.executeWithoutResult", "Spring opens the transaction, runs the callback, then commits or (on an exception) rolls back."),
        (189, "template.update", "Both updates reuse the one connection the transaction manager bound to the thread."),
    ]),
    ("jdbc-path-plain", "Path A: plain JDBC", J + "lab/jdbc/JdbcComparisonExperiments.java", 41, 44, [
        (42, "prepareStatement", "You write the SQL and manage the PreparedStatement."),
        (43, "ps.setLong(1, id)", "You bind every parameter."),
        (44, "executeQuery()", "You iterate and read the ResultSet yourself."),
    ]),
    ("jdbc-path-spring", "Path B: JdbcTemplate", J + "lab/jdbc/JdbcComparisonExperiments.java", 51, 52, [
        (52, "template.queryForObject", "Still your SQL, but resources and exceptions are handled by the template."),
    ]),
    ("jdbc-path-jpa", "Path C: EntityManager.find", J + "lab/jdbc/JdbcComparisonExperiments.java", 59, 60, [
        (60, "c.em.find", "No SQL is written: Hibernate generates it from the entity mapping and runs it through JDBC."),
    ]),
    ("hibernate-do-work", "Session.doWork exposes Hibernate's JDBC connection", J + "lab/jdbc/JdbcComparisonExperiments.java", 172, 175, [
        (172, "doWork", "Hands out the java.sql.Connection Hibernate itself uses."),
        (173, "connection.getClass()", "It is an ordinary pooled JDBC connection: Hibernate talks to MySQL through JDBC."),
    ]),
    ("jdbc-capture-execute", "How the lab observes JDBC calls", J + "lab/jdbc/JdbcCapture.java", 116, 137, [
        (118, "method.invoke(target, args)", "The call is forwarded unchanged to the real driver object; nothing is faked."),
        (120, "EXECUTE", "A failed execution is recorded too, with the exception class (never the message)."),
        (136, "log.add(label, \"EXECUTE\", clean(sql), detail)", "The SQL text (placeholders) and what the call returned are recorded; bound values never are."),
    ]),
    ("jdbc-service-run", "How a run is isolated and cleaned up", J + "lab/jdbc/JdbcLabService.java", 99, 113, [
        (100, "TraceContext.bind(hibernate)", "The existing SQL inspector observes the JPA path of the comparison experiments."),
        (109, "context.closeEverything()", "Connections and the lab pool are closed whatever happened."),
        (112, "removed = cleanup(runKey)", "Only the rows of THIS run (by its random key) are removed, child tables first; the count is reported."),
    ]),

    # ---- Microservices Lab ----
    ("micro-bus-call", "ServiceBus.call: where a call succeeds, fails, times out or is rejected", J + "lab/micro/core/ServiceBus.java", 189, 241, [
        (189, "bulkheads.tryAcquire(target)", "A call needs a permit of the TARGET service. Without one it is rejected here and never reaches the handler."),
        (197, "Mode.DOWN", "UNAVAILABLE: the connection is refused, the request is never processed."),
        (201, "Mode.REQUEST_LOST", "The request is lost: the caller times out although nothing was done."),
        (216, "handler.handle(", "The handler runs as ordinary Java code with its own local database transaction."),
        (232, "total > options.timeoutMillis()", "A slow answer becomes a TIMEOUT for the caller even though the handler already finished its work."),
    ]),
    ("micro-bulkhead", "Bulkheads: bounded concurrency permits per service", J + "lab/micro/core/Bulkheads.java", 74, 79, [
        (75, "semaphoreOf(target)", "PER_SERVICE gives every service its own Semaphore, SHARED gives all services one."),
        (79, "s.tryAcquire()", "A real, non-blocking Semaphore permit. null means the target is saturated."),
    ]),
    ("micro-inventory-reserve", "Inventory: one local transaction decides the reservation", J + "lab/micro/inventory/InventoryService.java", 74, 93, [
        (78, "db.local(ServiceName.INVENTORY", "The Inventory service commits its own short local transaction. No other service takes part in it."),
        (79, "findReservation", "Idempotent: asking again for the same order returns the existing reservation."),
        (86, "repository.decrement", "A guarded UPDATE (available >= quantity) takes the stock atomically; 0 rows changed means not enough."),
        (87, "OUT_OF_STOCK", "A BUSINESS refusal: the service answered normally, it is not a failure of the service."),
    ]),
    ("micro-run-execute", "A run executes at most once: compare-and-set on its state", J + "lab/micro/run/MicroRunService.java", 127, 145, [
        (132, "isFinished()", "A finished run returns its stored result and executes nothing."),
        (141, "store.transition(row.runKey(), owner, RunState.CREATED, RunState.EXECUTING)", "The database decides which concurrent request wins the move CREATED to EXECUTING; the others get the stored result or a 409."),
    ]),
    ("micro-service-boundary", "A service can register only its own operations", J + "lab/micro/core/HandlerRegistry.java", 32, 35, [
        (33, "operation.service() != owner", "Another service's contract operation cannot be implemented or hijacked from outside that service."),
    ]),
    ("micro-local-transaction", "Every service gets its own connections and transaction manager", J + "lab/micro/run/RunContext.java", 151, 153, [
        (152, "new DataSourceTransactionManager", "One transaction manager per service, so a call from one service into another opens a second, independent transaction."),
    ]),
    ("micro-monolith-tx", "The monolith: one database transaction for the whole workflow", J + "lab/micro/monolith/MonolithOrderWorkflow.java", 37, 70, [
        (38, "db.localFor(LABEL", "One transaction covers users, products, stock, reservations and orders."),
        (59, "update micro_lab_stock", "The stock is taken inside the same transaction as the order row."),
        (66, "if (failAfterReserve)", "An exception here rolls back EVERYTHING, including the stock already taken."),
    ]),
    ("micro-order-place", "Order service: several synchronous calls, no shared transaction", J + "lab/micro/order/OrderService.java", 91, 144, [
        (110, "insertOrder", "The order row is committed in the Order service's own local transaction, and it keeps a COPY of the unit price it was quoted."),
        (113, "CallResult<Reservation> reserve", "A synchronous call: Inventory commits its own transaction; no transaction spans the two services."),
        (118, "outcome() != Outcome.TIMEOUT", "After a timeout the order stays PENDING: this service cannot know whether the stock was reserved."),
        (127, "Injection.AFTER_RESERVE", "Lab fault injection: failing here leaves the reservation in place."),
        (141, "events.publish", "The event is published AFTER the commit, outside the transaction. This is not a transactional outbox."),
    ]),
    ("micro-event-channel", "The SIMULATED event channel: publish, then deliver as a separate step", J + "lab/micro/core/EventChannel.java", 59, 100, [
        (59, "public EventMessage publish", "Appends to an in-memory list. The sender does not wait for any consumer. Not Kafka or RabbitMQ."),
        (84, "public List<Attempt> deliverPending", "Delivery is its own step, made through the service bus and recorded as an EVENT call."),
    ]),
    ("micro-idempotent-consumer", "Idempotent consumer: the message id and the effect in one local transaction", J + "lab/micro/notification/NotificationService.java", 55, 65, [
        (57, "alreadyProcessed", "A message id handled before is ignored, so a duplicate delivery changes nothing."),
        (60, "markProcessed", "The message id and the notification are written in ONE local transaction, so they cannot diverge."),
    ]),
    ("micro-gateway-route", "The simulated gateway: authenticate, limit, route, translate", J + "lab/micro/gateway/GatewayService.java", 74, 107, [
        (75, "Credential.MISSING", "Authentication comes first; a rejected request reaches no service."),
        (107, "NO_ROUTE", "A path with no route is answered by the gateway itself."),
    ]),
    ("micro-rate-limiter", "Fixed-window rate limiter on the simulated clock", J + "lab/micro/gateway/GatewayService.java", 82, 92, [
        (84, "now - window[0] >= GatewayContract.WINDOW_MILLIS", "A new window starts once the simulated clock passes the end of the old one."),
        (88, "window[1] >= limiter.limit", "Over the limit: 429, without touching any service."),
        (91, "window[1]++", "Counted per client id."),
    ]),
    ("micro-registry-resolve", "Registry resolve: healthy instances only, round-robin", J + "lab/micro/registry/RegistryService.java", 94, 108, [
        (98, "UNKNOWN_SERVICE", "A name nobody registered is a business refusal, not a connection error."),
        (100, "isHealthy(r, now)", "Only instances that reported UP and whose heartbeat is fresh are candidates."),
        (102, "NO_HEALTHY_INSTANCE", "Every instance is unhealthy: the caller must handle this."),
        (105, "positions.merge", "The round-robin position, kept per run."),
    ]),
    ("micro-instance-behaviour", "Behaviour of ONE instance", J + "lab/micro/core/ServiceBehaviours.java", 84, 90, [
        (86, "instanceBehaviours.containsKey", "A behaviour set for one instance overrides the service-level one for calls aimed at that instance: how a single crashed instance is modelled."),
    ]),
    # ---- Stage B3 ----
    ("micro-ownership-guard", "The runtime ownership guard", J + "lab/micro/run/OwnershipGuardedDataSource.java", 70, 80, [
        (74, "ownedTables().contains(table)", "A statement that mentions a table the service does not own is refused before it reaches the driver. This is logical isolation enforced by lab code."),
    ]),
    ("micro-outbox-write", "Transactional outbox: the order and its event row in ONE local transaction", J + "lab/micro/order/OrderService.java", 207, 217, [
        (210, "repository.insertOutbox", "The outbox row is written in the SAME local transaction as the order row."),
        (212, "injected failure before the transaction", "An exception here rolls back BOTH rows: this is a real database rollback, inside one local transaction."),
    ]),
    ("micro-outbox-relay", "The outbox relay: publish, then mark, as separate steps", J + "lab/micro/order/OrderService.java", 236, 250, [
        (240, "events.publishWithId", "Publishes with the SAME message id as the outbox row, so a republication is recognisable as a duplicate."),
        (243, "injected crash after publishing", "A crash here leaves the row PENDING, so the next run publishes it again: delivery is at-least-once."),
        (246, "markOutboxPublished", "Marking the row PUBLISHED is a separate step after publishing."),
    ]),
    ("micro-saga-orchestrator", "Saga compensation, in reverse order", J + "lab/micro/order/SagaOrchestrator.java", 126, 135, [
        (128, "completed.size() - 1", "The compensations of the completed steps run in reverse order."),
        (133, "reversed by a new business operation", "A compensation is a new business operation (a release, a refund). It is not a database rollback."),
    ]),
    ("micro-saga-choreography", "Choreography: Inventory reacts to PaymentFailed", J + "lab/micro/inventory/InventoryService.java", 140, 148, [
        (141, "release(context, new Release", "Nothing coordinates this: Inventory reacts to an event by running its own compensation."),
        (145, "events.publish", "It then publishes StockReleased for the Order service to react to."),
    ]),
    ("micro-circuit-breaker", "Circuit breaker: when OPEN becomes HALF_OPEN", J + "lab/micro/core/CircuitBreaker.java", 61, 72, [
        (62, "openedAt >= openMillis", "The breaker leaves OPEN exactly when the open duration has elapsed; the next call is the probe."),
    ]),
    ("micro-retry", "Bounded retries with deterministic backoff", J + "lab/micro/core/ResilientCaller.java", 39, 52, [
        (39, "for (int number", "At most maxAttempts calls: retries are bounded."),
        (43, "bus.pause(backoff)", "The wait is spent on the simulated clock, never by sleeping, and counts as time of the enclosing span."),
        (45, "breaker.allowRequest()", "An open breaker refuses the call locally and it is not retried."),
    ]),
    ("micro-idempotent-charge", "Idempotent charge: a repeated key returns the original payment", J + "lab/micro/payment/PaymentService.java", 69, 90, [
        (76, "findByKey", "A request with a key that was already used returns the original payment instead of charging again."),
        (83, "DuplicateKeyException race", "Two requests with the same key arriving together: the unique key lets exactly one insert through."),
    ]),
    ("micro-correlation", "Correlation id and parent span", J + "lab/micro/core/ServiceBus.java", 170, 179, [
        (174, "correlation = parent.correlationId", "A child call inherits the correlation id of its parent: this is the propagation."),
        (176, "uncorrelated-", "When propagation is switched off the child starts an unrelated id and loses its parent link: the trace is broken."),
    ]),
    ("micro-registry-ttl", "The explicit expiry rule of the registry", J + "lab/micro/contract/RegistryContract.java", 26, 28, [
        (27, "ageMillis <= TTL_MILLIS", "Fresh while the age is at most the TTL: healthy at exactly the TTL, stale from TTL + 1 ms."),
    ]),
]


def main() -> int:
    out = []
    for rid, title, rel, start, end, notes in REFS:
        lines = (BACKEND / rel).read_text(encoding="utf-8").splitlines()
        assert 1 <= start <= end <= len(lines), (rid, "range outside file", start, end, len(lines))
        note_list = []
        for line_no, token, text in notes:
            assert start <= line_no <= end, (rid, "note outside range", line_no)
            assert token in lines[line_no - 1], (rid, "token not on line", line_no, token, lines[line_no - 1])
            note_list.append({"line": line_no, "token": token, "text": text})
        out.append({"id": rid, "title": title, "file": rel, "startLine": start, "endLine": end,
                    "language": "yaml" if rel.endswith(".yml") else "java",
                    "excerpt": "\n".join(lines[start - 1:end]), "notes": note_list})
    target = BACKEND / "src" / "main" / "resources" / "lab" / "code-references.json"
    with open(target, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(out, fh, indent=2, ensure_ascii=False)
        fh.write("\n")
    print(f"wrote {len(out)} references -> {target}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
