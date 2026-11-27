import { describe, expect, it } from 'vitest'
import * as jwt from '../scenarios/jwtSecurity.sim'
import * as rest from '../scenarios/restLifecycle.sim'
import {
  branchStates,
  edgeAppearance,
  edgeMarkers,
  edgeStates,
  flowCaption,
  nodeAriaLabel,
  nodeStates,
  showsPacket,
  stageOrder,
} from './diagram'
import { buildEdges, buildNodes } from './flowElements'
import { canvasHeight, COMPACT_STEP, COL_STEP, NODE_H, NODE_W, ROW_STEP, validateLayout } from './layout'

const SCENARIOS = [
  ['REST lifecycle', rest.SIMULATED_SCENARIO],
  ['JWT security', jwt.SIMULATED_SCENARIO],
]

const step = (id, nodes, from, to, direction = 'REQUEST') => ({
  id, title: id, nodes, edgeFrom: from ?? null, edgeTo: to ?? null, direction,
  provenance: 'CAPTURED', explanation: 'x'.repeat(30), evidence: [], sql: [], codeRefId: null,
})

/** The steps the backend assembles for a request the security filter chain rejects (see LifecycleStepAssembler). */
const REJECTED_401 = {
  ...rest.SIMULATED_SCENARIO,
  id: 'rest-live-401',
  mode: 'LIVE',
  steps: [
    step('client-request', ['client'], 'client', 'securityFilters'),
    step('security-filter-chain', ['securityFilters'], null, null),
    step('rejected-before-controller', ['securityFilters'], 'client', 'securityFilters', 'RESPONSE'),
    step('rejection-mechanism', ['securityFilters'], null, null, 'RESPONSE'),
    step('response', ['client'], 'client', 'securityFilters', 'RESPONSE'),
  ],
}

describe.each(SCENARIOS)('%s diagram: structure', (_name, scenario) => {
  it('is a valid scenario and layout (including non-overlapping layers in both layouts)', () => {
    expect(validateLayout(scenario)).toEqual([])
  })

  it('maps every step to exactly its nodes and its edge', () => {
    scenario.steps.forEach((s, i) => {
      const active = buildNodes({ scenario, index: i, compact: false })
        .filter((n) => n.type === 'lab' && n.data.state === 'active')
        .map((n) => n.id)
        .sort()
      expect(active, s.id).toEqual([...s.nodes].sort())
      const activeEdges = buildEdges({ scenario, index: i, compact: false, reducedMotion: false })
        .filter((e) => e.data.state === 'active' && scenario.edges.some((x) => x.id === e.id))
        .map((e) => e.id)
      expect(activeEdges, s.id).toEqual(s.edgeFrom ? [`${s.edgeFrom}->${s.edgeTo}`] : [])
    })
  })

  it('classifies every node as active, completed, upcoming or unreached, and the sets are consistent', () => {
    scenario.steps.forEach((s, i) => {
      const states = nodeStates(scenario, i)
      for (const [id, state] of states) {
        const earlier = scenario.steps.slice(0, i).some((x) => x.nodes.includes(id))
        const later = scenario.steps.slice(i + 1).some((x) => x.nodes.includes(id))
        const expected = s.nodes.includes(id) ? 'active' : earlier ? 'completed' : later ? 'upcoming' : 'unreached'
        expect(state, `${s.id}/${id}`).toBe(expected)
      }
    })
  })

  it('starts with only the first step active and everything else upcoming, and ends with nothing upcoming', () => {
    const first = nodeStates(scenario, 0)
    expect([...first.values()].filter((v) => v === 'active')).toHaveLength(scenario.steps[0].nodes.length)
    expect([...first.values()].filter((v) => v === 'completed')).toHaveLength(0)
    const last = nodeStates(scenario, scenario.steps.length - 1)
    expect([...last.values()].filter((v) => v === 'upcoming')).toHaveLength(0)
  })

  it('draws each edge from source to target with an arrowhead at the target', () => {
    const edges = buildEdges({ scenario, index: 0, compact: false, reducedMotion: false })
    for (const e of scenario.edges) {
      const built = edges.find((x) => x.id === e.id)
      expect(built.source).toBe(e.source)
      expect(built.target).toBe(e.target)
      expect(built.markerEnd, `${e.id} has an arrowhead`).toBeTruthy()
      expect(built.markerStart).toBeUndefined()
    }
  })

  it('success arrows only ever point right or down, so execution order never runs backwards', () => {
    const { positions, handles } = scenario.layout.wide
    for (const e of scenario.edges) {
      if (scenario.layout.edgeMeta?.[e.id]?.tone) continue // lookups and rejection branches are side paths
      const s = positions[e.source]
      const t = positions[e.target]
      expect(t.x >= s.x && t.y >= s.y, `${e.id} goes right or down`).toBe(true)
      expect(handles[e.id].length).toBe(2)
    }
  })

  it('numbers the stages along the success path, following only the main arrows', () => {
    const next = (id) => scenario.edges.find((e) => e.source === id && !scenario.layout.edgeMeta?.[e.id]?.tone)?.target
    const chain = []
    for (let id = 'client'; id; id = next(id)) chain.push(id)
    expect(stageOrder(scenario.layout).map((s) => s.nodeId)).toEqual(chain.filter((id) => scenario.layout.nodeMeta[id].stage != null))
  })

  it('gives each layer a lane and keeps every lane member contiguous in the phone column', () => {
    const order = scenario.layout.compact.order
    for (const lane of scenario.layout.lanes) {
      const idx = lane.nodes.map((n) => order.indexOf(n)).sort((a, b) => a - b)
      expect(idx[idx.length - 1] - idx[0], `${lane.id} is contiguous`).toBe(idx.length - 1)
    }
  })

  it('uses a distinct colour family per layer and labels every lane', () => {
    for (const lane of scenario.layout.lanes) expect(lane.label.length).toBeGreaterThan(2)
    const kinds = new Set(Object.values(scenario.layout.nodeMeta).map((m) => m.kind))
    expect(kinds.size).toBeGreaterThanOrEqual(4)
  })
})

