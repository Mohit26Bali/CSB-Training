import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import * as jwt from '../scenarios/jwtSecurity.sim'
import * as lifecycle from '../scenarios/restLifecycle.sim'
import { challengeOf, checkExpectation, summarizeTrace } from './compare'
import { factText } from './componentFacts'
import { validateLayout } from './layout'
import { PROVENANCE, validateExplain5W1H, validateScenario } from './scenario'

const backend = (...parts) => resolve(process.cwd(), '../backend', ...parts)
const resource = (name) => JSON.parse(readFileSync(backend('src/main/resources/lab', name), 'utf8'))
const codeRefIds = new Set(resource('code-references.json').map((r) => r.id))
const catalog = resource('jwt-simulator-catalog.json')
const experiments = new Map(catalog.experiments.map((e) => [e.id, e]))

/** Extracts the quoted ids of `static final Set<String> NAME = Set.of(...)` from a Java test source file. */
function javaSet(file, name) {
  const source = readFileSync(backend('src/test/java/com/springlab/lab', file), 'utf8')
  const block = source.match(new RegExp(`${name}\\s*=\\s*Set\\.of\\(([^;]*)\\);`))
  expect(block, `${name} in ${file}`).not.toBeNull()
  return [...block[1].matchAll(/"([^"]+)"/g)].map((m) => m[1]).sort()
}

describe('JWT Security Simulator content', () => {
  it('has the mandatory complete 5W1H explanation', () => {
    expect(validateExplain5W1H(jwt.EXPLAIN)).toEqual([])
  })

  it('the SIMULATED scenario is valid, uses real code references and carries no observations', () => {
    expect(validateScenario(jwt.SIMULATED_SCENARIO, { codeRefIds })).toEqual([])
    for (const step of jwt.SIMULATED_STEPS) {
      expect(step.provenance).toBe(PROVENANCE.CONCEPTUAL)
      expect(step.sql).toEqual([])
      expect(step.evidence).toEqual([])
    }
  })

  it('walks the whole pipeline, from the token to both refusal handlers', () => {
    const touched = new Set(jwt.SIMULATED_STEPS.flatMap((s) => s.nodes))
    for (const node of jwt.NODES) expect(touched.has(node.id), `${node.id} appears in a step`).toBe(true)
  })

  it('the diagram layout places every node and can draw every edge in both layouts', () => {
    expect(validateLayout(jwt.SIMULATED_SCENARIO)).toEqual([])
    expect(validateLayout(lifecycle.SIMULATED_SCENARIO)).toEqual([])
  })

  it('declares the same nodes and edges as the backend assembler\'s tests', () => {
    expect(jwt.NODES.map((n) => n.id).sort()).toEqual(javaSet('trace/JwtStepAssemblerTest.java', 'NODES'))
    expect(jwt.EDGES.map((e) => e.id).sort()).toEqual(javaSet('trace/JwtStepAssemblerTest.java', 'EDGES'))
    expect(lifecycle.NODES.map((n) => n.id).sort()).toEqual(javaSet('LifecycleStepAssemblerTest.java', 'NODES'))
    expect(lifecycle.EDGES.map((e) => e.id).sort()).toEqual(javaSet('LifecycleStepAssemblerTest.java', 'EDGES'))
  })
})

