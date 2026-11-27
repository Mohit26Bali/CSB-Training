import { EVIDENCE_LABELS } from '../scenarios/jdbcLab.sim'

/** Statements handed to the JDBC driver (or prepared by Hibernate) up to and including step `index`. Counted from the captured steps. */
export function statementsSoFar(steps, index) {
  return steps.slice(0, index + 1).reduce((n, s) => n + s.sql.length, 0)
}

export function totalStatements(steps) {
  return steps.reduce((n, s) => n + s.sql.length, 0)
}

const valueOf = (step, label) => step.evidence?.find((e) => e.label === label)?.value ?? null

/** Transaction state reported by the server for connection A at this step, or null. Never guessed on the client. */
export function transactionStateOf(step) {
  const v = valueOf(step, EVIDENCE_LABELS.transactionState)
  if (!v) return null
  const key = String(v).split(':')[0].trim()
  return { key, text: String(v) }
}

/** The last transaction state reported at or before `index` (a step without the evidence keeps the previous state). */
export function transactionStateAt(steps, index) {
  for (let i = index; i >= 0; i -= 1) {
    const s = transactionStateOf(steps[i])
    if (s) return s
  }
  return null
}

/** Pool counters parsed from "active=1 idle=0 total=1 waiting=0 (maximum 1)", or null. */
export function poolMetricsOf(step) {
  const v = valueOf(step, EVIDENCE_LABELS.pool)
  if (!v) return null
  const m = /active=(\d+) idle=(\d+) total=(\d+) waiting=(\d+) \(maximum (\d+)\)/.exec(String(v))
  if (!m) return null
  const [active, idle, total, waiting, maximum] = m.slice(1).map(Number)
  return { active, idle, total, waiting, maximum }
}
