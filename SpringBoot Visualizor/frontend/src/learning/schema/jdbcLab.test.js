import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { COMPARISON, DIAGRAMS, EVIDENCE_LABELS, liveScenario, MODULE, SIMULATED_NOTES, simulatedScenario, STATIC_ASSUMPTIONS, TAKEAWAYS, TOPICS } from '../scenarios/jdbcLab.sim'
import { poolMetricsOf, statementsSoFar, totalStatements, transactionStateAt } from './jdbcRun'
import { nodeStates } from './diagram'
import { validateLayout } from './layout'
import { validateExplain5W1H, validateScenario } from './scenario'

const backend = (...parts) => resolve(process.cwd(), '../backend', ...parts)
const resource = (name) => JSON.parse(readFileSync(backend('src/main/resources/lab', name), 'utf8'))
const catalog = resource('jdbc-lab-catalog.json')
const refIds = new Set(resource('code-references.json').map((r) => r.id))

describe('JDBC Lab content', () => {
  it('the module is registered against the jdbc Arena topic', () => {
    expect(MODULE.topicSlug).toBe('jdbc')
  })

  it('has seven topics, each with a complete 5W1H and a catalogue group', () => {
    expect(TOPICS).toHaveLength(7)
    expect(new Set(TOPICS.map((t) => t.id)).size).toBe(7)
    const groups = new Set(catalog.groups.map((g) => g.id))
    for (const t of TOPICS) {
      expect(validateExplain5W1H(t.explain), t.id).toEqual([])
      expect(groups.has(t.group), t.group).toBe(true)
    }
    expect(catalog.groups).toHaveLength(7)
  })

  it('diagram node and edge ids equal the server catalogue, so every server step fits its diagram', () => {
    expect(Object.keys(DIAGRAMS).sort()).toEqual(Object.keys(catalog.diagrams).sort())
    for (const [id, d] of Object.entries(DIAGRAMS)) {
      expect(d.nodes.map((n) => n.id).sort(), id).toEqual([...catalog.diagrams[id].nodes].sort())
      expect(d.edges.map((e) => e.id).sort(), id).toEqual([...catalog.diagrams[id].edges].sort())
    }
    for (const e of catalog.experiments) expect(DIAGRAMS[e.diagram], e.id).toBeDefined()
  })

  it('every SIMULATED walkthrough is a valid, conceptual scenario with a valid layout', () => {
    for (const t of TOPICS) {
      const scenario = simulatedScenario(t.id)
      expect(validateScenario(scenario, { codeRefIds: refIds }), t.id).toEqual([])
      expect(validateLayout(scenario), t.id).toEqual([])
      expect(scenario.mode).toBe('SIMULATED')
      for (const s of scenario.steps) {
        expect(s.provenance).toBe('CONCEPTUAL')
        expect(s.sql).toEqual([])
        expect(s.evidence).toEqual([])
      }
    }
  })

  it('a LIVE run is a valid scenario on its experiment diagram', () => {
    const info = catalog.experiments.find((e) => e.id === 'jdbc-commit')
    const run = { experimentId: info.id, steps: [
      { id: 'a', title: 'open', nodes: ['connectionA', 'transaction'], edgeFrom: 'connectionA', edgeTo: 'transaction', direction: 'REQUEST', provenance: 'CAPTURED', explanation: 'x'.repeat(30), evidence: [], sql: [{ index: 1, sql: 'update t set a = ?', phase: 'JDBC', trigger: 'A: executeUpdate' }], codeRefId: null },
      { id: 'b', title: 'commit', nodes: ['database'], edgeFrom: null, edgeTo: null, direction: 'REQUEST', provenance: 'DERIVED', explanation: 'y'.repeat(30), evidence: [], sql: [], codeRefId: null },
    ] }
    const scenario = liveScenario(run, info)
    expect(validateScenario(scenario)).toEqual([])
    expect(validateLayout(scenario)).toEqual([])
    expect(nodeStates(scenario, 1).get('database')).toBe('active')
  })

  it('every takeaway links only to experiments that exist', () => {
    const ids = new Set(catalog.experiments.map((e) => e.id))
    for (const t of TAKEAWAYS) for (const id of t.experiments) expect(ids.has(id), id).toBe(true)
    expect(catalog.experiments).toHaveLength(27)
  })

  it('states its limits and avoids placeholder text', () => {
    expect(SIMULATED_NOTES.length).toBeGreaterThanOrEqual(4)
    expect(STATIC_ASSUMPTIONS.length).toBeGreaterThanOrEqual(5)
    expect(JSON.stringify([SIMULATED_NOTES, STATIC_ASSUMPTIONS, TOPICS])).not.toMatch(/\b(TODO|TBD)\b/)
    expect(COMPARISON.rows).toHaveLength(6)
    for (const r of COMPARISON.rows) expect(r).toHaveLength(COMPARISON.columns.length)
  })

  it('never contains credentials or connection details', () => {
    const text = JSON.stringify([TOPICS, SIMULATED_NOTES, STATIC_ASSUMPTIONS, TAKEAWAYS])
    expect(text).not.toMatch(/localhost|3306|mohit|password=|jdbc:mysql:\/\//i)
  })
})

describe('JDBC run helpers', () => {
  const step = (sqlCount, evidence = []) => ({ sql: Array.from({ length: sqlCount }, (_, i) => ({ index: i })), evidence })

  it('counts statements from the captured steps', () => {
    const steps = [step(1), step(0), step(2)]
    expect(statementsSoFar(steps, 0)).toBe(1)
    expect(statementsSoFar(steps, 2)).toBe(3)
    expect(totalStatements(steps)).toBe(3)
  })

  it('reads the transaction state from the server evidence and keeps it until the next report', () => {
    const label = EVIDENCE_LABELS.transactionState
    const steps = [
      step(0, [{ label, value: 'OPEN: changes exist only inside this transaction (not committed)' }]),
      step(0),
      step(0, [{ label, value: 'ROLLED BACK: the changes were discarded' }]),
    ]
    expect(transactionStateAt(steps, 1).key).toBe('OPEN')
    expect(transactionStateAt(steps, 2).key).toBe('ROLLED BACK')
    expect(transactionStateAt([step(0)], 0)).toBeNull()
  })

  it('parses pool metrics and ignores anything else', () => {
    const ok = step(0, [{ label: EVIDENCE_LABELS.pool, value: 'active=1 idle=0 total=1 waiting=2 (maximum 3)' }])
    expect(poolMetricsOf(ok)).toEqual({ active: 1, idle: 0, total: 1, waiting: 2, maximum: 3 })
    expect(poolMetricsOf(step(0, [{ label: EVIDENCE_LABELS.pool, value: 'garbled' }]))).toBeNull()
    expect(poolMetricsOf(step(0))).toBeNull()
  })
})
