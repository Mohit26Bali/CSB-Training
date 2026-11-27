import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { EXPLAIN, NODES, EDGES, SIMULATED_SCENARIO, TAKEAWAYS } from '../scenarios/restLifecycle.sim'
import { checkExpectation, expectationForRole, skippedSteps, summarizeTrace } from './compare'
import { tokenizeLine } from './codeHighlight'
import { arenaUrl, badgeFor, EXPLAIN_SECTIONS, PROVENANCE, tamperToken, validateExplain5W1H, validateScenario } from './scenario'
import { STAGES } from './stages'

const backendResource = (name) => JSON.parse(readFileSync(resolve(process.cwd(), '../backend/src/main/resources/lab', name), 'utf8'))
const codeRefIds = new Set(backendResource('code-references.json').map((r) => r.id))
const catalog = backendResource('lifecycle-catalog.json')

describe('5W1H explanation schema', () => {
  const complete = () => structuredClone(EXPLAIN)

  it('has exactly the six mandatory sections, in order', () => {
    expect(EXPLAIN_SECTIONS.map((s) => s.key)).toEqual(['what', 'why', 'who', 'where', 'when', 'how'])
  })

  it('accepts the REST Request Lifecycle explanation', () => {
    expect(validateExplain5W1H(EXPLAIN)).toEqual([])
  })

  it.each(['what', 'why', 'who', 'where', 'when', 'how'])('rejects an explanation without "%s"', (key) => {
    const broken = complete()
    delete broken[key]
    const problems = validateExplain5W1H(broken)
    expect(problems.length).toBeGreaterThan(0)
    expect(problems.join(' ')).toContain(`"${key}"`)
  })

  it('rejects empty or too-thin sections', () => {
    const thin = complete()
    thin.what = 'short'
    thin.who = []
    thin.how = ['one', 'two']
    const problems = validateExplain5W1H(thin).join(' ')
    expect(problems).toContain('"what"')
    expect(problems).toContain('"who"')
    expect(problems).toContain('"how"')
  })

  it('rejects participants without a role and placeholder text', () => {
    const bad = complete()
    bad.who[0] = { name: 'Tomcat' }
    bad.why = 'TODO write the reason this concept exists later'
    const problems = validateExplain5W1H(bad).join(' ')
    expect(problems).toContain('"who"[0]')
    expect(problems).toContain('placeholder')
  })

  it('rejects a missing explanation object', () => {
    expect(validateExplain5W1H(undefined)).toHaveLength(1)
  })
})

