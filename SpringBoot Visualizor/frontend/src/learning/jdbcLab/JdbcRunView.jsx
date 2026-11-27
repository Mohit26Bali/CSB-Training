import { useMemo } from 'react'
import SimulationView from '../components/SimulationView'
import { liveScenario } from '../scenarios/jdbcLab.sim'
import { poolMetricsOf, statementsSoFar, totalStatements, transactionStateAt } from '../schema/jdbcRun'

export function StatementCounter({ steps, index }) {
  return (
    <div className="flex flex-wrap items-center gap-x-6 gap-y-1 rounded-lg border border-line bg-surface px-4 py-2 shadow-sm" data-testid="statement-counter">
      <div>
        <span className="text-2xl font-bold tabular-nums text-fg">{statementsSoFar(steps, index)}</span>
        <span className="ml-1 text-sm text-fg-2">{' '}of {totalStatements(steps)} statements handed to the JDBC driver so far</span>
      </div>
      <p className="text-xs text-muted">Counted from the calls captured while each step ran. A statement handed to the driver is not proof of a commit: see the transaction state.</p>
    </div>
  )
}

const TONE = {
  'AUTO-COMMIT': 'border-captured text-captured',
  OPEN: 'border-warning text-warning',
  COMMITTED: 'border-success text-success',
  'ROLLED BACK': 'border-danger text-danger',
}

/** Transaction state of connection A, taken from the server's evidence for the latest step that reported it. */
export function TransactionStateStrip({ steps, index }) {
  const state = transactionStateAt(steps, index)
  if (!state) return null
  return (
    <div className={`rounded-lg border bg-surface px-4 py-2 text-sm shadow-sm ${TONE[state.key] ?? 'border-line text-fg'}`} data-testid="transaction-state">
      <span className="font-semibold">Connection A: </span>
      {state.text}
    </div>
  )
}

export function PoolStrip({ step }) {
  const m = poolMetricsOf(step)
  if (!m) return null
  const cells = [['active', m.active], ['idle', m.idle], ['total', m.total], ['waiting', m.waiting], ['maximum', m.maximum]]
  return (
    <dl className="flex flex-wrap gap-x-6 gap-y-1 rounded-lg border border-line bg-surface px-4 py-2 shadow-sm" data-testid="pool-strip" aria-label="Lab pool metrics">
      {cells.map(([k, v]) => (
        <div key={k}>
          <dt className="text-xs text-muted">{k}</dt>
          <dd className="text-lg font-bold tabular-nums text-fg">{v}</dd>
        </div>
      ))}
      <p className="basis-full text-xs text-muted">Read from the dedicated lab pool at this step. The application&apos;s own pool is never changed.</p>
    </dl>
  )
}

/** One finished LIVE run on the experiment's diagram, with the counters and strips the experiment's kind needs. */
export default function JdbcRunView({ info, run, codeRefs }) {
  const scenario = useMemo(() => liveScenario(run, info), [run, info])
  const renderExtra = (step, index) => (
    <div className="space-y-2">
      <StatementCounter steps={run.steps} index={index} />
      {info.diagram === 'transaction' && <TransactionStateStrip steps={run.steps} index={index} />}
      {info.diagram === 'pool' && <PoolStrip step={step} />}
    </div>
  )
  return <SimulationView key={run.ranAt} scenario={scenario} codeRefs={codeRefs} renderExtra={renderExtra} />
}
