/**
 * The shared scenario model used by BOTH modes:
 *  - SIMULATED scenarios are authored in this frontend (every step CONCEPTUAL, no SQL).
 *  - LIVE scenarios are assembled by the backend from a real request, with per-step provenance.
 * Validators here run in tests and (for the 5W1H) at render time, so an incomplete explanation cannot ship silently.
 */

import { validateLayout } from './layout'

export const PROVENANCE = Object.freeze({
  CAPTURED: 'CAPTURED', // observed while the real request ran
  DERIVED: 'DERIVED', // inferred from captured facts plus SpringLab's own code
  CONCEPTUAL: 'CONCEPTUAL', // explains the framework; nothing about this step was observed
})

/**
 * Only CAPTURED steps may be presented as LIVE. This mapping is the single place that decides badge wording.
 */
export function badgeFor(provenance) {
  switch (provenance) {
    case PROVENANCE.CAPTURED:
      return { label: 'LIVE', icon: '●', hint: 'Observed while the real request ran', tone: 'live' }
    case PROVENANCE.DERIVED:
      return { label: 'INFERRED', icon: '◐', hint: 'Inferred from captured facts and SpringLab code; not observed directly', tone: 'derived' }
    case PROVENANCE.CONCEPTUAL:
    default:
      return { label: 'SIMULATED', icon: '○', hint: 'Conceptual explanation; nothing here was observed', tone: 'simulated' }
  }
}

export const EXPLAIN_SECTIONS = Object.freeze([
  { key: 'what', title: 'What', question: 'What is the concept?' },
  { key: 'why', title: 'Why', question: 'Why does it exist and what problem does it solve?' },
  { key: 'who', title: 'Who', question: 'Which components, classes, users or entities participate?' },
  { key: 'where', title: 'Where', question: 'Where does it operate in the application architecture?' },
  { key: 'when', title: 'When', question: 'At what point in the execution lifecycle does it operate?' },
  { key: 'how', title: 'How', question: 'How does it work, step by step?' },
])

const isText = (value, min = 12) => typeof value === 'string' && value.trim().length >= min

/** Returns a list of problems (empty = valid). Every module must provide all six sections. */
export function validateExplain5W1H(explain) {
  const problems = []
  if (!explain || typeof explain !== 'object') return ['explanation is missing']
  for (const key of ['what', 'why', 'where', 'when']) {
    if (!isText(explain[key])) problems.push(`"${key}" must be a sentence of at least 12 characters`)
  }
  if (!Array.isArray(explain.who) || explain.who.length === 0) {
    problems.push('"who" must list at least one participant')
  } else {
    explain.who.forEach((p, i) => {
      if (!p || !isText(p.name, 2) || !isText(p.role, 8)) problems.push(`"who"[${i}] needs a name and a role`)
    })
  }
  if (!Array.isArray(explain.how) || explain.how.length < 3) {
    problems.push('"how" must list at least 3 steps')
  } else {
    explain.how.forEach((s, i) => {
      if (!isText(s, 8)) problems.push(`"how"[${i}] is empty or too short`)
    })
  }
  const flat = JSON.stringify(explain)
  if (/\b(TODO|TBD|lorem ipsum)\b/i.test(flat)) problems.push('contains placeholder text')
  return problems
}

const DIRECTIONS = ['REQUEST', 'RESPONSE']

/**
 * Structural validation of a scenario (nodes, edges, steps). Returns a list of problems.
 * @param scenario  { id, title, mode, nodes, edges, steps }
 * @param options   { codeRefIds?: Set<string> }  when given, every codeRefId must be in the set
 */
export function validateScenario(scenario, { codeRefIds } = {}) {
  const problems = []
  if (!scenario?.id || !scenario?.title) problems.push('scenario needs id and title')
  if (!['SIMULATED', 'LIVE'].includes(scenario?.mode)) problems.push('mode must be SIMULATED or LIVE')

  const nodeIds = new Set((scenario?.nodes ?? []).map((n) => n.id))
  if (nodeIds.size !== (scenario?.nodes ?? []).length) problems.push('node ids must be unique')
  const edgeIds = new Set((scenario?.edges ?? []).map((e) => e.id))
  for (const e of scenario?.edges ?? []) {
    if (!nodeIds.has(e.source) || !nodeIds.has(e.target)) problems.push(`edge ${e.id} references an unknown node`)
    if (e.id !== `${e.source}->${e.target}`) problems.push(`edge id ${e.id} must be "source->target"`)
  }

  problems.push(...validateLayout(scenario))

  const steps = scenario?.steps ?? []
  if (steps.length === 0) problems.push('scenario has no steps')
  const seen = new Set()
  for (const step of steps) {
    const where = `step ${step.id}`
    if (!step.id || seen.has(step.id)) problems.push(`${where}: id missing or duplicated`)
    seen.add(step.id)
    if (!step.title) problems.push(`${where}: title missing`)
    if (!isText(step.explanation, 20)) problems.push(`${where}: explanation too short`)
    if (!Object.values(PROVENANCE).includes(step.provenance)) problems.push(`${where}: unknown provenance`)
    if (!DIRECTIONS.includes(step.direction)) problems.push(`${where}: direction must be REQUEST or RESPONSE`)
    for (const n of step.nodes ?? []) if (!nodeIds.has(n)) problems.push(`${where}: unknown node ${n}`)
    if (step.edgeFrom || step.edgeTo) {
      if (!edgeIds.has(`${step.edgeFrom}->${step.edgeTo}`)) problems.push(`${where}: unknown edge ${step.edgeFrom}->${step.edgeTo}`)
    }
    if (step.sql?.length && step.provenance !== PROVENANCE.CAPTURED) {
      problems.push(`${where}: SQL text may only appear on CAPTURED steps`)
    }
    if (scenario?.mode === 'SIMULATED') {
      if (step.provenance !== PROVENANCE.CONCEPTUAL) problems.push(`${where}: SIMULATED scenarios may only contain CONCEPTUAL steps`)
      if (step.sql?.length || step.evidence?.length) problems.push(`${where}: SIMULATED steps must not carry captured evidence or SQL`)
    }
    if (step.codeRefId && codeRefIds && !codeRefIds.has(step.codeRefId)) {
      problems.push(`${where}: unknown codeRefId ${step.codeRefId}`)
    }
  }
  return problems
}

/** Builds the Challenge Arena URL used by every module's "Test yourself" action. */
export function arenaUrl({ topic, difficulty, count } = {}) {
  const params = new URLSearchParams()
  if (topic) params.set('topic', topic)
  if (['EASY', 'MEDIUM', 'HARD'].includes(difficulty)) params.set('difficulty', difficulty)
  if (Number.isInteger(count) && count >= 1 && count <= 20) params.set('count', String(count))
  const query = params.toString()
  return query ? `/arena?${query}` : '/arena'
}

/** Alters one character INSIDE the signature so the HS256 signature no longer matches (the last character would not do). */
export function tamperToken(token) {
  const parts = token.split('.')
  if (parts.length !== 3 || parts[2].length === 0) return `${token}x`
  const first = parts[2][0] === 'A' ? 'B' : 'A'
  return [parts[0], parts[1], first + parts[2].slice(1)].join('.')
}
