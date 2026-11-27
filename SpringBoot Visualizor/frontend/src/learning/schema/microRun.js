/**
 * Pure helpers that turn a Microservices Lab run into what the interface draws. Nothing here invents an observation: every value is read
 * from the run the server returned (its steps, their evidence, its call records and spans). The only judgement made here is classification:
 * WHICH MECHANISM a recorded call is (a synchronous call, an event, a compensation, an outbox relay, a local refusal), decided from
 * the call's own recorded kind, operation and outcome.
 */
import { MECHANISMS } from './diagram'

/** Operations that are compensations: new business operations that reverse an earlier committed effect. */
export const COMPENSATION_OPERATIONS = Object.freeze(['release', 'refund', 'onPaymentFailed', 'resumeCompensation'])

/** Outcomes that say the dependency itself had a problem (a business refusal does not). */
export const FAILURE_OUTCOMES = Object.freeze(['UNAVAILABLE', 'TIMEOUT', 'TECHNICAL_ERROR', 'BULKHEAD_REJECTED', 'CIRCUIT_OPEN'])

const LOCAL_REFUSALS = ['CIRCUIT_OPEN', 'BULKHEAD_REJECTED']

/** Which mechanism a recorded call is. Order matters: a compensation delivered as an event is still, first of all, a compensation. */
export function mechanismOfCall(call) {
  if (LOCAL_REFUSALS.includes(call.outcome)) return 'REFUSED'
  if (COMPENSATION_OPERATIONS.includes(call.operation)) return 'COMPENSATION'
  if (call.operation === 'relayOutbox') return 'OUTBOX'
  if (call.kind === 'EVENT') return 'EVENT'
  return 'SYNC'
}

export const isFailure = (call) => FAILURE_OUTCOMES.includes(call.outcome)

/** Most significant first: a line that carries several mechanisms in one step is drawn as the first one found here. */
const SIGNIFICANCE = ['COMPENSATION', 'REFUSED', 'OUTBOX', 'EVENT', 'LOCAL_TX', 'SYNC']

const SHORT_OUTCOME = {
  UNAVAILABLE: 'DOWN',
  TIMEOUT: 'TIMEOUT',
  TECHNICAL_ERROR: 'ERROR',
  BULKHEAD_REJECTED: 'BULKHEAD FULL',
  CIRCUIT_OPEN: 'BREAKER OPEN',
}

const OUTCOME_MEANING = {
  UNAVAILABLE: 'The service could not be reached; the request was never processed.',
  TIMEOUT: 'No answer arrived in time. The work may or may not have been done.',
  TECHNICAL_ERROR: 'The service failed with an unexpected exception.',
  BULKHEAD_REJECTED: 'All of the service concurrency permits were in use; the call was refused locally.',
  CIRCUIT_OPEN: 'The caller circuit breaker was open; the call was refused locally.',
}

/** The calls the server recorded for one step, found through the "Service call N" evidence (N is the call's run-wide sequence number). */
export function callsOfStep(step, calls) {
  const bySeq = new Map((calls ?? []).map((c) => [c.seq, c]))
  const found = []
  for (const e of step.evidence ?? []) {
    const m = /^Service call (\d+)$/.exec(e.label)
    if (m && bySeq.has(Number(m[1]))) found.push(bySeq.get(Number(m[1])))
  }
  return found
}

/**
 * The lines a step draws: one per recorded call (by its mechanism), one per database the step touched (a LOCAL transaction), and the
 * step's own edge when no call explains it. Returned as [{ id: 'from->to', mechanism, failed, outcome, labels: [..] }].
 */
export function drawnOfStep(step, calls) {
  const drawn = new Map()
  const add = (id, mechanism, failed, outcome) => {
    const entry = drawn.get(id) ?? { id, mechanisms: new Set(), failed: false, outcome: null }
    entry.mechanisms.add(mechanism)
    if (failed) {
      entry.failed = true
      entry.outcome = outcome
    }
    drawn.set(id, entry)
  }
  const stepCalls = callsOfStep(step, calls)
  for (const c of stepCalls) add(`${c.from}->${c.to}`, mechanismOfCall(c), isFailure(c), c.outcome)

  if (step.provenance === 'CAPTURED') {
    for (const node of step.nodes ?? []) {
      if (node.endsWith('-db') && !node.startsWith('monolith')) add(`${node.slice(0, -3)}->${node}`, 'LOCAL_TX', false, null)
      if (node === 'monolith-db') add('monolith->monolith-db', 'LOCAL_TX', false, null)
    }
  }
  if (step.edgeFrom && step.edgeTo) {
    const id = `${step.edgeFrom}->${step.edgeTo}`
    if (!drawn.has(id)) {
      const relay = stepCalls.some((c) => c.operation === 'relayOutbox')
      const mechanism = step.edgeTo.endsWith('-db') ? 'LOCAL_TX' : step.edgeTo === 'broker' ? (relay ? 'OUTBOX' : 'EVENT') : 'SYNC'
      add(id, mechanism, false, null)
    }
  }
  return [...drawn.values()].map((d) => {
    const mechanism = SIGNIFICANCE.find((m) => d.mechanisms.has(m))
    return {
      id: d.id,
      mechanism,
      failed: d.failed,
      outcome: d.outcome,
      labels: SIGNIFICANCE.filter((m) => d.mechanisms.has(m)),
    }
  })
}

/** Text drawn on a line: the mechanism's icon and short name, every other mechanism on the same pair, and the failure if any. */
export function edgeLabel(entry) {
  const first = MECHANISMS[entry.mechanism]
  const others = entry.labels.filter((m) => m !== entry.mechanism).map((m) => MECHANISMS[m].short)
  return `${first.icon} ${first.short}${others.length ? ` + ${others.join(' + ')}` : ''}${entry.failed ? ` · ${SHORT_OUTCOME[entry.outcome] ?? entry.outcome}` : ''}`
}