describe('scenario schema and the SIMULATED walkthrough', () => {
  it('the simulated scenario is structurally valid and every code reference exists in the backend', () => {
    expect(validateScenario(SIMULATED_SCENARIO, { codeRefIds })).toEqual([])
  })

  it('the simulated scenario contains only CONCEPTUAL steps with no evidence and no SQL', () => {
    expect(SIMULATED_SCENARIO.steps.length).toBeGreaterThanOrEqual(10)
    for (const step of SIMULATED_SCENARIO.steps) {
      expect(step.provenance).toBe(PROVENANCE.CONCEPTUAL)
      expect(step.sql).toEqual([])
      expect(step.evidence).toEqual([])
      expect(JSON.stringify(step)).not.toMatch(/\bselect\b.*\bfrom\b/i) // never invented SQL
    }
  })

  it('declares the same diagram nodes and edges the backend assembler uses', () => {
    expect(NODES.map((n) => n.id)).toEqual(['client', 'securityFilters', 'dispatcher', 'controller', 'service', 'repository', 'hibernate', 'database'])
    expect(EDGES.map((e) => e.id)).toContain('securityFilters->repository')
  })

  it('the lesson flow is Understand → Visualize → Experiment → Observe → Explain → Test', () => {
    expect(STAGES.map((s) => s.label)).toEqual(['Understand', 'Visualize', 'Experiment', 'Observe', 'Explain', 'Test'])
  })

  it('takeaways only cite experiments that exist in the backend catalog', () => {
    const ids = new Set(catalog.experiments.map((e) => e.id))
    for (const t of TAKEAWAYS) for (const e of t.evidenceExperiments) expect(ids.has(e)).toBe(true)
  })

  describe('the validator catches dishonest or broken scenarios', () => {
    const base = () => structuredClone({ ...SIMULATED_SCENARIO, mode: 'LIVE' })

    it('rejects SQL on a step that is not CAPTURED', () => {
      const s = base()
      s.steps[0].sql = [{ index: 1, sql: 'select 1', phase: 'HANDLER', trigger: null }]
      expect(validateScenario(s).join(' ')).toContain('SQL text may only appear on CAPTURED steps')
    })

    it('accepts SQL on a CAPTURED step of a LIVE scenario', () => {
      const s = base()
      s.steps[0].provenance = 'CAPTURED'
      s.steps[0].sql = [{ index: 1, sql: 'select 1', phase: 'HANDLER', trigger: null }]
      expect(validateScenario(s)).toEqual([])
    })

    it('rejects a SIMULATED scenario that claims a captured step or carries evidence', () => {
      const s = structuredClone(SIMULATED_SCENARIO)
      s.steps[0].provenance = 'CAPTURED'
      s.steps[1].evidence = [{ label: 'x', value: 'y' }]
      const problems = validateScenario(s).join(' ')
      expect(problems).toContain('SIMULATED scenarios may only contain CONCEPTUAL steps')
      expect(problems).toContain('must not carry captured evidence')
    })

    it('rejects unknown nodes, edges, provenance, direction and code references', () => {
      const s = base()
      s.steps[0].nodes = ['nowhere']
      s.steps[1].edgeFrom = 'a'
      s.steps[1].edgeTo = 'b'
      s.steps[2].provenance = 'LIVE'
      s.steps[3].direction = 'SIDEWAYS'
      s.steps[4].codeRefId = 'made-up'
      const problems = validateScenario(s, { codeRefIds }).join(' ')
      for (const expected of ['unknown node nowhere', 'unknown edge a->b', 'unknown provenance', 'direction', 'unknown codeRefId made-up']) {
        expect(problems).toContain(expected)
      }
    })

    it('rejects duplicate step ids', () => {
      const s = base()
      s.steps[1].id = s.steps[0].id
      expect(validateScenario(s).join(' ')).toContain('duplicated')
    })
  })
})

describe('badge wording (truthfulness)', () => {
  it('only CAPTURED can ever be labelled LIVE', () => {
    expect(badgeFor(PROVENANCE.CAPTURED).label).toBe('LIVE')
    for (const p of [PROVENANCE.DERIVED, PROVENANCE.CONCEPTUAL, undefined, 'LIVE', 'anything']) {
      expect(badgeFor(p).label).not.toBe('LIVE')
    }
  })

  it('maps inferred and conceptual steps to distinct, non-LIVE labels', () => {
    expect(badgeFor(PROVENANCE.DERIVED).label).toBe('INFERRED')
    expect(badgeFor(PROVENANCE.CONCEPTUAL).label).toBe('SIMULATED')
  })
})

describe('Test yourself link', () => {
  it('builds the Challenge Arena URL with the supported parameters', () => {
    expect(arenaUrl({ topic: 'rest-lifecycle', difficulty: 'EASY', count: 5 })).toBe('/arena?topic=rest-lifecycle&difficulty=EASY&count=5')
  })

  it('omits empty or invalid parameters instead of sending them', () => {
    expect(arenaUrl({ topic: 'rest-lifecycle', difficulty: '', count: 5 })).toBe('/arena?topic=rest-lifecycle&count=5')
    expect(arenaUrl({ topic: 'x', difficulty: 'IMPOSSIBLE', count: 99 })).toBe('/arena?topic=x')
    expect(arenaUrl({})).toBe('/arena')
  })
})

describe('tamperToken', () => {
  const token = 'aaa.bbb.SIGNATUREvalue'

  it('changes a character inside the signature and nothing else', () => {
    const tampered = tamperToken(token)
    const [h, p, s] = tampered.split('.')
    expect([h, p]).toEqual(['aaa', 'bbb'])
    expect(s).not.toBe('SIGNATUREvalue')
    expect(s.slice(1)).toBe('IGNATUREvalue')
    expect(tampered).not.toBe(token)
  })

  it('always produces a different token, including when the signature starts with the replacement letter', () => {
    expect(tamperToken('a.b.Axyz')).not.toBe('a.b.Axyz')
    expect(tamperToken('a.b.Bxyz')).not.toBe('a.b.Bxyz')
  })

  it('still alters a malformed token', () => {
    expect(tamperToken('not-a-jwt')).not.toBe('not-a-jwt')
  })
})

