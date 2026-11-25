import { useState } from 'react'
import { api, type AssignmentDetail, type SandboxStep, type ScriptInfo } from '../api'
import { messageOf } from '../hooks'
import { SqlBlock } from '../components/SqlBlock'
import { ResultView } from '../components/ResultView'

const KIND_LABEL: Record<string, string> = {
  schema: 'builds the tables',
  seed: 'inserts the data',
  alter: 'changes the tables',
  queries: 'read-only queries',
  dml: 'changes data',
  negative: 'must fail',
  other: 'other',
}

function Steps({ steps }: { steps: SandboxStep[] }) {
  return (
    <ol className="steps">
      {steps
        .filter((s) => s.kind !== 'setting')
        .map((s, i) => (
          <li key={i}>
            {s.comments.length > 0 && <p className="step-note">{s.comments[s.comments.length - 1]}</p>}
            <SqlBlock sql={s.sql} />
            <ResultView result={s} caption={s.comments[s.comments.length - 1] ?? s.sql} />
          </li>
        ))}
    </ol>
  )
}

function ScriptCard({ a, script }: { a: AssignmentDetail; script: ScriptInfo }) {
  const runnable = a.dmlScripts.includes(script.file)
  const [open, setOpen] = useState(false)
  const [running, setRunning] = useState(false)
  const [outcome, setOutcome] = useState<{ steps: SandboxStep[]; appliedBefore: string[] } | null>(null)
  const [failure, setFailure] = useState<string | null>(null)

  async function run() {
    setRunning(true)
    setFailure(null)
    try {
      setOutcome(await api.runScript(a.id, script.file))
    } catch (err) {
      setFailure(messageOf(err))
    } finally {
      setRunning(false)
    }
  }

  return (
    <article className="script-card">
      <header>
        <h3>
          <code>{script.file}</code> <span className={`pill kind-${script.kind}`}>{KIND_LABEL[script.kind] ?? script.kind}</span>
        </h3>
        <div className="actions">
          <button className="btn small" onClick={() => setOpen(!open)} aria-expanded={open}>
            {open ? 'Hide source' : 'Show source'}
          </button>
          {runnable && (
            <button className="btn primary small" onClick={run} disabled={running}>
              {running ? 'Running…' : 'Run in sandbox'}
            </button>
          )}
        </div>
      </header>
      {!runnable && ['schema', 'seed', 'alter'].includes(script.kind) && (
        <p className="meta">Already applied when the demo database was built, so it is not run again.</p>
      )}
      {open && <SqlBlock sql={script.source} />}
      {failure && <p className="notice error" role="alert">{failure}</p>}
      {outcome && (
        <div className="outcome">
          <p className="notice info" role="status">
            Ran inside a transaction that was rolled back afterwards: <strong>nothing was saved.</strong>
            {outcome.appliedBefore.length > 0 && <> The earlier script{outcome.appliedBefore.length > 1 ? 's' : ''} ({outcome.appliedBefore.join(', ')}) ran first, as in the assignment.</>}
          </p>
          <Steps steps={outcome.steps} />
        </div>
      )}
    </article>
  )
}

export function ScriptsTab({ a }: { a: AssignmentDetail }) {
  const shown = a.scripts.filter((s) => s.kind !== 'negative' && s.kind !== 'queries')
  return (
    <>
      <p className="lead">
        The scripts that build and change this database. Data-changing scripts can be run here: each runs for real, you see the result of every
        statement, and then everything is undone. Queries and the must-fail tests have their own tabs.
      </p>
      {shown.map((s) => (
        <ScriptCard key={s.file} a={a} script={s} />
      ))}
    </>
  )
}