/**
 * Which lines the diagram draws at step `index`: ONLY the lines of the current step (state 'active'); every other line of the run is 'hidden'.
 * Drawing earlier and later steps as well made the picture unreadable (a dozen crossing lines), and the nodes already show who has been involved.
 */
export function edgeOverlay(steps, index) {
  const out = {}
  steps.forEach((step, i) => {
    for (const entry of step.drawn ?? []) {
      if (i === index) out[entry.id] = { state: 'active', mechanism: entry.mechanism, failed: entry.failed, label: edgeLabel(entry), step: i }
      else if (!out[entry.id]) out[entry.id] = { state: 'hidden', step: i }
    }
  })
  return out
}

/** A chip on a node whose most recent call failed (and has not succeeded since), for steps up to `index`. From recorded call outcomes only. */
export function nodeOverlay(steps, index) {
  const last = new Map()
  steps.slice(0, index + 1).forEach((step) => {
    for (const c of step.calls ?? []) last.set(c.to, c)
  })
  const out = {}
  for (const [node, call] of last) {
    if (isFailure(call)) out[node] = { chips: [{ text: SHORT_OUTCOME[call.outcome], title: OUTCOME_MEANING[call.outcome] }] }
  }
  return out
}

/** Attaches the calls and the drawn lines to every step of a run, so the diagram and the call list read from the same place. */
export function enrichSteps(steps, calls) {
  return steps.map((s) => {
    const own = callsOfStep(s, calls)
    return { ...s, calls: own, drawn: drawnOfStep(s, calls) }
  })
}

/** Commits and rollbacks per connection, read from the "Other database calls observed" evidence (a captured list of JDBC calls). */
export function localTransactions(step) {
  const e = (step.evidence ?? []).find((x) => x.label === 'Other database calls observed')
  if (!e) return []
  const counts = new Map()
  for (const m of e.value.matchAll(/([\w-]+): (commit|rollback)\(\)/g)) {
    const key = `${m[1]}:${m[2]}`
    counts.set(key, (counts.get(key) ?? 0) + 1)
  }
  return [...counts].map(([key, n]) => {
    const [service, kind] = key.split(':')
    return { service, kind: kind === 'commit' ? 'committed' : 'rolled back', count: n }
  })
}

const evidenceValue = (step, label) => (step.evidence ?? []).find((e) => e.label === label || e.label.startsWith(label))?.value ?? null

/** The latest value of an evidence label at or before `index`. */
export function latestEvidence(steps, index, label) {
  for (let i = index; i >= 0; i -= 1) {
    const v = evidenceValue(steps[i], label)
    if (v !== null) return v
  }
  return null
}

export const BREAKER_STATES = Object.freeze(['CLOSED', 'OPEN', 'HALF_OPEN'])

export function breakerState(steps, index) {
  const v = latestEvidence(steps, index, 'Breaker state')
  return BREAKER_STATES.includes(v) ? v : null
}

/** Free permits per service from the "Permits available to X" evidence, latest at or before `index`. */
export function permits(steps, index) {
  const out = {}
  for (const service of ['Payment', 'Inventory', 'Product']) {
    const v = latestEvidence(steps, index, `Permits available to ${service}`)
    if (v !== null && /^\d+$/.test(v)) out[service.toLowerCase()] = Number(v)
  }
  return out
}

/** The saga log lines of a step: [{ seq, step, state, detail }]. */
export function sagaLog(step) {
  return (step.evidence ?? [])
    .map((e) => /^Saga log (\d+) (\w+)$/.exec(e.label) && { m: /^Saga log (\d+) (\w+)$/.exec(e.label), value: e.value })
    .filter(Boolean)
    .map(({ m, value }) => {
      const match = /^(\w+)(?: \((.*)\))?$/.exec(value)
      return { seq: Number(m[1]), step: m[2], state: match?.[1] ?? value, detail: match?.[2] ?? '' }
    })
}

/** The headline outcome a step reports to its caller, if it records one. */
export function headline(step) {
  return evidenceValue(step, 'Caller sees') ?? evidenceValue(step, 'User sees') ?? evidenceValue(step, 'Final outcome') ?? evidenceValue(step, 'Response status')
}

/** Spans as indented rows (children under their parent, in start order). SIMULATED and MEASURED times stay in separate fields. */
export function spanRows(spans) {
  const ids = new Set(spans.map((s) => s.spanId))
  const children = new Map()
  const roots = []
  for (const s of spans) {
    if (s.parentSpanId && ids.has(s.parentSpanId)) children.set(s.parentSpanId, [...(children.get(s.parentSpanId) ?? []), s])
    else roots.push(s)
  }
  const order = (a, b) => a.startMillis - b.startMillis || Number(a.spanId.split('-')[1]) - Number(b.spanId.split('-')[1])
  const rows = []
  const walk = (span, depth) => {
    const kids = [...(children.get(span.spanId) ?? [])].sort(order)
    const childTotal = kids.reduce((n, k) => n + k.simulatedMillis, 0)
    rows.push({ span, depth, selfMillis: Math.max(0, span.simulatedMillis - childTotal) })
    kids.forEach((k) => walk(k, depth + 1))
  }
  roots.sort(order).forEach((r) => walk(r, 0))
  return rows
}

/** The local-transaction wording for a node id like "order-db": the service that owns the database. */
export const serviceOfDatabase = (node) => (node.endsWith('-db') ? node.slice(0, -3) : null)
