-- Seed question bank (authored by SpringLab: created_by = NULL). Every question has exactly one correct option.
-- Assumes Java 17+, Spring Boot 3.x (Spring Framework 6, Spring Security 6, Jakarta Persistence 3, Hibernate 6).
-- Pattern per question: insert the question, capture its id, insert its options.

-- ===================================================== Java Core =====================================================
INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'EASY', 'What does the expression "abc" == new String("abc") evaluate to in Java?',
 'The == operator compares object references, not contents. new String("abc") always creates a new object, which is a different reference from the pooled string literal "abc", so the result is false. Use equals() to compare the characters.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'java-core';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'true', FALSE, 0), (@q, 'false', TRUE, 1), (@q, 'It does not compile', FALSE, 2), (@q, 'It throws an exception at runtime', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'Which statement about java.util.HashMap is correct?',
 'HashMap allows one null key and any number of null values. It is not synchronized (use ConcurrentHashMap for concurrent access) and it does not guarantee any iteration order (use LinkedHashMap to keep insertion order).',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'java-core';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'It is synchronized by default', FALSE, 0), (@q, 'It preserves insertion order', FALSE, 1),
 (@q, 'It permits one null key and multiple null values', TRUE, 2), (@q, 'It does not permit null values', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'What happens when you call add() on the list returned by List.of(1, 2, 3)?',
 'List.of() returns an unmodifiable list. Any structural modification, including add(), throws UnsupportedOperationException. Copy it into a new ArrayList first if you need a mutable list.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'java-core';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'The element is added and the list grows', FALSE, 0), (@q, 'UnsupportedOperationException is thrown', TRUE, 1),
 (@q, 'ConcurrentModificationException is thrown', FALSE, 2), (@q, 'add() silently returns false', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'HARD', 'Given Integer a = 127, b = 127, c = 128, d = 128; what do a == b and c == d evaluate to with default JVM settings?',
 'Autoboxing uses Integer.valueOf(), which the Java specification requires to cache instances for values from -128 to 127. So a and b are the same object (true), while 128 is outside the guaranteed cache range and c and d are separate objects (false). Never compare boxed values with ==.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'java-core';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'true and true', FALSE, 0), (@q, 'true and false', TRUE, 1), (@q, 'false and false', FALSE, 2), (@q, 'false and true', FALSE, 3);

-- ============================================== Spring Boot Basics ===================================================
INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'EASY', 'Which annotation on a main class is equivalent to declaring @SpringBootConfiguration, @EnableAutoConfiguration and @ComponentScan together?',
 '@SpringBootApplication is a convenience annotation that is meta-annotated with those three. @ComponentScan scans the package of the annotated class and its sub-packages, which is why the main class usually sits in the root package.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'spring-boot-basics';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, '@SpringBootApplication', TRUE, 0), (@q, '@EnableWebMvc', FALSE, 1), (@q, '@Component', FALSE, 2), (@q, '@Configuration', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'Why is constructor injection generally preferred over field injection?',
 'With constructor injection the dependencies can be final, are guaranteed to be present when the object exists, and the class can be created in a plain unit test with new, without a Spring container or reflection. Field injection is still supported in Spring Boot 3, but it hides dependencies and prevents final fields.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'spring-boot-basics';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'Field injection was removed in Spring Boot 3', FALSE, 0),
 (@q, 'Dependencies can be final and required, and the class is easy to instantiate in unit tests without Spring', TRUE, 1),
 (@q, 'Constructor injection makes a bean a singleton', FALSE, 2),
 (@q, 'Only constructor injection works with @Autowired', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'EASY', 'Which scope does Spring give a bean when no scope is specified?',
 'The default scope is singleton: one shared instance per ApplicationContext. Prototype creates a new instance on every request for the bean; request and session are web-aware scopes that must be requested explicitly.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'spring-boot-basics';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'prototype', FALSE, 0), (@q, 'request', FALSE, 1), (@q, 'singleton', TRUE, 2), (@q, 'session', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'The same property is defined in an application.yml packaged inside the jar and as an operating-system environment variable. Which value does Spring Boot use?',
 'Spring Boot applies a defined order for externalized configuration. OS environment variables rank above application.properties/yml files packaged inside the jar, so the environment variable wins. This is why secrets and per-environment settings are normally supplied through the environment.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'spring-boot-basics';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'The value packaged in the jar', FALSE, 0), (@q, 'The operating-system environment variable', TRUE, 1),
 (@q, 'Whichever was defined first alphabetically', FALSE, 2), (@q, 'Startup fails because the property is defined twice', FALSE, 3);

-- ============================================== REST Request Lifecycle ===============================================
INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'EASY', 'In Spring MVC, which component acts as the front controller that receives each request and dispatches it to a handler?',
 'DispatcherServlet is the front controller. It uses HandlerMapping to find the handler (for example a @RequestMapping method), a HandlerAdapter to invoke it, and then handles the result, for REST controllers by writing the response body through an HttpMessageConverter.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'rest-lifecycle';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'DispatcherServlet', TRUE, 0), (@q, 'EntityManager', FALSE, 1), (@q, 'ApplicationContext', FALSE, 2), (@q, 'ViewResolver', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'What does the @RestController annotation combine?',
 '@RestController is a convenience annotation meta-annotated with @Controller and @ResponseBody. @ResponseBody means the return value is written to the HTTP response body (for example as JSON) instead of being interpreted as a view name.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'rest-lifecycle';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, '@Controller and @ResponseBody', TRUE, 0), (@q, '@Component and @RequestMapping', FALSE, 1),
 (@q, '@Service and @ResponseStatus', FALSE, 2), (@q, '@Controller and @Transactional', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'A controller method has a parameter annotated @Valid @RequestBody and the request body fails Bean Validation. What happens by default?',
 'When validation of a @RequestBody argument fails and there is no BindingResult parameter, Spring MVC throws MethodArgumentNotValidException before the method body runs. By default it is resolved to HTTP 400 (Bad Request); an @ExceptionHandler or ResponseEntityExceptionHandler can customize the response body.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'rest-lifecycle';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'The method runs and the violations are ignored', FALSE, 0),
 (@q, 'MethodArgumentNotValidException is thrown and Spring MVC answers 400 by default', TRUE, 1),
 (@q, 'A NullPointerException causes a 500 response', FALSE, 2), (@q, 'The server answers 404 Not Found', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'HARD', 'Which Spring MVC abstraction converts a Java object returned from a @RestController method into the JSON response body?',
 'An HttpMessageConverter (with Jackson on the classpath, typically MappingJackson2HttpMessageConverter) writes the returned object to the response body, chosen by content negotiation against the Accept header. HandlerMapping only selects the handler and a ViewResolver is for view-based rendering.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'rest-lifecycle';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'HandlerMapping', FALSE, 0), (@q, 'ViewResolver', FALSE, 1), (@q, 'HttpMessageConverter', TRUE, 2), (@q, 'ServletContextListener', FALSE, 3);

-- ============================================== JWT and Spring Security ==============================================
INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'EASY', 'What are the three parts of a signed JWT in its compact form, in order?',
 'A signed JWT (JWS compact serialization) is header.payload.signature, each part Base64URL-encoded and separated by dots. The header names the algorithm, the payload carries the claims, and the signature protects both.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jwt-security';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'Header, payload (claims), signature', TRUE, 0), (@q, 'Payload, header, signature', FALSE, 1),
 (@q, 'Username, password, expiry', FALSE, 2), (@q, 'Key, algorithm, claims', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'Is the payload of a normal signed JWT (JWS) encrypted?',
 'No. A JWS payload is only Base64URL-encoded, so anyone who holds the token can read the claims. The signature only guarantees integrity and authenticity. Do not put secrets in a JWT unless you use JWE (encrypted JWTs).',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jwt-security';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'Yes, the signature encrypts the payload', FALSE, 0),
 (@q, 'No, it is only encoded and signed, so anyone with the token can read it', TRUE, 1),
 (@q, 'Yes, but only when the HS256 algorithm is used', FALSE, 2),
 (@q, 'No, it is not even Base64-encoded', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'A request carries a valid token for a user who does not have the role required by the endpoint. Which HTTP status is appropriate?',
 '401 Unauthorized means the request is not authenticated (missing or invalid credentials). 403 Forbidden means the caller is authenticated but not allowed. In Spring Security the AuthenticationEntryPoint produces 401 and the AccessDeniedHandler produces 403.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jwt-security';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, '401 Unauthorized', FALSE, 0), (@q, '403 Forbidden', TRUE, 1), (@q, '404 Not Found', FALSE, 2), (@q, '400 Bad Request', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'HARD', 'Which key does a server use to verify the signature of a JWT signed with HS256?',
 'HS256 is HMAC with SHA-256, a symmetric algorithm: the same shared secret signs and verifies. Asymmetric algorithms such as RS256 sign with a private key and verify with the public key. The secret must be at least 256 bits and must never be exposed to clients.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jwt-security';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'The public key matching a private signing key', FALSE, 0), (@q, 'The same shared secret that was used to sign it', TRUE, 1),
 (@q, 'The password of the user in the subject claim', FALSE, 2), (@q, 'No key; the signature is only a checksum', FALSE, 3);

-- ================================================== JPA and Hibernate ================================================
INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'EASY', 'In a Spring Boot 3 application, which annotation marks a class as a JPA entity?',
 'Spring Boot 3 uses Jakarta Persistence 3, so the annotation is jakarta.persistence.Entity. The javax.persistence package was renamed to jakarta.persistence with Jakarta EE 9; javax.persistence annotations are not recognized by Hibernate 6.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jpa-hibernate';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, '@jakarta.persistence.Entity', TRUE, 0), (@q, '@javax.persistence.Entity', FALSE, 1),
 (@q, '@org.springframework.data.Entity', FALSE, 2), (@q, '@org.springframework.stereotype.Repository', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'In a bidirectional @OneToMany / @ManyToOne relationship, which side owns the relationship and holds the foreign key column?',
 'The @ManyToOne side is the owning side and maps the foreign key column (the many side holds the foreign key). The @OneToMany side is the inverse side and uses mappedBy to point at the owning field. Hibernate only writes the foreign key from the owning side.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jpa-hibernate';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'The @OneToMany side', FALSE, 0), (@q, 'The @ManyToOne side', TRUE, 1),
 (@q, 'Both sides equally', FALSE, 2), (@q, 'Neither; a join table is always required', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'What is the default fetch type of a @ManyToOne association according to the JPA specification?',
 'The JPA default for @ManyToOne and @OneToOne is EAGER, while @OneToMany and @ManyToMany default to LAZY. Many teams explicitly set fetch = FetchType.LAZY on @ManyToOne to avoid unexpected extra queries.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jpa-hibernate';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'LAZY', FALSE, 0), (@q, 'EAGER', TRUE, 1), (@q, 'It depends on the database', FALSE, 2), (@q, 'There is no default; it must be specified', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'HARD', 'With spring.jpa.open-in-view=false, what happens when a controller reads an uninitialized lazy collection of an entity that a finished service transaction returned?',
 'Without Open Session In View the persistence context is closed when the service transaction ends. Touching an uninitialized lazy collection afterwards throws LazyInitializationException. Fetch what you need inside the transaction (join fetch, entity graph) or map to a DTO there.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jpa-hibernate';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'Hibernate silently runs the query in a new session', FALSE, 0), (@q, 'LazyInitializationException is thrown', TRUE, 1),
 (@q, 'The collection is returned empty', FALSE, 2), (@q, 'The collection is fetched in the same query automatically', FALSE, 3);
