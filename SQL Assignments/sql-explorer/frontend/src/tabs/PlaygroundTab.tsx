import { useState } from 'react'
import { api, type AssignmentDetail, type StatementResult } from '../api'
import { messageOf } from '../hooks'
import { ResultView } from '../components/ResultView'

export function PlaygroundTab({ a }: { a: AssignmentDetail }) {
  const first = a.tables[0]?.name ?? 'employees'
  const [sql, setSql] = useState(`SELECT * FROM ${first}`)
  const [result, setResult] = useState<(StatementResult & { ms?: number }) | null>(null)
  const [failure, setFailure] = useState<string | null>(null)
  const [running, setRunning] = useState(false)

  async function run() {
    setRunning(true)
    setFailure(null)
    setResult(null)
    try {
      setResult(await api.runSql(a.id, sql))
    } catch (err) {
      setFailure(messageOf(err))
    } finally {
      setRunning(false)
    }
  }

  return (
    <>
      <p className="lead">
        Try your own query on <code>{a.originalDb}</code>. Only reading is possible: one SELECT (or WITH, SHOW, DESCRIBE, EXPLAIN) at a time, with a
        time limit and at most 500 rows. Nothing can be changed from here.
      </p>
      <label htmlFor="sql-input" className="visually-hidden">SQL query</label>
      <textarea
        id="sql-input"
        className="editor"
        value={sql}
        spellCheck={false}
        rows={6}
        onChange={(e) => setSql(e.target.value)}
        onKeyDown={(e) => {
          if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
            e.preventDefault()
            void run()
          }
        }}
      />
      <div className="toolbar">
        <button className="btn primary" onClick={run} disabled={running}>
          {running ? 'Running…' : 'Run query'}
        </button>
        <span className="meta">Ctrl + Enter also runs it</span>
        <span className="chips" aria-label="Tables">
          {a.tables.map((t) => (
            <button key={t.name} className="chip" onClick={() => setSql(`SELECT * FROM ${t.name}`)}>
              {t.name}
            </button>
          ))}
        </span>
      </div>
      {failure && <p className="notice error" role="alert">{failure}</p>}
      {result && <ResultView result={result} caption="Playground result" />}
    </>
  )
}