describe('REST lifecycle diagram: order and rejection paths', () => {
  it('keeps the service, repository, Hibernate, database order in stages, edges and steps', () => {
    const order = stageOrder(rest.LAYOUT).map((s) => s.nodeId)
    expect(order).toEqual(['client', 'securityFilters', 'dispatcher', 'controller', 'service', 'repository', 'hibernate', 'database'])
    expect(rest.EDGES.map((e) => e.id)).toEqual(expect.arrayContaining(['controller->service', 'service->repository', 'repository->hibernate', 'hibernate->database']))
    const ids = rest.SIMULATED_STEPS.map((s) => s.id)
    for (const [a, b] of [['controller', 'service'], ['service', 'repository'], ['repository', 'hibernate'], ['hibernate', 'database']]) {
      expect(ids.indexOf(a), `${a} before ${b}`).toBeLessThan(ids.indexOf(b))
    }
  })

  it('numbers the ten conceptual stages across the eight nodes', () => {
    const stages = Object.fromEntries(Object.entries(rest.LAYOUT.nodeMeta).map(([id, m]) => [id, m.stage]))
    expect(stages).toEqual({ client: 1, securityFilters: '2–3', dispatcher: '4–5', controller: 6, service: 7, repository: 8, hibernate: 9, database: 10 })
    expect(rest.LAYOUT.nodeMeta.dispatcher.chips).toEqual(['HandlerMapping', 'HandlerAdapter'])
    expect(rest.LAYOUT.lanes.find((l) => l.id === 'tomcat').nodes).toEqual(['securityFilters', 'dispatcher'])
  })

  it('has a red 401/403 branch from the security filter chain back to the client that never reaches the controller', () => {
    const [branch] = rest.LAYOUT.branches
    expect(branch).toMatchObject({ source: 'securityFilters', target: 'client', tone: 'error' })
    expect(branch.label).toContain('401')
    expect(branch.stepIds).toEqual(expect.arrayContaining(['rejected-before-controller', 'rejection-mechanism']))
    expect(rest.LAYOUT.branches.some((b) => b.target === 'controller' || b.source === 'controller')).toBe(false)
  })

  it('lights the rejection branch only on the steps that name it', () => {
    const lit = REJECTED_401.steps.map((_, i) => branchStates(rest.LAYOUT, REJECTED_401, i).get('securityFilters->client'))
    expect(lit).toEqual(['upcoming', 'upcoming', 'active', 'active', 'completed'])
  })

  it('shows the branch as an unused alternative when the request was not rejected', () => {
    const accepted = { ...rest.SIMULATED_SCENARIO, steps: rest.SIMULATED_STEPS.filter((s) => s.id !== 'authorization') }
    accepted.steps.forEach((_, i) => expect(branchStates(rest.LAYOUT, accepted, i).get('securityFilters->client')).toBe('unused'))
  })

  it('the simulated walkthrough lights the branch on its authorization step (401 and 403 are explained there)', () => {
    const i = rest.SIMULATED_STEPS.findIndex((s) => s.id === 'authorization')
    expect(branchStates(rest.LAYOUT, rest.SIMULATED_SCENARIO, i).get('securityFilters->client')).toBe('active')
    const edges = buildEdges({ scenario: rest.SIMULATED_SCENARIO, index: i, compact: false, reducedMotion: false })
    const branch = edges.find((e) => e.id === 'securityFilters->client')
    expect(branch.data).toMatchObject({ state: 'active', tone: 'error' })
    expect(branch.markerEnd).toBeTruthy()
  })

  it('a request rejected with 401 never reaches the controller, service, repository, Hibernate or database', () => {
    REJECTED_401.steps.forEach((_, i) => {
      const states = nodeStates(REJECTED_401, i)
      for (const id of ['dispatcher', 'controller', 'service', 'repository', 'hibernate', 'database']) {
        expect(states.get(id), `${id} at step ${i}`).toBe('unreached')
      }
    })
    const nodes = buildNodes({ scenario: REJECTED_401, index: 0, compact: false })
    expect(nodes.find((n) => n.id === 'controller').data.state).toBe('unreached')
    expect(nodeAriaLabel({ label: 'Controller', stage: 6, state: 'unreached' })).toBe('Stage 6: Controller (not reached in this request)')
  })

  it('draws the rejection response as the active edge pointing back at the client', () => {
    const i = 2
    const edges = buildEdges({ scenario: REJECTED_401, index: i, compact: false, reducedMotion: false })
    const e = edges.find((x) => x.id === 'client->securityFilters')
    expect(e.data).toMatchObject({ state: 'active', direction: 'RESPONSE' })
    expect(e.markerStart).toBeTruthy()
    expect(e.markerEnd).toBeUndefined()
  })
})

