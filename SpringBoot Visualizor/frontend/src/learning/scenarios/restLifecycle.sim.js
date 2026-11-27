/**
 * REST Request Lifecycle: the module's fixed diagram, its mandatory 5W1H, and the SIMULATED walkthrough.
 *
 * Every step here is CONCEPTUAL by definition: it explains how Spring Boot, Spring Security and JPA work and shows
 * real SpringLab code for the matching layer, but it reports nothing that was observed. It deliberately contains no
 * SQL text; the actual statements are only ever shown by the LIVE mode, from a real request.
 */

import { cell } from '../schema/layout'

export const MODULE = Object.freeze({
  id: 'rest-lifecycle',
  title: 'REST Request Lifecycle',
  topicSlug: 'rest-lifecycle', // Challenge Arena topic used by "Test yourself"
  summary: 'Follow one HTTP request from the browser through Spring Security, Spring MVC, the service layer, Spring Data and Hibernate, and back.',
})

/** The diagram is the same in SIMULATED and LIVE mode; steps point at these ids. */
export const NODES = [
  { id: 'client', label: 'Browser / client', sublabel: 'sends the HTTP request', description: 'The React app (or any HTTP client) sends the request with an Authorization: Bearer header.' },
  { id: 'securityFilters', label: 'Security filter chain', sublabel: 'FilterChainProxy', description: 'Spring Security filters run before DispatcherServlet: bearer token extraction, JWT validation, authorization.' },
  { id: 'dispatcher', label: 'DispatcherServlet', sublabel: 'Spring MVC front controller', description: 'Chooses the handler (HandlerMapping), invokes it (HandlerAdapter) and writes the response (HttpMessageConverter).' },
  { id: 'controller', label: 'Controller', sublabel: '@RestController', description: 'Web layer. Binds the request to Java arguments and delegates to a service.' },
  { id: 'service', label: 'Service', sublabel: '@Service, @Transactional', description: 'Business logic and the transaction boundary.' },
  { id: 'repository', label: 'Repository', sublabel: 'Spring Data JPA', description: 'An interface; Spring Data generates the implementation as a proxy at startup.' },
  { id: 'hibernate', label: 'Hibernate / JPA', sublabel: 'EntityManager', description: 'Translates JPQL and entity operations to SQL and manages the persistence context.' },
  { id: 'database', label: 'MySQL', sublabel: 'the database', description: 'Executes the SQL that Hibernate sends through JDBC.' },
]

/** Edge ids are always "source->target" in REQUEST direction; a step's direction says which way data moves. */
export const EDGES = [
  { id: 'client->securityFilters', source: 'client', target: 'securityFilters' },
  { id: 'securityFilters->dispatcher', source: 'securityFilters', target: 'dispatcher' },
  { id: 'securityFilters->repository', source: 'securityFilters', target: 'repository', secondary: true },
  { id: 'dispatcher->controller', source: 'dispatcher', target: 'controller' },
  { id: 'controller->service', source: 'controller', target: 'service' },
  { id: 'service->repository', source: 'service', target: 'repository' },
  { id: 'repository->hibernate', source: 'repository', target: 'hibernate' },
  { id: 'hibernate->database', source: 'hibernate', target: 'database' },
]

// Headroom above the first row for the 401/403 return arc.
const TOP = 70

/**
 * Where each node sits. The request descends like a staircase: every arrow points right or down, never back, so the
 * execution order reads left to right and top to bottom (web tier, then application code, then persistence, then the
 * database). The ten conceptual stages of a request map onto the eight nodes as numbered below: the servlet container
 * (2) is the "Embedded Tomcat" layer around the security filter chain (3), and HandlerMapping / HandlerAdapter (5) are
 * the two parts of DispatcherServlet's work (4). Compact (phone) is one column in the same order.
 */
