/**
 * JWT Security Simulator: the module's fixed diagram, its mandatory 5W1H, the SIMULATED walkthrough and the glossary of
 * Spring Security components. Every step here is CONCEPTUAL by definition: it explains how Spring Security works and
 * shows real SpringLab code for the matching part, but reports nothing that was observed. The LIVE mode (real requests
 * to the lab endpoints) supplies the observed steps.
 */

import { cell } from '../schema/layout'

export const MODULE = Object.freeze({
  id: 'jwt-security',
  title: 'JWT Security Simulator',
  topicSlug: 'jwt-security', // Challenge Arena topic used by "Test yourself"
  summary:
    'Take a JWT apart, break it in every way that matters, and watch SpringLab\'s real security pipeline answer 401 or 403 and say why.',
})

export const NODES = [
  { id: 'client', label: 'Browser / client', sublabel: 'sends Authorization: Bearer <jwt>', description: 'Presents the token on every request.' },
  { id: 'bearerFilter', label: 'BearerTokenAuthenticationFilter', sublabel: 'extracts the bearer token', description: 'Reads the Authorization header and starts authentication.' },
  { id: 'authManager', label: 'AuthenticationManager', sublabel: 'delegates to a provider', description: 'ProviderManager hands the token to the JwtAuthenticationProvider, the provider that understands JWTs.' },
  { id: 'jwtDecoder', label: 'JwtDecoder', sublabel: 'NimbusJwtDecoder: parse, verify, validate', description: 'Checks structure, algorithm, signature, exp, nbf and iss.' },
  { id: 'converter', label: 'Converter (SpringLab)', sublabel: 'DatabaseBackedJwtAuthenticationConverter', description: 'Builds the Authentication; authorities come from the database.' },
  { id: 'database', label: 'users table', sublabel: 'role and enabled flag', description: 'The authoritative source of the caller\'s role and status.' },
  { id: 'securityContext', label: 'SecurityContext', sublabel: 'Authentication for this request', description: 'Thread-local holder; cleared at the end of the request.' },
  { id: 'authorization', label: 'AuthorizationFilter', sublabel: 'access rules (hasRole ...)', description: 'Compares the caller\'s authorities with the rule for the path.' },
  { id: 'controller', label: 'Controller', sublabel: 'runs only if allowed', description: 'Reached only after authentication and authorization succeed.' },
  { id: 'entryPoint', label: 'AuthenticationEntryPoint', sublabel: 'answers 401', description: 'Writes the "not authenticated" response.' },
  { id: 'accessDenied', label: 'AccessDeniedHandler', sublabel: 'answers 403', description: 'Writes the "authenticated but not allowed" response.' },
]

export const EDGES = [
  { id: 'client->bearerFilter', source: 'client', target: 'bearerFilter' },
  { id: 'bearerFilter->authManager', source: 'bearerFilter', target: 'authManager' },
  { id: 'authManager->jwtDecoder', source: 'authManager', target: 'jwtDecoder' },
  { id: 'jwtDecoder->converter', source: 'jwtDecoder', target: 'converter' },
  { id: 'converter->database', source: 'converter', target: 'database' },
  { id: 'converter->securityContext', source: 'converter', target: 'securityContext' },
  { id: 'securityContext->authorization', source: 'securityContext', target: 'authorization' },
  { id: 'authorization->controller', source: 'authorization', target: 'controller' },
  { id: 'authorization->accessDenied', source: 'authorization', target: 'accessDenied', secondary: true },
  { id: 'authorization->entryPoint', source: 'authorization', target: 'entryPoint', secondary: true },
]

/**
 * Authentication (is the token genuine?) happens in the first two layers, authorization (may this caller do this?) in
 * the third, and the two refusal handlers sit in their own layer under the rule that triggers them. Like the REST
 * diagram it descends as a staircase: every success arrow points right or down. Only bearer requests are drawn here:
 * the login pipeline (UserDetailsService, PasswordEncoder, DaoAuthenticationProvider) is a different path.
 */