describe('the component glossary', () => {
  const required = ['SecurityFilterChain', 'BearerTokenAuthenticationFilter', 'AuthenticationManager', 'AuthenticationProvider',
    'UserDetailsService', 'JwtDecoder', 'JwtAuthenticationConverter', 'SecurityContext', 'AuthorizationFilter']

  it('covers every Spring Security component the lesson must explain', () => {
    const names = jwt.GLOSSARY.map((g) => g.name).join(' | ')
    for (const component of required) expect(names, component).toContain(component)
  })

  it('every entry is complete and honest about how it is known', () => {
    const ids = new Set()
    for (const g of jwt.GLOSSARY) {
      expect(ids.has(g.id), `duplicate glossary id ${g.id}`).toBe(false)
      ids.add(g.id)
      expect(g.role.length).toBeGreaterThan(30)
      expect(g.inSpringLab.length).toBeGreaterThan(30)
      expect(['login', 'bearer', 'both']).toContain(g.usedBy)
      expect(['tested', 'documented']).toContain(g.evidence)
      if (g.codeRefId) expect(codeRefIds.has(g.codeRefId), `${g.id} code reference`).toBe(true)
    }
  })

  it('states that SpringLab does not use Spring\'s own JwtAuthenticationConverter', () => {
    const converter = jwt.GLOSSARY.find((g) => g.id === 'jwtConverter')
    expect(converter.inSpringLab).toContain('does not use Spring\'s JwtAuthenticationConverter')
    expect(converter.inSpringLab).toContain('DatabaseBackedJwtAuthenticationConverter')
  })

  it('places the password pipeline at LOGIN only and the token pipeline on bearer requests', () => {
    const byId = Object.fromEntries(jwt.GLOSSARY.map((g) => [g.id, g]))
    expect(byId.userDetailsService.usedBy).toBe('login')
    expect(byId.passwordEncoder.usedBy).toBe('login')
    expect(byId.jwtDecoder.usedBy).toBe('bearer')
    expect(byId.jwtConverter.usedBy).toBe('bearer')
  })

  it('anything not proven by a test is marked as documented framework behaviour', () => {
    const documented = jwt.GLOSSARY.filter((g) => g.evidence === 'documented').map((g) => g.id)
    expect(documented).toContain('authenticationProvider') // JwtAuthenticationProvider is not separately observed
  })

  it('reads live facts from the backend report', () => {
    const facts = {
      filterChains: [{ matcher: 'lab', filters: [] }, { matcher: 'main', filters: [] }],
      authenticationManager: { type: 'ProviderManager', providers: ['DaoAuthenticationProvider'] },
      userDetailsService: 'AppUserDetailsService', passwordEncoder: 'DelegatingPasswordEncoder',
      jwtDecoder: { type: 'NimbusJwtDecoder', beanCount: 1 }, jwtAuthenticationConverter: 'DatabaseBackedJwtAuthenticationConverter',
    }
    expect(factText(facts, 'filterChains')).toBe('2 chain(s): lab  |  main')
    expect(factText(facts, 'authenticationManager')).toBe('ProviderManager with providers [DaoAuthenticationProvider]')
    expect(factText(facts, 'authenticationManager.providers')).toBe('DaoAuthenticationProvider')
    expect(factText(facts, 'jwtDecoder')).toBe('NimbusJwtDecoder (1 bean)')
    expect(factText(facts, 'userDetailsService')).toBe('AppUserDetailsService')
    expect(factText(facts, 'unknown')).toBeNull()
    expect(factText(null, 'filterChains')).toBeNull()
    expect(factText({ ...facts, authenticationManager: { type: 'X', providers: [] } }, 'authenticationManager.providers')).toBeNull()
  })
})

describe('the claims the lesson makes are backed by catalogued experiments', () => {
  const cited = [...jwt.TAKEAWAYS.flatMap((t) => t.evidenceExperiments), ...Object.values(jwt.AUTHN_VS_AUTHZ).flatMap((s) => s.experiments)]

  it('every cited experiment exists and is a LIVE one the backend tests execute', () => {
    for (const id of cited) {
      expect(experiments.has(id), id).toBe(true)
      expect(experiments.get(id).kind, id).toBe('LIVE')
    }
  })

  it('experiments cited as authentication failures are 401s with no SQL; authorization ones end in 403', () => {
    for (const id of jwt.AUTHN_VS_AUTHZ.authentication.experiments) {
      for (const x of Object.values(experiments.get(id).expectation)) {
        expect(x.status).toBe(401)
        expect(x.sqlCount).toBe(0)
      }
    }
    for (const id of jwt.AUTHN_VS_AUTHZ.authorization.experiments) {
      const statuses = Object.values(experiments.get(id).expectation).map((x) => x.status)
      expect(statuses).toContain(403)
    }
  })

  it('every experiment of the catalog is reachable from the page and has consequences stated', () => {
    for (const e of catalog.experiments) {
      expect(['authentication', 'authorization']).toContain(e.group)
      expect(e.consequence.length).toBeGreaterThan(60)
      expect(e.changes.length).toBeGreaterThan(20)
    }
  })

  it('names the four things the lesson must show: expiry, malformed, tampered and wrong key / issuer', () => {
    const ids = catalog.experiments.map((e) => e.id)
    for (const required of ['expired-token', 'malformed-token', 'tampered-signature', 'wrong-signing-key', 'wrong-issuer']) {
      expect(ids).toContain(required)
    }
  })
})

