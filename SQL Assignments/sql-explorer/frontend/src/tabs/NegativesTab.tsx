import { useState } from 'react'
import { api, type AssignmentDetail, type NegativeResult } from '../api'
import { messageOf } from '../hooks'
import { SqlBlock } from '../components/SqlBlock'

export function NegativesTab({ a }: { a: AssignmentDetail }) {
  const [results, setResults] = useState<NegativeResult[] | null>(null)
  const [summary, setSummary] = useState<{ passed: number; total: number } | null>(null)
  const [running, setRunning] = useState(false)
  const [failure, setFailure] = useState<string | null>(null)

  async function run() {
    setRunning(true)
    setFailure(null)
    try {
      const r = await api.runNegatives(a.id)
      setResults(r.results)
      setSummary({ passed: r.passed, total: r.total })
    } catch (err) {
      setFailure(messageOf(err))
    } finally {
      setRunning(false)
    }
  }

  return (
    <>
      <p className="lead">
        These statements break a rule on purpose (a duplicate, a missing value, a bad reference). Each one is <strong>supposed to fail</strong> with a
        specific MySQL error. Run them to see the error MySQL really gives, next to the one the assignment expects.
      </p>
      <div className="toolbar">
        <button className="btn primary" onClick={run} disabled={running}>
          {running ? 'Running…' : `Run all ${a.negatives.length} tests`}
        </button>
        {summary && (
          <span role="status" className={summary.passed === summary.total ? 'verdict ok' : 'verdict bad'}>
            {summary.passed} of {summary.total} failed exactly as expected
          </span>
        )}
      </div>
      {failure && <p className="notice error" role="alert">{failure}</p>}
      <ol className="negatives">
        {a.negatives.map((n) => {
          const r = results?.find((x) => x.id === n.id)
          return (
            <li key={n.id} className={r ? (r.ok ? 'neg ok' : 'neg bad') : 'neg'}>
              <h3>
                <span className="num-badge small">{n.id}</span> {n.title}
              </h3>
              <SqlBlock sql={n.sql} />
              <p className="meta">Expected MySQL error <strong>{n.expectedCode}</strong></p>
              {r && (
                <p className={r.ok ? 'notice ok' : 'notice error'} role="status">
                  {r.ok ? '✓ ' : '✗ '}
                  {r.unexpectedSuccess ? 'The statement succeeded, but it should have failed.' : <><strong>Error {r.actualCode}</strong> {r.message}</>}
                </p>
              )}
            </li>
          )
        })}
      </ol>
    </>
  )
}
