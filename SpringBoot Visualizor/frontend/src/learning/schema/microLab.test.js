import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { MECHANISMS } from './diagram'
import { drawnOfStep, mechanismOfCall } from './microRun'
import { defaultsOf, toWhatIfRequest, validateWhatIf } from './microWhatIf'
import { validateLayout } from './layout'
import { validateExplain5W1H, validateScenario } from './scenario'
import { liveScenario, MODULE, NODE_IDS, REALITY, SIMULATED_NOTES, simulatedScenario, STATIC_ASSUMPTIONS, TAKEAWAYS, TOPICS } from '../scenarios/microLab.sim'

// The fixture is generated from the REAL backend and guarded by MicroFrontendFixtureTest, so these checks run against what the backend does.
const fixture = JSON.parse(readFileSync(resolve(process.cwd(), 'src/learning/scenarios/microCatalog.fixture.json'), 'utf8'))
const refIds = new Set(JSON.parse(readFileSync(resolve(process.cwd(), '../backend/src/main/resources/lab/code-references.json'), 'utf8')).map((r) => r.id))

function runLike(experiment) {
  const calls = []
  const steps = experiment.steps.map((s) => {
    const evidence = s.calls.map((c) => {
      const [from, rest] = c.split('>')
      const [to, tail] = rest.split('.')
      const [operation, kind, outcome] = tail.split(':')
      const seq = calls.length + 1
      calls.push({ seq, from, to, operation, kind, outcome, code: null, simulatedMillis: 0, handlerInvoked: true, instance: null })
      return { label: `Service call ${seq}`, value: c }
    })
    return {
      id: s.id, title: s.id, nodes: s.nodes, edgeFrom: s.edgeFrom, edgeTo: s.edgeTo, direction: 'REQUEST', provenance: s.provenance,
      explanation: 'An explanation that is long enough to be valid.', evidence, sql: [], codeRefId: s.codeRefId,
    }
  })
  return { steps, calls }
}