export const LAYOUT = {
  wide: {
    positions: {
      client: cell(0, 0),
      bearerFilter: cell(1, 0),
      authManager: cell(1, 1),
      jwtDecoder: cell(2, 1),
      converter: cell(3, 1),
      database: cell(4, 1),
      securityContext: cell(3, 2),
      authorization: cell(4, 2),
      controller: cell(5, 2),
      entryPoint: cell(3, 3),
      accessDenied: cell(4, 3),
    },
    handles: {
      'client->bearerFilter': ['r-s', 'l-t'],
      'bearerFilter->authManager': ['b-s', 't-t'],
      'authManager->jwtDecoder': ['r-s', 'l-t'],
      'jwtDecoder->converter': ['r-s', 'l-t'],
      'converter->database': ['r-s', 'l-t'],
      'converter->securityContext': ['b-s', 't-t'],
      'securityContext->authorization': ['r-s', 'l-t'],
      'authorization->controller': ['r-s', 'l-t'],
      'authorization->accessDenied': ['br-s', 'tr-t'],
      'authorization->entryPoint': ['bl-s', 'tr-t'],
    },
  },
  compact: {
    order: ['client', 'bearerFilter', 'authManager', 'jwtDecoder', 'converter', 'database', 'securityContext', 'authorization', 'controller', 'accessDenied', 'entryPoint'],
    handles: {
      'converter->securityContext': ['r-s', 'r-t'],
      'authorization->accessDenied': ['r-s', 'r-t'],
      'authorization->entryPoint': ['r-s', 'r-t'],
    },
  },
  heights: { wide: 540 },
  fullWidth: true,
  minWidth: 900,
  nodeMeta: {
    client: { kind: 'client', stage: 1 },
    bearerFilter: { kind: 'security', stage: 2 },
    authManager: {
      kind: 'security',
      stage: '3–4',
      chips: ['ProviderManager', { text: 'JwtAuthenticationProvider', title: 'Documented framework behaviour; SpringLab does not observe this class separately.' }],
    },
    jwtDecoder: { kind: 'security', stage: 5 },
    converter: { kind: 'business', stage: 6 },
    database: { kind: 'database' },
    securityContext: { kind: 'security', stage: 7 },
    authorization: { kind: 'security', stage: 8 },
    controller: { kind: 'mvc', stage: 9 },
    entryPoint: { kind: 'error', stage: '401' },
    accessDenied: { kind: 'error', stage: '403' },
  },
  lanes: [
    { id: 'client', label: 'Client', kind: 'client', nodes: ['client'] },
    { id: 'authentication', label: 'Authentication', caption: 'is the token genuine?', kind: 'security', nodes: ['bearerFilter', 'authManager', 'jwtDecoder'] },
    { id: 'identity', label: 'Identity', caption: 'role from the database', kind: 'business', nodes: ['converter', 'database'] },
    { id: 'authorization', label: 'Authorization', caption: 'may this caller do this?', kind: 'security', nodes: ['securityContext', 'authorization', 'controller'] },
    { id: 'rejections', label: 'Rejections', kind: 'error', nodes: ['entryPoint', 'accessDenied'] },
  ],
  edgeMeta: {
    'converter->database': { label: 'reads role', tone: 'lookup' },
    'authorization->entryPoint': { label: '401 not authenticated', tone: 'error', offset: 20, compactOffset: 46 },
    'authorization->accessDenied': { label: '403 not allowed', tone: 'error', offset: 20, compactOffset: 22 },
  },
  legend: {
    position: 'top-right',
    note: 'Bearer requests never use UserDetailsService or PasswordEncoder. Those belong to the login pipeline (POST /api/auth/login), which is a separate path.',
  },
}

