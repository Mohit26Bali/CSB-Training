import { readdirSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { FAMILIES, FLOW_EDGES, FLOW_NODES, liveFlowScenario, MODULE, SIMULATED_NOTES, SIMULATED_SCENARIO, STATIC_ASSUMPTIONS, TAKEAWAYS, TOPICS } from '../scenarios/jpaLab.sim'
import { statementsSoFar, totalStatements } from './jpaRun'
import { nodeStates } from './diagram'
import { validateLayout } from './layout'
import { validateExplain5W1H, validateScenario } from './scenario'

const backend = (...parts) => resolve(process.cwd(), '../backend', ...parts)
const resource = (name) => JSON.parse(readFileSync(backend('src/main/resources/lab', name), 'utf8'))
const catalog = resource('jpa-lab-catalog.json')
const refIds = new Set(resource('code-references.json').map((r) => r.id))
const entityClasses = readdirSync(backend('src/main/java/com/springlab/lab/jpa/entity')).map((f) => f.replace('.java', '')).filter((n) => n !== 'LabBase')

describe('JPA Relationship Lab content', () => {
  it('every topic has the mandatory complete 5W1H', () => {
    expect(TOPICS.length).toBeGreaterThanOrEqual(8)
    for (const t of TOPICS) expect(validateExplain5W1H(t.explain), t.id).toEqual([])
    expect(new Set(TOPICS.map((t) => t.id)).size).toBe(TOPICS.length)
    expect(TOPICS.map((t) => t.id)).toEqual(expect.arrayContaining(['orm-jpa', 'one-to-one', 'owning-side', 'many-to-many', 'cascade', 'fetching', 'n-plus-one', 'persistence-context']))
  })

  it('the SIMULATED walkthrough is valid, conceptual and carries no observation', () => {
    expect(validateScenario(SIMULATED_SCENARIO, { codeRefIds: refIds })).toEqual([])
    expect(validateLayout(SIMULATED_SCENARIO)).toEqual([])
    for (const s of SIMULATED_SCENARIO.steps) {
      expect(s.provenance).toBe('CONCEPTUAL')
      expect(s.sql).toEqual([])
      expect(s.evidence).toEqual([])
    }
    expect(SIMULATED_NOTES.length).toBeGreaterThanOrEqual(5)
    expect(JSON.stringify(SIMULATED_NOTES)).not.toMatch(/\b(TODO|TBD)\b/)
  })

  it('the persistence flow nodes are the ones the server uses in its steps', () => {
    expect(FLOW_NODES.map((n) => n.id).sort()).toEqual([...catalog.flowNodes].sort())
    expect(FLOW_EDGES.map((e) => e.id)).toEqual(expect.arrayContaining(['application->entityManager', 'entityManager->persistenceContext', 'entityManager->jdbc', 'jdbc->database']))
  })

  it('a LIVE run on the flow diagram is a valid scenario with the same layout', () => {
    const run = { experimentId: 'dirty-checking', steps: [
      { id: 'find', title: 'find', nodes: ['entityManager'], edgeFrom: 'entityManager', edgeTo: 'jdbc', direction: 'REQUEST', provenance: 'CAPTURED', explanation: 'x'.repeat(30), evidence: [], sql: [{ index: 1, sql: 'select 1', phase: 'EXPERIMENT', trigger: null }], codeRefId: null },
      { id: 'change', title: 'change', nodes: ['persistenceContext'], edgeFrom: null, edgeTo: null, direction: 'REQUEST', provenance: 'DERIVED', explanation: 'y'.repeat(30), evidence: [], sql: [], codeRefId: null },
    ] }
    const scenario = liveFlowScenario(run, 'Dirty checking')
    expect(validateScenario(scenario)).toEqual([])
    expect(nodeStates(scenario, 1).get('persistenceContext')).toBe('active')
    expect(nodeStates(scenario, 1).get('entityManager')).toBe('completed')
  })

  it('families and catalogue experiments focus only on entities that exist in the backend', () => {
    for (const f of FAMILIES) for (const e of f.focus) expect(entityClasses, `${f.id}/${e}`).toContain(e)
    for (const e of catalog.experiments) for (const focus of e.focus) expect(entityClasses, `${e.id}/${focus}`).toContain(focus)
    expect(entityClasses).toHaveLength(11)
  })

  it('every takeaway links only to experiments that exist, and every group has experiments', () => {
    const ids = new Set(catalog.experiments.map((e) => e.id))
    for (const t of TAKEAWAYS) for (const id of t.experiments) expect(ids.has(id), id).toBe(true)
    for (const g of catalog.groups) expect(catalog.experiments.some((e) => e.group === g.id), g.id).toBe(true)
    expect(catalog.experiments).toHaveLength(25)
  })

  it('names the Arena topic that exists and states its assumptions', () => {
    expect(MODULE.topicSlug).toBe('jpa-hibernate')
    expect(readFileSync(backend('src/main/resources/db/migration/V2__challenge_arena.sql'), 'utf8')).toContain("'jpa-hibernate'")
    expect(STATIC_ASSUMPTIONS.join(' ')).toMatch(/jpa_lab_\*/)
    expect(STATIC_ASSUMPTIONS.join(' ')).toMatch(/placeholders/)
  })

  it('counts statements from the captured steps', () => {
    const steps = [{ sql: [1, 2] }, { sql: [] }, { sql: [3] }]
    expect(statementsSoFar(steps, 0)).toBe(2)
    expect(statementsSoFar(steps, 1)).toBe(2)
    expect(statementsSoFar(steps, 2)).toBe(3)
    expect(totalStatements(steps)).toBe(3)
  })
})