describe('the server\'s challenge as evidence', () => {
  const trace = (evidence) => ({ status: 401, handlerInvoked: false, sqlCount: 0, steps: [{ id: 's', evidence }] })

  it('finds the WWW-Authenticate challenge in any of the labels the backend uses', () => {
    for (const label of ['Observed WWW-Authenticate', 'Observed WWW-Authenticate header', 'WWW-Authenticate']) {
      expect(challengeOf(trace([{ label, value: 'Bearer error="invalid_token"' }]))).toBe('Bearer error="invalid_token"')
    }
    expect(challengeOf(trace([{ label: 'WWW-Authenticate', value: '(none)' }]))).toBeNull()
    expect(challengeOf(trace([]))).toBeNull()
  })

  it('checks a documented challenge fragment case-insensitively and flags a mismatch', () => {
    const observed = summarizeTrace(trace([{ label: 'WWW-Authenticate', value: 'Bearer error_description="Jwt expired at 2026"' }]))
    const ok = checkExpectation({ status: 401, handlerInvoked: false, sqlCount: 0, challengeContains: 'EXPIRED' }, observed)
    expect(ok.every((r) => r.ok)).toBe(true)
    expect(ok.at(-1).field).toContain('mentions "EXPIRED"')
    const bad = checkExpectation({ challengeContains: 'signature' }, observed)
    expect(bad[0].ok).toBe(false)
  })
})

describe('layout validation catches broken diagrams', () => {
  const base = () => structuredClone(jwt.SIMULATED_SCENARIO)

  it('reports a missing position, a missing compact entry and a duplicate position', () => {
    const s = base()
    delete s.layout.wide.positions.database
    s.layout.compact.order = s.layout.compact.order.filter((id) => id !== 'controller')
    s.layout.wide.positions.client = { ...s.layout.wide.positions.bearerFilter }
    const problems = validateLayout(s).join(' ')
    expect(problems).toContain('missing a position for database')
    expect(problems).toContain('compact layout is missing controller')
    expect(problems).toContain('share a wide position')
  })

  it('requires explicit handles for edges between non-neighbours in the compact column', () => {
    const s = base()
    delete s.layout.compact.handles['converter->securityContext']
    expect(validateLayout(s).join(' ')).toContain('converter->securityContext joins non-adjacent nodes')
  })

  it('rejects invalid handle ids and a missing layout', () => {
    const s = base()
    s.layout.wide.handles['client->bearerFilter'] = ['x-s', 'l-t']
    expect(validateLayout(s).join(' ')).toContain('client->bearerFilter')
    expect(validateLayout({ nodes: [], edges: [] })).toEqual(['scenario has no layout'])
  })
})

describe('unavailable or partial runtime metadata never breaks the lesson', () => {
  const keys = ['filterChains', 'authenticationManager', 'authenticationManager.providers', 'userDetailsService',
    'passwordEncoder', 'jwtDecoder', 'jwtAuthenticationConverter']

  it('returns null (never throws) for missing, empty or malformed facts', () => {
    for (const facts of [undefined, null, {}, { filterChains: 'x', authenticationManager: 5, jwtDecoder: [] },
      { filterChains: [], authenticationManager: { type: '', providers: null }, jwtDecoder: { type: null } }]) {
      for (const key of keys) expect(() => factText(facts, key), `${JSON.stringify(facts)} / ${key}`).not.toThrow()
      for (const key of keys) expect(factText(facts, key)).toBeNull()
    }
  })

  it('says the providers are not reported instead of showing an empty list', () => {
    const facts = { authenticationManager: { type: 'ProviderManager', providers: [] } }
    expect(factText(facts, 'authenticationManager')).toBe('ProviderManager (providers not reported)')
    expect(factText(facts, 'authenticationManager.providers')).toBeNull()
  })

  it('tolerates a chain without a matcher and a decoder without a bean count', () => {
    expect(factText({ filterChains: [{}] }, 'filterChains')).toBe('1 chain(s): (unknown)')
    expect(factText({ jwtDecoder: { type: 'NimbusJwtDecoder' } }, 'jwtDecoder')).toBe('NimbusJwtDecoder (? bean)')
  })
})