export const EXPLAIN = {
  what: 'A JSON Web Token (JWT) is a compact string of three Base64URL parts - a header, a payload of claims and a signature - that a client presents in an "Authorization: Bearer" header. Spring Security\'s resource-server support validates the token and turns it into an authenticated identity for one request.',
  why: 'A stateless service needs to know who is calling without keeping sessions. A signed token lets the server confirm that it issued the token and that nobody altered it, while authorization can still use current data. The simulator keeps three questions apart that are easy to confuse: can the token be read (decoding), is it genuine and current (verification), and is this caller allowed to do this (authorization).',
  who: [
    { name: 'The client (browser)', role: 'stores the token and sends it in the Authorization header of each request' },
    { name: 'BearerTokenAuthenticationFilter', role: 'extracts the bearer token from the request and asks the AuthenticationManager to authenticate it' },
    { name: 'AuthenticationManager (ProviderManager) and JwtAuthenticationProvider', role: 'route the token to the JwtDecoder and then to the converter' },
    { name: 'JwtDecoder (NimbusJwtDecoder)', role: 'parses the token, verifies the HS256 signature and validates exp, nbf and iss' },
    { name: 'DatabaseBackedJwtAuthenticationConverter (SpringLab)', role: 'reads the caller\'s current role and enabled flag from the users table and builds the Authentication' },
    { name: 'SecurityContext / SecurityContextHolder', role: 'holds the Authentication for the duration of one request' },
    { name: 'AuthorizationFilter and ExceptionTranslationFilter', role: 'apply the access rules and turn failures into 401 or 403 responses' },
    { name: 'AuthenticationEntryPoint and AccessDeniedHandler', role: 'write the 401 and 403 responses (SpringLab: RestAuthenticationEntryPoint, RestAccessDeniedHandler)' },
    { name: 'AuthenticationManager with DaoAuthenticationProvider, UserDetailsService and PasswordEncoder', role: 'verify the username and password at login only, after which SpringLab issues the token' },
    { name: 'The caller (STUDENT, TEACHER or ADMIN)', role: 'the role stored in the database, not the one claimed in the token, decides what they may do' },
  ],
  where: 'In the security layer, before DispatcherServlet and every controller: inside the SecurityFilterChain that Spring Security runs for each request. Tokens are issued by the login endpoint (AuthService and JwtService) and checked on every later request; authorization data comes from the users table.',
  when: 'Once at login, when the token is issued, and then on every request that carries a token: first the token is extracted and validated (structure, algorithm, signature, expiry, issuer), then the user is looked up in the database, then the access rule is applied, and only then does the controller run. A failure at any stage ends the request with 401 or 403 before the controller.',
  how: [
    'Login: the AuthenticationManager verifies the username and password (UserDetailsService and PasswordEncoder) and SpringLab issues a signed JWT, header.payload.signature.',
    'Every later request sends "Authorization: Bearer <jwt>" and BearerTokenAuthenticationFilter extracts the token.',
    'The AuthenticationManager passes it to the JwtAuthenticationProvider, which asks the JwtDecoder to parse it, check the algorithm and verify the signature with the secret key.',
    'The decoder then validates the claims: exp and nbf against the clock (60 seconds of skew) and iss against the expected issuer.',
    'SpringLab\'s converter looks the user up by the token\'s subject and reads the current role and enabled flag from the database; the token\'s own role claim is ignored.',
    'The Authentication is stored in the SecurityContext for this request only; the application is stateless, so nothing is kept afterwards.',
    'AuthorizationFilter compares the authorities with the rule for the path; if the rule is satisfied the request continues to the controller.',
    'No valid authentication gives 401 from the AuthenticationEntryPoint; an authenticated caller who is not allowed gives 403 from the AccessDeniedHandler.',
  ],
}

export const STATIC_ASSUMPTIONS = [
  'Tokens are HS256 JWTs signed with a shared secret (symmetric). Asymmetric setups (RS256, ES256) configure the decoder differently.',
  'The decoder is a NimbusJwtDecoder with the default validators (exp and nbf with the library\'s default 60 second clock skew) plus an issuer check. Quoted framework messages come from the Spring Security version shown below.',
  'SpringLab builds a caller\'s authorities from the database on every authenticated request. The role claim in the token is informational.',
  'The simulator uses a separate, in-memory lab signing key and a separate JwtDecoder instance, only on /api/lab/secure/**. The application\'s real key never signs a lab token, and a lab token is rejected everywhere else.',
  'LIVE steps were observed during a real request to a lab endpoint. Which component rejected a request is inferred and labelled INFERRED.',
  'Your real access token is used only to call the simulator\'s own API and is never recorded in a trace. The tokens you see here are lab tokens.',
]

