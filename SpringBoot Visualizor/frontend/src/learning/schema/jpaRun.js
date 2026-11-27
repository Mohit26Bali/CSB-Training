/** Statements Hibernate prepared up to and including step `index`, computed from the captured steps (never typed in). */
export function statementsSoFar(steps, index) {
  return steps.slice(0, index + 1).reduce((n, s) => n + s.sql.length, 0)
}

export function totalStatements(steps) {
  return steps.reduce((n, s) => n + s.sql.length, 0)
}