describe('JWT diagram: stages, branches and the login/bearer distinction', () => {
  it('numbers the nine bearer-authentication stages in the specified order', () => {
    const m = jwt.LAYOUT.nodeMeta
    expect([m.client.stage, m.bearerFilter.stage, m.authManager.stage, m.jwtDecoder.stage, m.converter.stage, m.securityContext.stage, m.authorization.stage, m.controller.stage])
      .toEqual([1, 2, '3–4', 5, 6, 7, 8, 9])
    expect(m.authManager.chips.map((c) => (typeof c === 'string' ? c : c.text))).toEqual(['ProviderManager', 'JwtAuthenticationProvider'])
  })

  it('separates the 401 and 403 branches, both red and labelled, leaving from the authorization filter', () => {
    const edges = jwt.EDGES.filter((e) => e.secondary)
    expect(edges.map((e) => e.id).sort()).toEqual(['authorization->accessDenied', 'authorization->entryPoint'])
    expect(jwt.LAYOUT.edgeMeta['authorization->entryPoint']).toMatchObject({ tone: 'error' })
    expect(jwt.LAYOUT.edgeMeta['authorization->entryPoint'].label).toContain('401')
    expect(jwt.LAYOUT.edgeMeta['authorization->accessDenied']).toMatchObject({ tone: 'error' })
    expect(jwt.LAYOUT.edgeMeta['authorization->accessDenied'].label).toContain('403')
    expect(jwt.LAYOUT.nodeMeta.entryPoint).toMatchObject({ kind: 'error', stage: '401' })
    expect(jwt.LAYOUT.nodeMeta.accessDenied).toMatchObject({ kind: 'error', stage: '403' })
  })

  it('the controller is reachable only through the authorization filter', () => {
    expect(jwt.EDGES.filter((e) => e.target === 'controller').map((e) => e.source)).toEqual(['authorization'])
  })

  it('never draws UserDetailsService or PasswordEncoder, and says they belong to login only', () => {
    const drawn = JSON.stringify([jwt.NODES, jwt.LAYOUT.nodeMeta, jwt.LAYOUT.lanes])
    expect(drawn).not.toMatch(/UserDetailsService|PasswordEncoder|DaoAuthenticationProvider/)
    expect(jwt.LAYOUT.legend.note).toMatch(/never use UserDetailsService or PasswordEncoder/)
    expect(jwt.LAYOUT.legend.note).toMatch(/login/i)
  })

  it('a 403 trace marks the access-denied handler active and leaves the 401 entry point unreached', () => {
    const forbidden = {
      ...jwt.SIMULATED_SCENARIO,
      mode: 'LIVE',
      steps: [
        step('client-request', ['client'], 'client', 'bearerFilter'),
        step('token-validation', ['jwtDecoder'], 'authManager', 'jwtDecoder'),
        step('authorization-decision', ['authorization'], null, null),
        step('response-403', ['accessDenied'], 'authorization', 'accessDenied'),
      ],
    }
    const states = nodeStates(forbidden, 3)
    expect(states.get('accessDenied')).toBe('active')
    expect(states.get('entryPoint')).toBe('unreached')
    expect(states.get('controller')).toBe('unreached')
    expect(edgeStates(forbidden, 3).get('authorization->accessDenied')).toBe('active')
    expect(edgeStates(forbidden, 3).get('authorization->entryPoint')).toBe('unused')
  })
})