/** Key points for the Explain stage; evidenceExperiments are ids from lab/jwt-simulator-catalog.json. */
export const TAKEAWAYS = [
  { text: 'Decoding needs no key: anyone can read a token\'s header and claims. Only verification shows the token is genuine, so a claim that has not been verified must not be trusted.', evidenceExperiments: ['tampered-payload'] },
  { text: 'A token is accepted only if every check passes, in order: structure, algorithm, signature, then expiry, not-before and issuer. A correct signature does not rescue an expired token or one from another issuer.', evidenceExperiments: ['expired-token', 'not-yet-valid', 'wrong-issuer'] },
  { text: 'The server, not the token, decides which algorithm is acceptable: an unsigned "alg none" token is refused.', evidenceExperiments: ['algorithm-none'] },
  { text: 'Authentication failures are 401. They happen before any database lookup and before any controller, so a rejected token costs no SQL.', evidenceExperiments: ['expired-token', 'wrong-signing-key', 'tampered-signature', 'malformed-token', 'no-token'] },
  { text: 'Authorization failures are 403: the caller is authenticated (one database lookup) but the stored role does not satisfy the rule for the path.', evidenceExperiments: ['student-calls-admin-area', 'teacher-area'] },
  { text: 'The role claim in a token is not authoritative. A validly signed token claiming ADMIN does not make anyone an administrator, and a token claiming less does not demote one: the database wins both ways.', evidenceExperiments: ['claim-not-authoritative', 'claim-understated'] },
  { text: 'The login pipeline (AuthenticationManager, UserDetailsService, PasswordEncoder) is used to log in. A bearer request never touches it; it is authenticated by the JwtDecoder and the converter.', evidenceExperiments: [] },
]

/** 401 versus 403, side by side. The experiment ids point to the live evidence for each claim. */
export const AUTHN_VS_AUTHZ = {
  authentication: {
    title: 'Authentication: "Who are you?"',
    status: '401 Unauthorized',
    happens: 'Before any database lookup or controller',
    cause: 'No token, or a token that is malformed, tampered, expired, not yet valid, from the wrong issuer, signed with the wrong key or unsigned. Also a valid token for an unknown or disabled account.',
    component: 'AuthenticationEntryPoint',
    experiments: ['no-token', 'expired-token', 'wrong-signing-key', 'tampered-signature', 'algorithm-none'],
  },
  authorization: {
    title: 'Authorization: "May you do this?"',
    status: '403 Forbidden',
    happens: 'After authentication succeeded (one role lookup), before the controller',
    cause: 'The caller is known, but the role stored in the database does not satisfy the rule for this path, whatever the token\'s role claim says.',
    component: 'AccessDeniedHandler',
    experiments: ['student-calls-admin-area', 'claim-not-authoritative', 'teacher-area'],
  },
}

/**
 * Spring Security components, with how SpringLab actually uses them.
 * usedBy: 'login' | 'bearer' | 'both'.  evidence: 'tested' (a backend test proves the SpringLab statement) or
 * 'documented' (framework behaviour taken from Spring Security's documented design, not separately observed here).
 * factKey selects a value read live from GET /api/lab/jwt/components.
 */