describe('Microservices Lab content', () => {
  it('has the eight backend topics, in the same order, each with a complete 5W1H', () => {
    expect(TOPICS.map((t) => t.id)).toEqual(fixture.topics)
    expect(TOPICS).toHaveLength(8)
    for (const t of TOPICS) expect(validateExplain5W1H(t.explain), t.id).toEqual([])
  })

  it('explains every topic at three levels and lists mistakes and what to look for', () => {
    for (const t of TOPICS) {
      for (const level of ['beginner', 'intermediate', 'advanced']) expect(t.levels[level].length, `${t.id}/${level}`).toBeGreaterThan(80)
      expect(t.mistakes.length, t.id).toBeGreaterThanOrEqual(3)
      expect(t.observations.length, t.id).toBeGreaterThanOrEqual(3)
    }
    expect(JSON.stringify(TOPICS)).not.toMatch(/\b(TODO|TBD|lorem)\b/i)
  })

  it('registers the module against the microservices Arena topic', () => {
    expect(MODULE.topicSlug).toBe('microservices')
  })

  it('covers every one of the 37 backend experiments, grouped into the right topics', () => {
    expect(fixture.experiments).toHaveLength(37)
    const perTopic = {}
    for (const e of fixture.experiments) perTopic[e.topic] = (perTopic[e.topic] ?? 0) + 1
    expect(Object.keys(perTopic).sort()).toEqual([...fixture.topics].sort())
    expect(perTopic).toMatchObject({ monolith: 4, communication: 6, gateway: 4, discovery: 4, 'database-per-service': 4, saga: 6, resilience: 5, observability: 4 })
  })

  it('links every takeaway only to experiments that exist', () => {
    const ids = new Set(fixture.experiments.map((e) => e.id))
    for (const t of TAKEAWAYS) for (const id of t.experiments) expect(ids.has(id), id).toBe(true)
    expect(TAKEAWAYS.length).toBeGreaterThanOrEqual(10)
  })

  it('states what is real and what is simulated, and never claims real infrastructure', () => {
    const all = JSON.stringify([REALITY, SIMULATED_NOTES, STATIC_ASSUMPTIONS])
    expect(REALITY.real.length).toBeGreaterThanOrEqual(3)
    expect(REALITY.simulated.join(' ')).toMatch(/not Kafka or RabbitMQ/)
    expect(all).toMatch(/not Eureka/)
    expect(all).toMatch(/not OpenTelemetry/)
    expect(all).not.toMatch(/uses Kafka|integrat\w+ with (Kafka|RabbitMQ|Eureka|OpenTelemetry)/i)
    expect(SIMULATED_NOTES.length).toBeGreaterThanOrEqual(5)
  })

  it('never contains credentials or connection details', () => {
    expect(JSON.stringify([TOPICS, SIMULATED_NOTES, STATIC_ASSUMPTIONS, TAKEAWAYS, REALITY])).not.toMatch(/localhost|3306|mohit|password=|jdbc:mysql:\/\//i)
  })
})

describe('the conceptual (SIMULATED) walkthroughs', () => {
  it('are valid scenarios with a valid layout, and every step is conceptual with no observation', () => {
    for (const t of TOPICS) {
      const scenario = simulatedScenario(t.id)
      expect(validateScenario(scenario), t.id).toEqual([])
      expect(validateLayout(scenario), t.id).toEqual([])
      expect(scenario.mode).toBe('SIMULATED')
      expect(scenario.steps.length).toBeGreaterThanOrEqual(4)
      for (const s of scenario.steps) {
        expect(s.provenance).toBe('CONCEPTUAL')
        expect(s.evidence).toEqual([])
        expect(s.sql).toEqual([])
      }
    }
  })

  it('draw only known mechanisms between known nodes', () => {
    for (const t of TOPICS) {
      for (const s of simulatedScenario(t.id).steps) {
        for (const d of s.drawn) {
          expect(MECHANISMS[d.mechanism], `${t.id}/${s.id}`).toBeDefined()
          for (const n of d.id.split('->')) expect(NODE_IDS, d.id).toContain(n)
        }
      }
    }
  })
})

describe('every real experiment, as the backend runs it, fits the diagram', () => {
  it('builds a valid LIVE scenario with a valid layout and known code references', () => {
    for (const exp of fixture.experiments) {
      const scenario = liveScenario(runLike(exp), { id: exp.id, title: exp.id })
      expect(validateScenario(scenario, { codeRefIds: refIds }), exp.id).toEqual([])
      expect(validateLayout(scenario), exp.id).toEqual([])
    }
  })

  it('only ever uses nodes the diagram has, and only ever draws lines between them', () => {
    for (const exp of fixture.experiments) {
      const run = runLike(exp)
      for (const s of run.steps) {
        for (const n of s.nodes) expect(NODE_IDS, `${exp.id}/${s.id}`).toContain(n)
        for (const d of drawnOfStep(s, run.calls)) for (const n of d.id.split('->')) expect(NODE_IDS, `${exp.id}/${s.id} ${d.id}`).toContain(n)
      }
    }
  })

  it('classifies every recorded call into a known mechanism', () => {
    const seen = new Set()
    for (const exp of fixture.experiments) {
      for (const c of runLike(exp).calls) {
        const m = mechanismOfCall(c)
        expect(MECHANISMS[m], `${exp.id} ${c.operation}`).toBeDefined()
        seen.add(m)
      }
    }
    // the real runs contain every mechanism the legend teaches, except a bare local transaction (which is not a call)
    expect([...seen].sort()).toEqual(['COMPENSATION', 'EVENT', 'OUTBOX', 'REFUSED', 'SYNC'])
  })

  it('shows a local transaction on the database of every service that ran one', () => {
    let local = 0
    for (const exp of fixture.experiments) {
      const run = runLike(exp)
      for (const s of run.steps) local += drawnOfStep(s, run.calls).filter((d) => d.mechanism === 'LOCAL_TX' || d.labels.includes('LOCAL_TX')).length
    }
    expect(local).toBeGreaterThan(20)
  })

  it('keeps provenance as the backend assigned it: a conceptual step never draws a call', () => {
    for (const exp of fixture.experiments) {
      for (const s of exp.steps) {
        if (s.provenance === 'CONCEPTUAL') expect(s.calls, `${exp.id}/${s.id}`).toEqual([])
      }
    }
  })
})

describe('What-If options mirror the backend', () => {
  it('every option has a valid default and the request carries exactly the option keys', () => {
    let withOptions = 0
    for (const exp of fixture.experiments) {
      const defaults = defaultsOf(exp.whatIfs)
      expect(validateWhatIf(exp.whatIfs, defaults), exp.id).toEqual({})
      expect(Object.keys(toWhatIfRequest(exp.whatIfs, defaults)).sort()).toEqual(exp.whatIfs.map((o) => o.key).sort())
      for (const o of exp.whatIfs) {
        expect(['BOOLEAN', 'INT', 'CHOICE']).toContain(o.kind)
        if (o.kind === 'INT') {
          expect(o.min).toBeLessThanOrEqual(Number(o.defaultValue))
          expect(Number(o.defaultValue)).toBeLessThanOrEqual(o.max)
        }
        if (o.kind === 'CHOICE') expect(o.choices).toContain(o.defaultValue)
      }
      if (exp.whatIfs.length) withOptions++
    }
    expect(withOptions).toBeGreaterThanOrEqual(15)
  })

  it('rejects what the server would reject, and sends strings', () => {
    const options = [
      { key: 'n', kind: 'INT', min: 50, max: 500, defaultValue: '100' },
      { key: 'b', kind: 'BOOLEAN', defaultValue: 'true' },
      { key: 'c', kind: 'CHOICE', choices: ['a', 'b'], defaultValue: 'a' },
    ]
    expect(Object.keys(validateWhatIf(options, { n: '49', b: 'true', c: 'a' }))).toEqual(['n'])
    expect(Object.keys(validateWhatIf(options, { n: '501', b: 'maybe', c: 'z' })).sort()).toEqual(['b', 'c', 'n'])
    expect(Object.keys(validateWhatIf(options, { n: '1 or 1=1', b: 'true', c: 'a' }))).toEqual(['n'])
    expect(Object.keys(validateWhatIf(options, { n: '', b: 'true', c: 'a' }))).toEqual(['n'])
    expect(toWhatIfRequest(options, { n: 75, b: false, c: 'b' })).toEqual({ n: '75', b: 'false', c: 'b' })
  })
})
