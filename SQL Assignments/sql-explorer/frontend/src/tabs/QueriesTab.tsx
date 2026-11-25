import { useState } from 'react'
import { api, type AssignmentDetail, type QueryDef, type StatementResult } from '../api'
import { messageOf } from '../hooks'
import { SqlBlock } from '../components/SqlBlock'
import { ResultView } from '../components/ResultView'

type Mode = 'seed' | 'after-scripts'

function QueryCard({ a, q, mode, number }: { a: AssignmentDetail; q: QueryDef; mode: Mode; number: number }) {
  const [result, setResult] = useState<(StatementResult & { ms?: number }) | null>(null)
  const [running, setRunning] = useState(false)
  const [failure, setFailure] = useState<string | null>(null)

  async function run() {
    setRunning(true)
    setFailure(null)
    try {
      setResult(await api.runQuery(a.id, q.id, mode))
    } catch (err) {
      setFailure(messageOf(err))
    } finally {
      setRunning(false)
    }
  }

  return (
    <article className="query-card" aria-labelledby={`q-${q.id}`}>
      <header>
        <h3 id={`q-${q.id}`}>
          <span className="num-badge small">{number}</span> {q.title}
        </h3>
        <button className="btn primary small" onClick={run} disabled={running} aria-label={`Run query ${number}: ${q.title}`}>
          {running ? 'Running…' : 'Run'}
        </button>
      </header>
      <SqlBlock sql={q.sql} />
      {failure && <p className="notice error" role="alert">{failure}</p>}
      {result && <ResultView result={result} caption={q.title} />}
    </article>
  )
}

export function QueriesTab({ a }: { a: AssignmentDetail }) {
  const [mode, setMode] = useState<Mode>('seed')
  const hasDml = a.dmlScripts.length > 0
  return (
    <>
      {hasDml && (
        <fieldset className="mode">
          <legend>Run the queries</legend>
          <label>
            <input type="radio" name="mode" checked={mode === 'seed'} onChange={() => setMode('seed')} /> on the seed data
          </label>
          <label>
            <input type="radio" name="mode" checked={mode === 'after-scripts'} onChange={() => setMode('after-scripts')} /> after the data-changing scripts ran
          </label>
          <p className="meta">
            "After" runs {a.dmlScripts.join(' and ')} first, inside a transaction that is rolled back, so you see what the queries show once the
            scripts have done their work.
          </p>
        </fieldset>
      )}
      {a.queries.map((q, i) => (
        <QueryCard key={`${q.id}-${mode}`} a={a} q={q} mode={mode} number={i + 1} />
      ))}
    </>
  )
}