export const LAYOUT = {
  wide: {
    positions: {
      client: cell(0, 0, TOP),
      securityFilters: cell(1, 0, TOP),
      dispatcher: cell(2, 0, TOP),
      controller: cell(2, 1, TOP),
      service: cell(3, 1, TOP),
      repository: cell(3, 2, TOP),
      hibernate: cell(4, 2, TOP),
      database: cell(5, 2, TOP),
    },
    handles: {
      'client->securityFilters': ['r-s', 'l-t'],
      'securityFilters->dispatcher': ['r-s', 'l-t'],
      'dispatcher->controller': ['b-s', 't-t'],
      'controller->service': ['r-s', 'l-t'],
      'service->repository': ['b-s', 't-t'],
      'repository->hibernate': ['r-s', 'l-t'],
      'hibernate->database': ['r-s', 'l-t'],
      'securityFilters->repository': ['b-s', 'l-t'],
    },
  },
  compact: {
    order: ['client', 'securityFilters', 'dispatcher', 'controller', 'service', 'repository', 'hibernate', 'database'],
    handles: { 'securityFilters->repository': ['r-s', 'r-t'] },
  },
  heights: { wide: 450 },
  fullWidth: true,
  minWidth: 900,
  nodeMeta: {
    client: { kind: 'client', stage: 1 },
    securityFilters: { kind: 'security', stage: '2–3' },
    dispatcher: { kind: 'mvc', stage: '4–5', chips: ['HandlerMapping', 'HandlerAdapter'] },
    controller: { kind: 'mvc', stage: 6 },
    service: { kind: 'business', stage: 7 },
    repository: { kind: 'persistence', stage: 8 },
    hibernate: { kind: 'persistence', stage: 9 },
    database: { kind: 'database', stage: 10 },
  },
  lanes: [
    { id: 'browser', label: 'Browser', kind: 'client', nodes: ['client'] },
    { id: 'tomcat', label: 'Embedded Tomcat', caption: 'servlet container', kind: 'mvc', nodes: ['securityFilters', 'dispatcher'] },
    { id: 'application', label: 'SpringLab code', caption: 'web, then business layer', kind: 'business', nodes: ['controller', 'service'] },
    { id: 'persistence', label: 'Persistence', kind: 'persistence', nodes: ['repository', 'hibernate'] },
    { id: 'database', label: 'Database', kind: 'database', nodes: ['database'] },
  ],
  edgeMeta: {
    'securityFilters->repository': { label: 'role lookup', tone: 'lookup' },
  },
  // Not every request goes all the way down: a missing or invalid token (401) or a missing role (403) is answered by the
  // security filter chain and the request never reaches the controller. This arrow is that alternative path.
  branches: [
    {
      id: 'securityFilters->client',
      source: 'securityFilters',
      target: 'client',
      label: '401 / 403: ends here',
      tone: 'error',
      handles: ['t-s', 't-t'],
      compactHandles: ['l-s', 'l-t'],
      offset: 44,
      stepIds: ['rejected-before-controller', 'rejection-mechanism', 'authorization'],
    },
  ],
  legend: { position: 'bottom-left' },
}

