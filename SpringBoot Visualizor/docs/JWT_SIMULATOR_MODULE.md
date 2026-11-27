# JWT Security Simulator module (Phase 6)

Route: `/learn/jwt-security`. Flow: Understand → Visualize → Experiment → Observe → Explain → Test.
Built on the learning framework (`LEARNING_FRAMEWORK.md`) and the shared trace infrastructure of the REST Request
Lifecycle module (`REST_LIFECYCLE_MODULE.md`).

## What the learner can do

* **Take a token apart** (Understand): issue a lab token with a chosen fault, or paste any token. The server shows
  **decoding** (Base64URL, no key) and **verification** (the lab decoder's verdict plus the individual checks) in two
  separate panels, so the difference between "I can read it" and "I can trust it" is concrete.
* **Walk through the pipeline** (Visualize): a SIMULATED conceptual walkthrough, or a LIVE request: the server issues a
  lab token of the chosen kind and one real request carrying it goes through a real Spring Security filter chain.
* **Run 14 LIVE experiments and read 3 SIMULATED ones** (Experiment), grouped as authentication (401) and authorization (403).
* **Read the evidence** (Observe), **the explanation** (Explain: 401 vs 403 side by side, takeaways linked to experiments,
  every Spring Security component and how SpringLab uses it), and **test yourself** (the `jwt-security` Arena topic).

## Architecture: how a lab request is processed

```
POST /api/lab/jwt/issue         real chain, real token      → LabJwtService signs with the LAB key
GET  /api/lab/secure/**         LAB chain (Order 1)         → BearerTokenAuthenticationFilter
                                                              → LAB JwtDecoder instance (lab key, same validators)
                                                              → DatabaseBackedJwtAuthenticationConverter (REAL class: users table)
                                                              → AuthorizationFilter rules → LabSecureController
                                                              → RestAuthenticationEntryPoint / RestAccessDeniedHandler (REAL classes)
everything else                 MAIN chain (unchanged)
```

* **Separate key, no second bean.** `LabJwtService` creates a random 256-bit key in memory at startup (never configured, logged or
  returned; it changes on every restart) and a second unrelated key for the "wrong key" experiment. The key, encoder and
  decoder are plain fields: the application still has exactly **one** `JwtDecoder`, **one** `JwtEncoder` and **one**
  `SecretKey` bean. The lab chain passes the decoder to `jwt.decoder(...)` as an instance, which needs no bean.
* **Why a lab chain at all.** Expiry, not-before, issuer and wrong-key failures can only be shown for real if the learner can
  obtain a correctly signed (or deliberately mis-signed) token. That must never involve the application's real key, so the
  real chain cannot be used. The lab chain is built like the real one (resource server, stateless, the application's own
  converter, entry point and access-denied handler); only the decoder's key and issuer and the protected paths differ.
* **Nothing can be reached with a lab token** except `/api/lab/secure/**` (which only describes the caller) and the inspector.
  The real decoder rejects it, and the lab decoder rejects real tokens.
* **The subject is never client-chosen**: a lab token always names the caller. The client may choose the *fault* and the
  *role the token claims*, never who it is about.
* **Tracing** reuses the Phase 5 filter (traces are owner-bound via single-use tickets, see `REST_LIFECYCLE_MODULE.md`) through the new `TraceModule` interface; the JWT module lists only its three
  lab-secure GET endpoints and has its own `JwtStepAssembler`. The trace now also keeps the Authorization **scheme** (never
  the credentials) and the server's own `WWW-Authenticate` challenge (dropped if it ever contained the credentials).

## Endpoints (all authenticated by the real chain unless noted)

| Endpoint | Purpose |
|---|---|
| `POST /api/lab/jwt/issue` | `{variant, claimedRole?}` → a lab token for the caller, plus its inspection |
| `POST /api/lab/jwt/inspect` | `{token}` (≤ 4096 chars) → decoding + verification against the lab key |
| `GET /api/lab/jwt/catalog` | endpoints, token faults and the 14 + 3 experiments with documented expectations |
| `GET /api/lab/jwt/components` | what the running application's security configuration actually consists of (class names, counts) |
| `GET /api/lab/secure/whoami`, `/teacher-area`, `/admin-area` | **lab chain**; traceable; describe the caller |

## What is LIVE, INFERRED and SIMULATED

| Claim shown | Provenance | Why |
|---|---|---|
| Authorization scheme present, configured filter list, status, challenge header, SQL text, controller reached, authorities in the SecurityContext (when a controller was reached) | CAPTURED → **LIVE** | observed during the request |
| The decoder's verdict and message for a token | CAPTURED → LIVE | the real lab decoder's own answer |
| The individual checks (structure, algorithm, signature, expiry, nbf, issuer) | DERIVED → INFERRED | rebuilt by running the same components one at a time; cross-checked against the decoder on every call |
| "The JwtDecoder rejected it", "the converter refused it", "authorization denied it", "the entry point / access-denied handler wrote the response" | DERIVED → INFERRED | attributed from status, challenge text and whether a database lookup followed |
| The SecurityContext and authorization steps of a **403** | DERIVED → INFERRED | no controller ran, so nothing observed them |
| The walkthrough, leaked-secret / RS256 / disabled-while-valid experiments, MySQL execution | CONCEPTUAL → SIMULATED | explanation only |

## Claim → test map

| Claim | Verified by |
|---|---|
| Every LIVE experiment gives the documented status / controller / SQL count / challenge text **for every role**; SQL count equals Hibernate's own statistics | `JwtSimulatorIntegrationTest.everyLiveExperimentBehavesAsTheLessonClaimsForEveryRole` |
| Same experiments against **MySQL's own statement counters** (per schema) over real HTTP, plus real role/enable changes through the admin API | live script (`verify_phase6`), 149 checks |
| The inspector's verdict for each fault matches what the real filter chain does, and its first failing check matches the catalog | `theInspectorsVerdictForEveryIssuedTokenMatchesWhatTheRealFilterChainDoes`, `LabJwtInspectorTest` |
| The reconstructed checks always agree with the decoder; only the valid token is accepted; categories are right | `LabJwtInspectorTest` (all nine faults) |
| Decoding works for any token incl. forged / invalid ones; a forged role claim is readable but rejected | `anyTokenCanBeDecodedWithoutAKey...`, `aTamperedPayloadShowsTheForgedRole...` |
| `alg: none` is refused because the server decides the algorithm | `theAlgorithmNoneToken...`, experiment `algorithm-none` |
| A signature check precedes the claim checks | `theFrameworkMessageIsTheRealDecoders...` (expired + wrong signature → BAD_SIGNATURE) |
| Roles come from the database, not the claim, in both directions | experiments `claim-not-authoritative`, `claim-understated`; `theWhoAmIEndpointShowsDatabaseAuthorities...` |
| A disabled or deleted account's valid token is refused **after** the lookup (1 SQL), unlike a decoder rejection (0 SQL) | `anAccountDisabledAfterTheTokenWasIssued...`, `aValidTokenForAnUnknownUser...` |
| A lab token is useless on real endpoints; a real token is rejected by the lab chain | `aLabTokenIsUselessAgainstTheApplicationsRealEndpoints`, `aRealApplicationTokenIsRejectedByTheLabChain` |
| No second `JwtDecoder` / `JwtEncoder` / `SecretKey` bean; the lab decoder is not a bean | `noSecondJwtDecoderEncoderOrSecretKeyBeanExists`, components endpoint |
| Main chain unchanged; exactly one lab chain in front of it | `theApplicationHasTheMainChainAndExactlyOneLabChain...` |
| The lab key, the real token and the real secret never appear in any response or trace | `theLabKeyAppearsInNoResponseOrTrace`, `theRealTokenAndRealSecretNeverAppear...`, live script |
| Issuing never accepts a client subject; validates input; requires authentication | `aLabTokenAlwaysNamesTheCaller...`, `issuingValidatesItsInput`, `issuingAndInspectingRequireAuthentication` |
| `AuthenticationManager` (login) = `DaoAuthenticationProvider`; `UserDetailsService`, `PasswordEncoder`, converter are SpringLab's | `theComponentFactsMatchTheRunningApplication` |
| `UserDetailsService` and `PasswordEncoder` are used at login and **never** by a bearer request | `LoginVersusBearerIntegrationTest` (Mockito spies) |
| Stateless: no session, no cookie | `neitherChainCreatesASessionOrCookie` |
| Trace filter list equals the chain Spring reports for the lab URL | `theSecurityFilterListInATraceIsTheChain...` |
| Provenance rules per outcome (valid / expired / forbidden / no token / disabled) | `JwtStepAssemblerTest` |
| The credentials never reach a stored trace; challenge redaction | `TraceFilterHelpersTest`, `theTokenAndItsValueNeverAppearInAnyStep` |
| Catalog is consistent and complete (required failures present, 401 ⇒ 0 SQL, 403 ⇒ 1 SQL) | `JwtCatalogConsistencyTest`, `jwtSecurity.test.js` |
| Every code excerpt equals the real source lines | `CodeReferenceTest` (23 references) |
| Frontend ids match the backend's; diagram layout valid in both layouts | `jwtSecurity.test.js` (reads the Java tests' id sets) |
| Decode panel vs verify panel, badges, glossary, workshop, What If details | `jwtComponents.test.jsx` |

## Known limits

* Which component rejected a request is **inferred**, not observed; the decoder's individual checks are reconstructed.
* The `JwtAuthenticationProvider` that authenticates bearer tokens is documented framework behaviour, not separately observed
  (the glossary marks it as such). Everything else in the glossary about SpringLab is verified by a test.
* No reflection into Spring Security is used any more. The login `AuthenticationManager` is declared explicitly in
  `SecurityConfig` as a plain `ProviderManager` with one `DaoAuthenticationProvider`, so its providers are public API and
  the facts endpoint reads them directly (guarded by `theLoginAuthenticationManagerIsAnExplicitProviderManager...`). If the
  manager were ever replaced by another type, the endpoint reports no providers and the UI says "providers not reported"
  (`componentFacts.js` returns null for any missing or malformed value; tested). The only reflection left is in tests, to read
  the lab key and prove it never appears in a response.
* Quoted framework messages (for example "Jwt expired at …") are from the Spring Security version shown on the page.
* Lab tokens stop working when the application restarts (the lab key is regenerated).
* Self-invocations inside services and calls that bypass Spring proxies are not observed (as in the other module).
