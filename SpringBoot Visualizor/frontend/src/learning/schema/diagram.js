/**
 * Pure presentation logic for the architecture diagrams (no React, no React Flow), so it can be tested directly.
 *
 * Everything here is derived from the scenario's own steps: which node or edge a step highlights is already part of the
 * scenario (and, for LIVE traces, of the backend's assembled steps). Nothing in this file adds an observation.
 */

/**
 * One colour family per architectural layer. Class names are written out in full so Tailwind can see them. Layer colours
 * identify WHERE a component lives; they are never used for execution state (that is lime, green and red).
 */
export const KIND_STYLES = {
  client: {
    label: 'Client',
    node: 'border-layer-client/45', bar: 'bg-layer-client', badge: 'bg-layer-client text-on-accent', accent: 'text-layer-client',
    lane: 'border-layer-client/30 bg-layer-client/5', laneLabel: 'text-layer-client',
  },
  security: {
    label: 'Spring Security',
    node: 'border-layer-security/45', bar: 'bg-layer-security', badge: 'bg-layer-security text-on-accent', accent: 'text-layer-security',
    lane: 'border-layer-security/30 bg-layer-security/5', laneLabel: 'text-layer-security',
  },
  mvc: {
    label: 'Spring MVC',
    node: 'border-layer-mvc/45', bar: 'bg-layer-mvc', badge: 'bg-layer-mvc text-on-accent', accent: 'text-layer-mvc',
    lane: 'border-layer-mvc/30 bg-layer-mvc/5', laneLabel: 'text-layer-mvc',
  },
  business: {
    label: 'Application code',
    node: 'border-layer-business/45', bar: 'bg-layer-business', badge: 'bg-layer-business text-on-accent', accent: 'text-layer-business',
    lane: 'border-layer-business/30 bg-layer-business/5', laneLabel: 'text-layer-business',
  },
  persistence: {
    label: 'Persistence',
    node: 'border-layer-persistence/45', bar: 'bg-layer-persistence', badge: 'bg-layer-persistence text-on-accent', accent: 'text-layer-persistence',
    lane: 'border-layer-persistence/30 bg-layer-persistence/5', laneLabel: 'text-layer-persistence',
  },
  database: {
    label: 'Database',
    node: 'border-layer-database/45', bar: 'bg-layer-database', badge: 'bg-layer-database text-on-accent', accent: 'text-layer-database',
    lane: 'border-layer-database/30 bg-layer-database/5', laneLabel: 'text-layer-database',
  },
  error: {
    label: 'Rejection',
    node: 'border-error/50', bar: 'bg-error', badge: 'bg-error text-on-accent', accent: 'text-error',
    lane: 'border-error/30 bg-error/5', laneLabel: 'text-error',
  },
}

/**
 * How two parts of a distributed system interact. These are DIFFERENT mechanisms with different guarantees, so the diagram must not draw
 * them all as the same arrow. The distinction never relies on colour alone: each has its own line pattern AND a text label with an icon.
 */
export const MECHANISMS = Object.freeze({
  SYNC: { label: 'Synchronous call', short: 'sync call', icon: '→', dash: undefined, note: 'The caller waits for the answer.' },
  EVENT: { label: 'Event on the simulated channel', short: 'event', icon: '⇢', dash: '10 6', note: 'The sender does not wait; the consumer handles it later.' },
  LOCAL_TX: { label: 'Local database transaction', short: 'local tx', icon: '▣', dash: '2 5', note: 'One service commits to its own database.' },
  OUTBOX: { label: 'Outbox relay', short: 'outbox relay', icon: '⇉', dash: '12 4 2 4', note: 'Committed outbox rows are published in a separate step.' },
  COMPENSATION: { label: 'Compensation: a new business operation', short: 'compensation', icon: '↺', dash: '14 5', tone: 'warning', note: 'Reverses an earlier committed effect. It is not a database rollback.' },
  REFUSED: { label: 'Refused locally', short: 'refused locally', icon: '✕', dash: '3 3', tone: 'error', note: 'Never reached the service (open circuit breaker or full bulkhead).' },
})

export const NODE_STATES = ['active', 'completed', 'upcoming', 'unreached']
export const EDGE_STATES = ['active', 'completed', 'upcoming', 'unused']

export const edgeKey = (step) => (step?.edgeFrom && step?.edgeTo ? `${step.edgeFrom}->${step.edgeTo}` : null)

/**
 * Where each node stands at step `index`:
 *  active     the current step highlights it
 *  completed  an earlier step already involved it
 *  upcoming   only a later step involves it
 *  unreached  no step of this scenario involves it (for example the controller in a request rejected with 401)
 */
export function nodeStates(scenario, index) {
  const before = new Set()
  const after = new Set()
  const ever = new Set()
  scenario.steps.forEach((step, i) => {
    for (const id of step.nodes ?? []) {
      ever.add(id)
      if (i < index) before.add(id)
      if (i > index) after.add(id)
    }
  })
  const current = new Set(scenario.steps[index]?.nodes ?? [])
  const states = new Map()
  for (const node of scenario.nodes) {
    states.set(node.id, current.has(node.id) ? 'active' : before.has(node.id) ? 'completed' : after.has(node.id) ? 'upcoming' : 'unreached')
  }
  return states
}