export const GLOSSARY = [
  {
    id: 'securityFilterChain', name: 'SecurityFilterChain and FilterChainProxy', usedBy: 'both', evidence: 'tested',
    role: 'A list of servlet filters that Spring Security applies to a request. FilterChainProxy picks the first chain whose request matcher fits the URL.',
    inSpringLab: 'There are two chains: a lab chain for /api/lab/secure/** and the main chain for everything else. Both are stateless.',
    factKey: 'filterChains', codeRefId: 'security-filter-chain',
  },
  {
    id: 'bearerTokenFilter', name: 'BearerTokenAuthenticationFilter', usedBy: 'bearer', evidence: 'tested',
    role: 'Extracts a token from an "Authorization: Bearer" header and asks the AuthenticationManager to authenticate it. Without that header it does nothing.',
    inSpringLab: 'Added by oauth2ResourceServer(...).jwt(...) in both chains and listed in every trace.',
    factKey: null, codeRefId: 'security-filter-chain',
  },
  {
    id: 'authenticationManager', name: 'AuthenticationManager (ProviderManager)', usedBy: 'both', evidence: 'tested',
    role: 'The entry point of authentication: it asks each AuthenticationProvider in turn whether it can authenticate the given Authentication.',
    inSpringLab: 'The bean named authenticationManager serves LOGIN. A bearer request is authenticated by the resource-server support\'s own manager with a JwtAuthenticationProvider (documented Spring Security behaviour).',
    factKey: 'authenticationManager', codeRefId: 'security-beans',
  },
  {
    id: 'authenticationProvider', name: 'AuthenticationProvider', usedBy: 'both', evidence: 'documented',
    role: 'Performs authentication for one kind of Authentication: DaoAuthenticationProvider checks a username and password, JwtAuthenticationProvider validates a bearer JWT.',
    inSpringLab: 'The login manager reports DaoAuthenticationProvider (read from the running application). JwtAuthenticationProvider comes with the resource-server setup and is not separately observed.',
    factKey: 'authenticationManager.providers', codeRefId: 'login-service',
  },
  {
    id: 'userDetailsService', name: 'UserDetailsService', usedBy: 'login', evidence: 'tested',
    role: 'Loads a stored user (name, password hash, authorities, enabled flag) by username.',
    inSpringLab: 'AppUserDetailsService, called by DaoAuthenticationProvider when someone logs in. A test proves a bearer request never calls it.',
    factKey: 'userDetailsService', codeRefId: 'user-details-service',
  },
  {
    id: 'passwordEncoder', name: 'PasswordEncoder', usedBy: 'login', evidence: 'tested',
    role: 'Hashes new passwords and checks a submitted password against the stored hash.',
    inSpringLab: 'A DelegatingPasswordEncoder (hashes carry a {bcrypt} prefix). It is used at login and never by a bearer request (tested).',
    factKey: 'passwordEncoder', codeRefId: 'security-beans',
  },
  {
    id: 'jwtDecoder', name: 'JwtDecoder', usedBy: 'bearer', evidence: 'tested',
    role: 'Parses a JWT, verifies its signature and validates its claims, and throws if anything is wrong.',
    inSpringLab: 'One NimbusJwtDecoder bean (HS256, the default validators and an issuer check). The simulator uses a second decoder INSTANCE for the lab key that is deliberately not a bean.',
    factKey: 'jwtDecoder', codeRefId: 'jwt-decoder-config',
  },
  {
    id: 'jwtConverter', name: 'JwtAuthenticationConverter (the converter role)', usedBy: 'bearer', evidence: 'tested',
    role: 'Turns a validated Jwt into an Authentication with a principal and authorities. Spring\'s own JwtAuthenticationConverter maps claims to authorities.',
    inSpringLab: 'SpringLab does not use Spring\'s JwtAuthenticationConverter. Its own DatabaseBackedJwtAuthenticationConverter does the same job but takes the authorities from the users table, not from claims.',
    factKey: 'jwtAuthenticationConverter', codeRefId: 'auth-converter',
  },
  {
    id: 'securityContext', name: 'SecurityContext and SecurityContextHolder', usedBy: 'both', evidence: 'tested',
    role: 'Holds the Authentication of the current request in a thread-local; the SecurityContextHolderFilter sets it up and clears it.',
    inSpringLab: 'Stateless: no HTTP session and no cookie are created (tested), so the Authentication exists only for the duration of one request.',
    factKey: null, codeRefId: 'security-filter-chain',
  },
  {
    id: 'authorizationRules', name: 'AuthorizationFilter and the access rules', usedBy: 'bearer', evidence: 'tested',
    role: 'Decides whether the current Authentication may access the URL, using rules such as hasRole("ADMIN").',
    inSpringLab: 'The rules live in SecurityConfig (main chain) and LabSecurityConfig (lab chain); @PreAuthorize repeats some of them on controllers.',
    factKey: null, codeRefId: 'lab-secure-chain',
  },
  {
    id: 'exceptionHandling', name: 'ExceptionTranslationFilter, AuthenticationEntryPoint and AccessDeniedHandler', usedBy: 'both', evidence: 'tested',
    role: 'Turn "not authenticated" into a 401 and "not allowed" into a 403.',
    inSpringLab: 'RestAuthenticationEntryPoint and RestAccessDeniedHandler write application/problem+json responses.',
    factKey: null, codeRefId: 'entry-point',
  },
]

const sim = (id, title, nodes, edgeFrom, edgeTo, explanation, codeRefId) => ({
  id,
  title,
  nodes,
  edgeFrom,
  edgeTo,
  direction: 'REQUEST',
  provenance: 'CONCEPTUAL',
  explanation,
  evidence: [],
  sql: [],
  codeRefId: codeRefId ?? null,
})