describe('trace comparison helpers', () => {
  const trace = (status, handlerInvoked, sqlCount, ids) => ({ status, handlerInvoked, sqlCount, steps: ids.map((id) => ({ id })) })

  it('summarizes only captured facts', () => {
    expect(summarizeTrace(trace(200, true, 2, ['a', 'b']))).toEqual({ status: 200, handlerInvoked: true, sqlCount: 2, challenge: null, stepIds: ['a', 'b'] })
  })

  it('lists the step kinds an experiment skipped (ignoring numeric suffixes)', () => {
    const baseline = summarizeTrace(trace(200, true, 2, ['client-request', 'auth-lookup-0', 'sql-0', 'controller', 'service-1', 'response']))
    const experiment = summarizeTrace(trace(401, false, 0, ['client-request', 'rejected-before-controller', 'response']))
    expect(skippedSteps(baseline, experiment)).toEqual(['auth-lookup', 'sql', 'controller', 'service'])
  })

  it('checks observations against documented expectations and flags mismatches', () => {
    const observed = summarizeTrace(trace(403, false, 1, []))
    expect(checkExpectation({ status: 403, handlerInvoked: false, sqlCount: 1 }, observed).every((r) => r.ok)).toBe(true)
    const wrong = checkExpectation({ status: 401, handlerInvoked: true, sqlCount: 0 }, observed)
    expect(wrong.map((r) => r.ok)).toEqual([false, false, false])
  })

  it('skips fields the catalog leaves undefined (for example an administrator\'s variable SQL count)', () => {
    const rows = checkExpectation({ status: 200, handlerInvoked: true, sqlCount: null }, summarizeTrace(trace(200, true, 5, [])))
    expect(rows.map((r) => r.field)).toEqual(['HTTP status', 'Controller invoked'])
  })

  it('chooses the role-specific expectation, falling back to the wildcard', () => {
    const catalogEntry = catalog.targets.find((t) => t.id === 'admin-users')
    expect(expectationForRole(catalogEntry.expectation, 'STUDENT').status).toBe(403)
    expect(expectationForRole(catalogEntry.expectation, 'ADMIN').status).toBe(200)
    expect(expectationForRole(catalog.targets.find((t) => t.id === 'me').expectation, 'TEACHER').status).toBe(200)
    expect(expectationForRole(null, 'ADMIN')).toBeNull()
  })
})

describe('code highlighting tokenizer', () => {
  const lines = [
    '    @Transactional(readOnly = true)',
    '    public List<TopicResponse> list() {',
    '        return topicService.list(); // delegates',
    '    @RequestMapping("/api/topics")',
    '',
  ]

  it('joining the tokens always reproduces the original line exactly', () => {
    for (const line of lines) expect(tokenizeLine(line).map((t) => t.text).join('')).toBe(line)
  })

  it('recognizes annotations, keywords, strings and comments', () => {
    const types = (line) => tokenizeLine(line).filter((t) => t.type !== 'plain').map((t) => `${t.type}:${t.text}`)
    expect(types(lines[0])).toEqual(['annotation:@Transactional', 'keyword:true'])
    expect(types(lines[1])).toContain('keyword:public')
    expect(types(lines[2])).toEqual(expect.arrayContaining(['keyword:return', 'comment:// delegates']))
    expect(types(lines[3])).toEqual(['annotation:@RequestMapping', 'string:"/api/topics"'])
  })

  it('leaves non-Java code untouched', () => {
    expect(tokenizeLine('open-in-view: false', 'yaml')).toEqual([{ type: 'plain', text: 'open-in-view: false' }])
  })

  it('every excerpt served by the backend round-trips through the tokenizer', () => {
    for (const ref of backendResource('code-references.json')) {
      for (const line of ref.excerpt.split('\n')) {
        expect(tokenizeLine(line, ref.language).map((t) => t.text).join('')).toBe(line)
      }
    }
  })
})