export const EXPLAIN = {
  what: 'The request lifecycle is the path an HTTP request takes through a Spring Boot application: from the embedded servlet container, through Spring Security\'s filters and Spring MVC\'s DispatcherServlet, into a controller, a service and a repository, and back out as an HTTP response.',
  why: 'Knowing the order explains behaviour that otherwise looks like magic: why a missing or invalid token is answered with 401 before any controller runs, why a 403 can still cost a database query, where exceptions become HTTP errors, and which layer owns transactions and SQL.',
  who: [
    { name: 'Embedded Tomcat (servlet container)', role: 'accepts the HTTP request and runs the servlet filters, then DispatcherServlet' },
    { name: 'FilterChainProxy / SecurityFilterChain', role: 'Spring Security\'s entry point; applies the filters configured for the URL' },
    { name: 'BearerTokenAuthenticationFilter and JwtDecoder', role: 'extract the Bearer token and verify its signature, expiry and issuer' },
    { name: 'DatabaseBackedJwtAuthenticationConverter (SpringLab)', role: 'reads the caller\'s current role and enabled flag from the database' },
    { name: 'AuthorizationFilter, ExceptionTranslationFilter', role: 'apply the access rules and turn failures into 401 or 403 responses' },
    { name: 'DispatcherServlet, HandlerMapping, HandlerAdapter', role: 'select the controller method, invoke it and handle its result or exception' },
    { name: '@RestController, @Service, Spring Data repository', role: 'the web, business and persistence layers of SpringLab' },
    { name: 'Hibernate (EntityManager) and MySQL', role: 'turn repository calls into SQL and execute it' },
    { name: 'HttpMessageConverter (Jackson) and @RestControllerAdvice', role: 'write DTOs as JSON and translate exceptions to ProblemDetail responses' },
    { name: 'The caller (STUDENT, TEACHER or ADMIN)', role: 'their role, read from the users table, decides which endpoints they may call' },
  ],
  where: 'Across SpringLab\'s layered monolith: servlet container, security filter chain, web layer (controllers), service layer, persistence layer (Spring Data and Hibernate), database. The exception handler and the message converters are cross-cutting pieces on the way back.',
  when: 'At runtime, once for every HTTP request: filters run first, then handler selection, controller, service, repository and database, then the response is written and travels back through the filters. Beans, proxies and the filter chain themselves are created once at application startup, not per request.',
  how: [
    'The browser sends GET /api/... with an Authorization: Bearer header and Tomcat starts the filter chain.',
    'Spring Security\'s filters validate the JWT (signature, expiry, issuer) with a JwtDecoder.',
    'SpringLab\'s converter loads the caller\'s current role and enabled flag from the database and builds the Authentication.',
    'AuthorizationFilter applies the URL rules; ExceptionTranslationFilter turns a failure into 401 or 403 and the request ends here.',
    'DispatcherServlet asks its HandlerMapping for the matching @RequestMapping method and a HandlerAdapter invokes it.',
    'The controller delegates to a @Service, which declares the transaction boundary with @Transactional.',
    'The service calls a Spring Data repository; Hibernate turns the call into SQL and sends it to MySQL through JDBC.',
    'The result is mapped to a DTO, an HttpMessageConverter writes it as JSON, and the response returns through the filters; an exception is translated by the @RestControllerAdvice instead.',
  ],
}

/** Assumptions that do not change at runtime. Versions and settings come live from GET /api/lab/meta. */
export const STATIC_ASSUMPTIONS = [
  'SpringLab is a single Spring Boot application on the servlet stack (Spring MVC with an embedded Tomcat), not WebFlux.',
  'Authentication uses spring-boot-starter-oauth2-resource-server with a symmetric HS256 JWT; authorities are read from the database on every authenticated request, not from the token.',
  'Controllers return DTOs, never entities; JSON is written by Jackson through an HttpMessageConverter.',
  'The security filter chain shown is the one configured for the requested URL; it is read from FilterChainProxy, not inferred.',
  'Captured SQL is the text Hibernate hands to JDBC, with ? placeholders. It does not show bound values, result rows, or how MySQL executed the statement.',
  'The LIVE mode traces real GET requests to a fixed list of existing endpoints. It cannot change any application setting.',
]

/** Key points for the Explain stage. Each one is checked by a backend test (see the module notes). */
export const TAKEAWAYS = [
  { text: 'A request without a valid token is answered inside the security filter chain: no controller, service or repository runs and no SQL is prepared.', evidenceExperiments: ['no-token', 'tampered-token'] },
  { text: 'Authentication costs a database round trip: the caller\'s role is read on every authenticated request, so even a 403 prepares one SQL statement.', evidenceExperiments: ['insufficient-role'] },
  { text: 'Business errors reach application code: the controller and service run, the repository is queried, and the exception is translated into a 404 ProblemDetail.', evidenceExperiments: ['missing-resource'] },
  { text: 'Controllers delegate; the service owns the transaction boundary; repositories are proxies generated by Spring Data.', evidenceExperiments: [] },
  { text: 'SQL text comes from Hibernate, not from the database: it shows what was prepared, never what MySQL returned.', evidenceExperiments: [] },
]

const sim = (id, title, nodes, edgeFrom, edgeTo, direction, explanation, codeRefId) => ({
  id,
  title,
  nodes,
  edgeFrom,
  edgeTo,
  direction,
  provenance: 'CONCEPTUAL',
  explanation,
  evidence: [],
  sql: [],
  codeRefId: codeRefId ?? null,
})