/** The same four-way classification for edges (an edge is "used" by a step that names it as edgeFrom to edgeTo). */
export function edgeStates(scenario, index) {
  const before = new Set()
  const after = new Set()
  scenario.steps.forEach((step, i) => {
    const key = edgeKey(step)
    if (!key) return
    if (i < index) before.add(key)
    if (i > index) after.add(key)
  })
  const active = edgeKey(scenario.steps[index])
  const states = new Map()
  for (const edge of scenario.edges) {
    states.set(edge.id, edge.id === active ? 'active' : before.has(edge.id) ? 'completed' : after.has(edge.id) ? 'upcoming' : 'unused')
  }
  return states
}

/** Branches (extra paths such as the 401/403 return) are active on the steps they name, never inferred from anything else. */
export function branchStates(layout, scenario, index) {
  const states = new Map()
  for (const branch of layout.branches ?? []) {
    const hit = (i) => branch.stepIds.includes(scenario.steps[i]?.id)
    const earlier = scenario.steps.some((_, i) => i < index && hit(i))
    const later = scenario.steps.some((_, i) => i > index && hit(i))
    states.set(branch.id, hit(index) ? 'active' : earlier ? 'completed' : later ? 'upcoming' : 'unused')
  }
  return states
}

/**
 * Colour, weight and dash for an edge, as CSS variables (the design tokens). Execution state decides the colour: active is
 * lime, completed is green, a rejection branch is red, upcoming is muted. Layer colours are never used on edges. The direction
 * (request or response) is shown by the arrowhead and the packet, not by colour.
 */
export function edgeAppearance({ state, tone, mechanism, failed }) {
  const m = mechanism ? MECHANISMS[mechanism] : undefined
  if (m) return mechanismAppearance({ state, mechanism: m, failed })
  const dashed = tone === 'error' || tone === 'lookup'
  if (state === 'active') {
    const color = tone === 'error' ? 'var(--color-error)' : 'var(--color-accent-primary)'
    return { stroke: color, width: 3.5, dash: dashed ? '8 5' : undefined, opacity: 1, arrow: color }
  }
  if (state === 'completed') {
    const color = tone === 'error' ? 'var(--color-error)' : 'var(--color-success)'
    return { stroke: color, width: 2.5, dash: dashed ? '8 5' : undefined, opacity: tone === 'error' ? 0.7 : 1, arrow: color }
  }
  if (state === 'upcoming') {
    return { stroke: 'var(--color-border-strong)', width: 2, dash: dashed ? '8 5' : undefined, opacity: 1, arrow: 'var(--color-text-muted)' }
  }
  // 'unused': a possible path this request did not take
  return { stroke: tone === 'error' ? 'var(--color-error)' : 'var(--color-border-default)', width: 1.75, dash: '4 5', opacity: tone === 'error' ? 0.45 : 0.9, arrow: 'var(--color-border-strong)' }
}

/** Colour comes from the execution state (or amber for a compensation, red for a failure); the LINE PATTERN comes from the mechanism. */
function mechanismAppearance({ state, mechanism, failed }) {
  const warning = mechanism.tone === 'warning'
  const error = failed || mechanism.tone === 'error'
  if (state === 'unused') {
    return { stroke: 'var(--color-border-default)', width: 1.75, dash: '4 5', opacity: 0.9, arrow: 'var(--color-border-strong)' }
  }
  const color = error ? 'var(--color-error)' : warning ? 'var(--color-warning)' : state === 'active' ? 'var(--color-accent-primary)' : state === 'completed' ? 'var(--color-success)' : 'var(--color-border-strong)'
  const width = state === 'active' ? 3.5 : state === 'completed' ? 2.5 : 2
  const arrow = state === 'upcoming' && !error && !warning ? 'var(--color-text-muted)' : color
  return { stroke: color, width, dash: mechanism.dash, opacity: state === 'completed' && error ? 0.8 : 1, arrow }
}

/**
 * Which arrowheads an edge carries. A REQUEST-direction edge points at its target. The active RESPONSE edge points the
 * other way (at its source), so the reverse flow is readable even with animation off.
 */
export function edgeMarkers({ state, direction }) {
  const reversed = state === 'active' && direction === 'RESPONSE'
  return { end: !reversed, start: reversed }
}

/** A moving packet is drawn only on the active edge and only when the user has not asked for reduced motion. */
export function showsPacket({ state, reducedMotion }) {
  return state === 'active' && !reducedMotion
}

/** The text a screen reader gets for a node, so state is never conveyed by colour alone. */
export function nodeAriaLabel({ label, stage, state }) {
  const where = stage ? `Stage ${stage}: ` : ''
  const status = {
    active: 'current step',
    completed: 'already passed',
    upcoming: 'comes later',
    unreached: 'not reached in this request',
  }[state]
  return `${where}${label} (${status})`
}

/** The ordered execution stages for the legend and for tests: [{stage, nodeId}] sorted by stage. */
export function stageOrder(layout) {
  return Object.entries(layout.nodeMeta ?? {})
    .filter(([, m]) => m.stage != null && Number(String(m.stage).split('–')[0]) < 100)
    .map(([nodeId, m]) => ({ nodeId, stage: m.stage, first: Number(String(m.stage).split('–')[0]) }))
    .sort((a, b) => a.first - b.first)
}

/** One line under the diagram: where the request is now and what comes next. */
export function flowCaption(scenario, index) {
  const step = scenario.steps[index]
  const next = scenario.steps[index + 1]
  const name = (id) => scenario.nodes.find((n) => n.id === id)?.label ?? id
  const key = edgeKey(step)
  let movement = null
  if (key) {
    const from = name(step.edgeFrom)
    const to = name(step.edgeTo)
    movement = step.direction === 'RESPONSE' ? `Response: ${to} → ${from}` : `Request: ${from} → ${to}`
  }
  return { current: step.title, movement, next: next ? next.title : null }
}