export const SIMULATED_STEPS = [
  sim('client-request', 'The client presents a token', ['client'], 'client', 'bearerFilter',
    'The client sends "Authorization: Bearer <jwt>". The token is three Base64URL parts joined by dots: a header (algorithm), a payload (claims such as sub, exp, iss) and a signature. SpringLab builds it when someone logs in.',
    'jwt-issuing'),
  sim('bearer-filter', 'The bearer token is extracted', ['bearerFilter'], null, null,
    'BearerTokenAuthenticationFilter reads the Authorization header. If it holds a bearer token, the filter wraps it in an unauthenticated BearerTokenAuthenticationToken and asks the AuthenticationManager to authenticate it. With no bearer header it does nothing and the request continues as anonymous.',
    'security-filter-chain'),
  sim('authentication-manager', 'The AuthenticationManager picks a provider', ['authManager'], 'bearerFilter', 'authManager',
    'The AuthenticationManager (a ProviderManager) asks its AuthenticationProviders which one supports this kind of Authentication. For a bearer token that is the JwtAuthenticationProvider, which delegates to the JwtDecoder.'),
  sim('login-contrast', 'Contrast: the login pipeline is different', ['authManager'], null, null,
    'At LOGIN the AuthenticationManager uses DaoAuthenticationProvider, which calls UserDetailsService.loadUserByUsername and the PasswordEncoder to check the password, and SpringLab then issues the token. A bearer request does none of that: the token replaces the password. SpringLab\'s tests prove it.',
    'login-service'),
  sim('jwt-decoder', 'The JwtDecoder parses, verifies and validates', ['jwtDecoder'], 'authManager', 'jwtDecoder',
    'In order: the token must have three parts and decode; its algorithm must be the one the server expects (HS256); its signature must verify with the secret key; only then are the claims validated: exp and nbf against the clock (60 seconds of skew) and iss against the expected issuer. The first failure ends authentication with a 401. Reading the claims needs no key; trusting them does.',
    'jwt-decoder-config'),
  sim('converter', 'The converter builds the Authentication', ['converter'], 'jwtDecoder', 'converter',
    'A token that passed validation is handed to SpringLab\'s converter. It uses the subject to find the user and builds the Authentication whose authorities come from the database. This is why the token\'s role claim is not authoritative.',
    'auth-converter'),
  sim('database-lookup', 'The role and status come from the database', ['database'], 'converter', 'database',
    'The converter reads the user\'s current role and enabled flag. An unknown or disabled user is refused with the same 401 as an invalid token, so a role change or a disabled account takes effect on the very next request.',
    'auth-converter'),
  sim('security-context', 'The Authentication is stored for this request', ['securityContext'], 'converter', 'securityContext',
    'The Authentication (the validated Jwt as principal, ROLE_<stored role> as authority) is put in the SecurityContext. The application is stateless, so no session is created and the context is cleared when the request ends.',
    'security-filter-chain'),
  sim('authorization', 'The access rule is applied', ['authorization'], 'securityContext', 'authorization',
    'AuthorizationFilter evaluates the rule for the path, for example hasRole("ADMIN"), against the authorities in the SecurityContext. Satisfied: the request moves on. Not satisfied: access is denied.',
    'lab-secure-chain'),
  sim('controller', 'Allowed: the controller runs', ['controller'], 'authorization', 'controller',
    'Authentication and authorization both succeeded, so DispatcherServlet invokes the controller and the response is 200.',
    'lab-secure-controller'),
  sim('forbidden', 'Authenticated but not allowed: 403', ['accessDenied'], 'authorization', 'accessDenied',
    'When an authenticated caller does not satisfy the rule, ExceptionTranslationFilter calls the AccessDeniedHandler, which answers 403 Forbidden. The caller is known; the answer is still no.',
    'access-denied-handler'),
  sim('unauthenticated', 'Not authenticated: 401', ['entryPoint'], 'authorization', 'entryPoint',
    'When there is no valid authentication, the AuthenticationEntryPoint answers 401 Unauthorized with a Bearer challenge. An invalid token is refused earlier, by the bearer filter\'s failure handling, with a challenge that names the problem. 401 means "I do not know who you are".',
    'entry-point'),
]

export const SIMULATED_SCENARIO = {
  id: 'jwt-security-simulated',
  title: 'A bearer token through Spring Security (conceptual walkthrough)',
  mode: 'SIMULATED',
  nodes: NODES,
  edges: EDGES,
  layout: LAYOUT,
  steps: SIMULATED_STEPS,
}