describe('edge states, appearance and motion', () => {
  it('classifies edges as active, completed, upcoming or unused', () => {
    const s = rest.SIMULATED_SCENARIO
    const i = s.steps.findIndex((x) => x.id === 'controller')
    const states = edgeStates(s, i)
    expect(states.get('dispatcher->controller')).toBe('active')
    expect(states.get('securityFilters->dispatcher')).toBe('completed')
    expect(states.get('service->repository')).toBe('upcoming')
    expect(edgeStates(REJECTED_401, 0).get('controller->service')).toBe('unused')
  })

  it('colours edges by execution state with the design tokens: lime active, green completed, red rejection, muted upcoming', () => {
    expect(edgeAppearance({ state: 'active', direction: 'REQUEST' }).stroke).toBe('var(--color-accent-primary)')
    expect(edgeAppearance({ state: 'active', tone: 'error', direction: 'REQUEST' }).stroke).toBe('var(--color-error)')
    expect(edgeAppearance({ state: 'completed', direction: 'REQUEST' }).stroke).toBe('var(--color-success)')
    expect(edgeAppearance({ state: 'upcoming', direction: 'REQUEST' }).stroke).toBe('var(--color-border-strong)')
    expect(edgeAppearance({ state: 'active', tone: 'lookup', direction: 'REQUEST' }).dash).toBeTruthy()
    expect(edgeAppearance({ state: 'unused', direction: 'REQUEST' }).width).toBeLessThan(edgeAppearance({ state: 'active', direction: 'REQUEST' }).width)
    // direction is shown by the arrowhead, not by a colour that could be confused with a state
    expect(edgeAppearance({ state: 'active', direction: 'RESPONSE' }).stroke).toBe(edgeAppearance({ state: 'active', direction: 'REQUEST' }).stroke)
  })

  it('never uses a raw hex colour for an edge', () => {
    for (const state of ['active', 'completed', 'upcoming', 'unused']) {
      for (const tone of [undefined, 'error', 'lookup']) {
        const look = edgeAppearance({ state, tone, direction: 'REQUEST' })
        expect(look.stroke).toMatch(/^var\(--/)
        expect(look.arrow).toMatch(/^var\(--/)
      }
    }
  })

  it('points the arrowhead at the source for an active response and at the target otherwise', () => {
    expect(edgeMarkers({ state: 'active', direction: 'RESPONSE' })).toEqual({ end: false, start: true })
    expect(edgeMarkers({ state: 'active', direction: 'REQUEST' })).toEqual({ end: true, start: false })
    expect(edgeMarkers({ state: 'upcoming', direction: 'RESPONSE' })).toEqual({ end: true, start: false })
  })

  it('animates a packet only on the active edge and never with reduced motion', () => {
    expect(showsPacket({ state: 'active', reducedMotion: false })).toBe(true)
    expect(showsPacket({ state: 'active', reducedMotion: true })).toBe(false)
    for (const state of ['completed', 'upcoming', 'unused']) expect(showsPacket({ state, reducedMotion: false })).toBe(false)
  })

  it('with reduced motion the direction is still readable from the arrowhead and the caption', () => {
    const s = rest.SIMULATED_SCENARIO
    const i = s.steps.length - 1 // response step
    const edge = buildEdges({ scenario: s, index: i, compact: false, reducedMotion: true }).find((e) => e.data.state === 'active')
    expect(edge.data.reducedMotion).toBe(true)
    expect(showsPacket({ state: edge.data.state, reducedMotion: edge.data.reducedMotion })).toBe(false)
    expect(edge.markerStart).toBeTruthy()
    expect(flowCaption(s, i).movement).toMatch(/^Response: /)
  })

  it('words the movement and the next step for the caption', () => {
    const s = rest.SIMULATED_SCENARIO
    expect(flowCaption(s, 0)).toMatchObject({ movement: 'Request: Browser / client → Security filter chain', next: s.steps[1].title })
    expect(flowCaption(s, s.steps.length - 1).next).toBeNull()
    expect(flowCaption(s, 1).movement).toBeNull()
  })
})

describe('responsive layout assumptions', () => {
  it.each(SCENARIOS)('%s: the desktop diagram is wide and short, and asks for a high enough breakpoint', (_n, scenario) => {
    const { positions } = scenario.layout.wide
    const xs = Object.values(positions).map((p) => p.x)
    const ys = Object.values(positions).map((p) => p.y)
    const width = Math.max(...xs) + NODE_W
    const height = Math.max(...ys) + NODE_H
    expect(width).toBeLessThanOrEqual(5 * COL_STEP + NODE_W)
    expect(canvasHeight(scenario.layout, false)).toBeGreaterThanOrEqual(height)
    expect(canvasHeight(scenario.layout, false)).toBeLessThanOrEqual(600)
    expect(scenario.layout.fullWidth).toBe(true)
    expect(scenario.layout.minWidth).toBeGreaterThanOrEqual(900)
  })

  it.each(SCENARIOS)('%s: the phone layout is a single column at natural size with room for every node', (_n, scenario) => {
    const nodes = buildNodes({ scenario, index: 0, compact: true }).filter((n) => n.type === 'lab')
    expect(new Set(nodes.map((n) => n.position.x))).toEqual(new Set([0]))
    const ys = nodes.map((n) => n.position.y).sort((a, b) => a - b)
    expect(new Set(ys).size).toBe(ys.length)
    expect(Math.min(...ys.slice(1).map((y, i) => y - ys[i]))).toBeGreaterThanOrEqual(COMPACT_STEP)
    expect(canvasHeight(scenario.layout, true)).toBeGreaterThanOrEqual(Math.max(...ys) + NODE_H)
  })

  it.each(SCENARIOS)('%s: rows are far enough apart for the lane headers', (_n, scenario) => {
    expect(ROW_STEP).toBeGreaterThanOrEqual(NODE_H + 44)
    expect(scenario.layout.heights.wide).toBeGreaterThan(0)
  })

  it('compact edges between non-adjacent nodes have explicit side handles', () => {
    for (const scenario of [rest, jwt]) {
      const order = scenario.LAYOUT.compact.order
      for (const e of scenario.EDGES) {
        if (order.indexOf(e.target) - order.indexOf(e.source) !== 1) expect(scenario.LAYOUT.compact.handles[e.id]).toBeTruthy()
      }
    }
  })
})
