const CHALLENGE_LABELS = new Set(['Observed WWW-Authenticate', 'Observed WWW-Authenticate header', 'WWW-Authenticate'])

/** The server's own WWW-Authenticate challenge as recorded in the trace evidence, or null when none was sent. */
export function challengeOf(trace) {
  for (const step of trace.steps) {
    for (const e of step.evidence ?? []) {
      if (CHALLENGE_LABELS.has(e.label) && e.value && e.value !== '(none)') return e.value
    }
  }
  return null
}

/** Facts that are compared between a normal request and an experiment, taken only from the backend's captured trace. */
export function summarizeTrace(trace) {
  return {
    status: trace.status,
    handlerInvoked: trace.handlerInvoked,
    sqlCount: trace.sqlCount,
    challenge: challengeOf(trace),
    stepIds: trace.steps.map((s) => s.id),
  }
}

/** Steps present in the normal request but absent from the experiment (what the experiment skipped), by step kind. */
export function skippedSteps(baseline, experiment) {
  const kind = (id) => id.replace(/-\d+$/, '')
  const present = new Set(experiment.stepIds.map(kind))
  return [...new Set(baseline.stepIds.map(kind))].filter((k) => !present.has(k))
}

/**
 * Compares an observed trace with the documented expectation for the caller's role.
 * Returns one row per documented field: { field, expected, observed, ok }. Null/undefined expectations are skipped.
 */
export function checkExpectation(expectation, observed) {
  if (!expectation) return []
  const rows = []
  if (expectation.status != null) {
    rows.push({ field: 'HTTP status', expected: expectation.status, observed: observed.status, ok: expectation.status === observed.status })
  }
  if (expectation.handlerInvoked != null) {
    const expected = expectation.handlerInvoked ? 'yes' : 'no'
    const actual = observed.handlerInvoked ? 'yes' : 'no'
    rows.push({ field: 'Controller invoked', expected, observed: actual, ok: expected === actual })
  }
  if (expectation.sqlCount != null) {
    rows.push({ field: 'SQL statements prepared', expected: expectation.sqlCount, observed: observed.sqlCount, ok: expectation.sqlCount === observed.sqlCount })
  }
  if (expectation.challengeContains) {
    const found = (observed.challenge ?? '').toLowerCase().includes(expectation.challengeContains.toLowerCase())
    rows.push({
      field: `Server's WWW-Authenticate mentions "${expectation.challengeContains}"`,
      expected: 'yes',
      observed: found ? 'yes' : 'no',
      ok: found,
    })
  }
  return rows
}

export function expectationForRole(expectations, role) {
  if (!expectations) return null
  return expectations[role] ?? expectations['*'] ?? null
}