export const SIMULATED_STEPS = [
  sim('client-request', 'The client sends the HTTP request', ['client'], 'client', 'securityFilters', 'REQUEST',
    'A browser or API client sends, for example, GET /api/topics with an Authorization: Bearer header. SpringLab\'s React app does this through Axios.'),
  sim('security-filter-chain', 'Spring Security filter chain', ['securityFilters'], null, null, 'REQUEST',
    'Before DispatcherServlet sees the request it passes through the SecurityFilterChain, entered through FilterChainProxy. With oauth2ResourceServer().jwt(), BearerTokenAuthenticationFilter extracts the Bearer token and a JwtDecoder verifies the signature, expiry and issuer.',
    'security-filter-chain'),
  sim('auth-lookup', 'Authentication: the caller\'s role comes from the database', ['securityFilters', 'repository'], 'securityFilters', 'repository', 'REQUEST',
    'After the token is valid, SpringLab\'s converter reads the user\'s current role and enabled flag from the database and builds the Authentication. The token only proves identity; this is why a role change or a disabled account applies on the very next request.',
    'auth-converter'),
  sim('authorization', 'Authorization decision', ['securityFilters'], null, null, 'REQUEST',
    'AuthorizationFilter compares the caller\'s authorities with the rules, for example /api/admin/** requires ROLE_ADMIN. If the caller is not authenticated, ExceptionTranslationFilter starts the AuthenticationEntryPoint (401); if authenticated but not allowed, the AccessDeniedHandler runs (403). Either way the request ends here.',
    'security-filter-chain'),
  sim('dispatch', 'DispatcherServlet selects the handler', ['dispatcher'], 'securityFilters', 'dispatcher', 'REQUEST',
    'DispatcherServlet is Spring MVC\'s front controller. It asks its HandlerMapping which handler matches the request, here a @RestController method chosen by @GetMapping, and prepares to call it through a HandlerAdapter.'),
  sim('controller', 'The controller method runs', ['controller'], 'dispatcher', 'controller', 'REQUEST',
    'The HandlerAdapter resolves the method arguments: path variables are converted, @AuthenticationPrincipal is injected and @Valid @RequestBody is validated. The controller stays thin and delegates to a service.',
    'topic-controller'),
  sim('service', 'The service layer', ['service'], 'controller', 'service', 'REQUEST',
    'The service holds the business logic and the transaction boundary. @Transactional is applied by a Spring proxy around the method: the proxy starts the transaction before the method and commits or rolls back after it.',
    'topic-service'),
  sim('repository', 'The Spring Data repository', ['repository'], 'service', 'repository', 'REQUEST',
    'The service calls a repository interface. Spring Data generates the implementation at startup as a proxy; query methods are built from the method name or from an @Query declaration.',
    'topic-repository'),
  sim('hibernate', 'Hibernate and the EntityManager', ['hibernate'], 'repository', 'hibernate', 'REQUEST',
    'The repository runs the query through JPA\'s EntityManager, implemented by Hibernate. Hibernate translates JPQL to SQL for the MySQL dialect, binds parameters and manages the persistence context. This simulated walkthrough shows no SQL on purpose: the real statements are only shown by the LIVE mode.'),
  sim('database', 'MySQL executes the statement', ['database'], 'hibernate', 'database', 'REQUEST',
    'Hibernate sends the prepared statement to MySQL through JDBC, binds the parameters and reads the result set.'),
  sim('serialization', 'The response body is written', ['dispatcher'], 'dispatcher', 'controller', 'RESPONSE',
    'The service maps the result to a DTO and the controller returns it. An HttpMessageConverter, Jackson for JSON, writes the DTO to the response body. If an exception was thrown instead, the exception resolvers delegate to the @RestControllerAdvice, which produces a ProblemDetail.',
    'not-found-handler'),
  sim('response', 'The response returns to the client', ['client'], 'client', 'securityFilters', 'RESPONSE',
    'The response travels back out through the filter chain to the client, which sees the status code, headers and body.'),
]

export const SIMULATED_SCENARIO = {
  id: 'rest-lifecycle-simulated',
  title: 'A request through SpringLab (conceptual walkthrough)',
  mode: 'SIMULATED',
  nodes: NODES,
  edges: EDGES,
  layout: LAYOUT,
  steps: SIMULATED_STEPS,
}
