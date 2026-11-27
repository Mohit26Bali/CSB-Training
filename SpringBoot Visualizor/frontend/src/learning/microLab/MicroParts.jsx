import Disclosure from '../../components/ui/Disclosure'
import FlowCanvas from '../components/FlowCanvas'
import FlowCaption from '../components/FlowCaption'
import { MECHANISMS } from '../schema/diagram'
import { BREAKER_STATES, breakerState, headline, isFailure, localTransactions, mechanismOfCall, permits, sagaLog, spanRows } from '../schema/microRun'
import { REALITY } from '../scenarios/microLab.sim'

const MECHANISM_ORDER = ['SYNC', 'EVENT', 'LOCAL_TX', 'OUTBOX', 'COMPENSATION', 'REFUSED']

/** A short sample of the line pattern, drawn with the same dash the diagram uses, so the legend shows what to look for. */
function LineSample({ mechanism }) {
  const m = MECHANISMS[mechanism]
  const colour = m.tone === 'warning' ? 'var(--color-warning)' : m.tone === 'error' ? 'var(--color-error)' : 'var(--color-fg-2)'
  return (
    <svg width="44" height="10" viewBox="0 0 44 10" aria-hidden="true" className="shrink-0">
      <line x1="2" y1="5" x2="36" y2="5" stroke={colour} strokeWidth="2.5" strokeDasharray={m.dash} />
      <path d="M34 1.5 41 5 34 8.5z" fill={colour} />
    </svg>
  )
}

/** Mechanism chip used wherever a call is named: icon + text + the same line pattern. Never colour alone. */
export function MechanismChip({ mechanism }) {
  const m = MECHANISMS[mechanism]
  const tone = m.tone === 'warning' ? 'border-warning-line text-warning' : m.tone === 'error' ? 'border-error-line text-error' : 'border-line-strong text-fg-2'
  return (
    <span data-testid="mechanism-chip" data-mechanism={mechanism} title={`${m.label}. ${m.note}`} className={`inline-flex items-center gap-1 whitespace-nowrap rounded-full border bg-surface px-2 py-0.5 text-xs font-semibold ${tone}`}>
      <span aria-hidden="true">{m.icon}</span>
      {m.short}
    </span>
  )
}

/** What the different lines and shapes mean. Teaches that these are different mechanisms, not one kind of arrow. */
export function MechanismLegend() {
  return (
    <Disclosure title="How to read the lines" meta="six different mechanisms" defaultOpen>
      <ul className="grid gap-x-6 gap-y-2 text-xs text-fg-2 sm:grid-cols-2" data-testid="mechanism-legend">
        {MECHANISM_ORDER.map((key) => (
          <li key={key} className="flex items-start gap-2">
            <LineSample mechanism={key} />
            <span>
              <span className="font-semibold text-fg">{MECHANISMS[key].label}.</span> {MECHANISMS[key].note}
            </span>
          </li>
        ))}
      </ul>
      <p className="mt-2 text-xs text-fg-2">
        Lime is the step happening now, green what already happened; amber marks a compensation and red a failed call. Squares travelling along a line are
        events; circles are calls. Cylinders are databases. A dashed node outline or a <span className="font-semibold text-conceptual">simulated</span> tag
        marks infrastructure that is a model, not a deployed system. When one pair of services uses several mechanisms in a step, the line shows the most
        significant and its label lists the others.
      </p>
    </Disclosure>
  )
}

/** What is real in this lab and what is simulated: always one click away, and shown before the first run. */
export function RealityPanel({ defaultOpen = false }) {
  return (
    <Disclosure title="What is real here, and what is simulated" meta="one JVM, one MySQL schema" defaultOpen={defaultOpen}>
      <div className="grid gap-3 md:grid-cols-2" data-testid="reality-panel">
        <div>
          <h4 className="text-xs font-semibold uppercase tracking-wide text-captured">Real (observed)</h4>
          <ul className="mt-1 list-disc space-y-1 pl-5 text-sm text-fg-2">
            {REALITY.real.map((t) => (
              <li key={t}>{t}</li>
            ))}
          </ul>
        </div>
        <div>
          <h4 className="text-xs font-semibold uppercase tracking-wide text-conceptual">Simulated (a model)</h4>
          <ul className="mt-1 list-disc space-y-1 pl-5 text-sm text-fg-2">
            {REALITY.simulated.map((t) => (
              <li key={t}>{t}</li>
            ))}
          </ul>
        </div>
      </div>
    </Disclosure>
  )
}

/** A persistent, honest banner for a run: the server's own statement of the environment. */
export function EnvironmentBanner({ text }) {
  return (
    <p className="rounded-md border border-dashed border-conceptual/60 bg-conceptual-soft px-3 py-2 text-sm text-fg" data-testid="environment-banner">
      <strong className="text-conceptual">Simulated infrastructure.</strong> {text}
    </p>
  )
}

const OUTCOME_TONE = {
  SUCCESS: 'border-success text-success',
  BUSINESS_FAILURE: 'border-warning text-warning',
}

/**
 * The calls of the current step, one per line, each with its mechanism, its outcome and whether the handler ran. It is the text equivalent of the
 * diagram (so nothing depends on seeing it) and, on a phone, it REPLACES the diagram: a vertical list reads better than a shrunken picture.
 */
export function CallSequence({ step }) {
  if (step.calls === undefined) return <ConceptLines step={step} />
  const calls = step.calls
  const transactions = localTransactions(step)
  return (
    <div className="space-y-3 rounded-lg border border-line bg-surface p-3" data-testid="call-sequence">
      <h4 className="text-sm font-semibold text-fg">{calls.length ? 'Calls in this step' : 'This step makes no service call'}</h4>
      {calls.length > 0 && (
        <ol className="space-y-2">
          {calls.map((c) => {
            const mechanism = mechanismOfCall(c)
            const failed = isFailure(c)
            return (
              <li key={c.seq} className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm" data-testid="call-row">
                <span className="w-6 text-right tabular-nums text-muted">{c.seq}.</span>
                <MechanismChip mechanism={mechanism} />
                <span className="font-mono text-fg">
                  {c.from} → {c.to}
                  {c.instance ? `#${c.instance}` : ''}.{c.operation}
                </span>
                <span className={`rounded-full border px-2 py-0.5 text-xs font-semibold ${failed ? 'border-error text-error' : (OUTCOME_TONE[c.outcome] ?? 'border-line-strong text-fg-2')}`}>
                  {c.outcome}
                  {c.code ? ` (${c.code})` : ''}
                </span>
                <span className="text-xs text-muted">
                  {c.simulatedMillis} ms simulated · {c.handlerInvoked ? 'handler ran' : 'handler did not run'}
                </span>
              </li>
            )
          })}
        </ol>
      )}
      {transactions.length > 0 && (
        <div>
          <h5 className="text-xs font-semibold uppercase tracking-wide text-muted">Local database transactions (captured)</h5>
          <ul className="mt-1 space-y-1 text-sm text-fg-2" data-testid="local-transactions">
            {transactions.map((t) => (
              <li key={`${t.service}-${t.kind}`} className="flex items-center gap-2">
                <MechanismChip mechanism="LOCAL_TX" />
                <span>
                  {t.service}: {t.kind}
                  {t.count > 1 ? ` ×${t.count}` : ''}
                </span>
                {t.kind === 'rolled back' && <span className="text-xs text-muted">a database rollback inside one service</span>}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}

/** The lines a CONCEPTUAL step draws, as text. Nothing here was observed. */
function ConceptLines({ step }) {
  const lines = step.drawn ?? []
  return (
    <div className="space-y-2 rounded-lg border border-dashed border-conceptual/50 bg-surface p-3" data-testid="concept-lines">
      <h4 className="text-sm font-semibold text-fg">{lines.length ? 'Lines this concept draws (SIMULATED)' : 'This concept step draws no line'}</h4>
      {lines.length > 0 && (
        <ul className="space-y-1.5">
          {lines.map((l) => (
            <li key={l.id} className="flex flex-wrap items-center gap-2 text-sm">
              <MechanismChip mechanism={l.mechanism} />
              <span className="font-mono text-fg">{l.id.replace('->', ' → ')}</span>
            </li>
          ))}
        </ul>
      )}
      <p className="text-xs text-muted">Conceptual: nothing was called or observed.</p>
    </div>
  )
}

/**
 * The diagram area of a scenario in either mode. On a phone the diagram is replaced by the vertical list of the step's calls (or, for a conceptual
 * step, of the lines it draws): a list reads better there than a shrunken picture of nineteen nodes.
 */
export function DiagramArea({ scenario, step, index, compact, reducedMotion }) {
  return (
    <div className="space-y-2">
      <FlowCaption scenario={scenario} index={index} />
      {compact ? (
        <CallSequence step={step} />
      ) : (
        <>
          <FlowCanvas scenario={scenario} index={index} compact={false} reducedMotion={reducedMotion} />
          <CallSequence step={step} />
        </>
      )}
      <MechanismLegend />
    </div>
  )
}

/** The three breaker states with the current one marked by text as well as colour. Read from the run's own evidence. */
export function BreakerStrip({ steps, index }) {
  const state = breakerState(steps, index)
  if (!state) return null
  return (
    <div className="flex flex-wrap items-center gap-2 rounded-lg border border-line bg-surface px-4 py-2" data-testid="breaker-strip" aria-label={`Circuit breaker state: ${state}`}>
      <span className="text-xs font-semibold uppercase tracking-wide text-muted">Circuit breaker</span>
      {BREAKER_STATES.map((s, i) => (
        <span key={s} className="flex items-center gap-2">
          <span aria-current={s === state ? 'step' : undefined} className={`rounded-full px-3 py-1 text-xs font-semibold ${s === state ? (s === 'OPEN' ? 'bg-error text-on-accent' : 'bg-accent text-on-accent') : 'bg-subtle text-fg-2'}`}>
            {s === state ? '● ' : ''}
            {s}
          </span>
          {i < BREAKER_STATES.length - 1 && <span aria-hidden="true" className="text-muted">→</span>}
        </span>
      ))}
      <span className="text-xs text-muted">from the run&apos;s own evidence</span>
    </div>
  )
}

/** Free concurrency permits per service (real Semaphores), from the run's evidence. */
export function BulkheadStrip({ steps, index }) {
  const p = permits(steps, index)
  const entries = Object.entries(p)
  if (entries.length === 0) return null
  return (
    <dl className="flex flex-wrap gap-x-6 gap-y-1 rounded-lg border border-line bg-surface px-4 py-2" data-testid="bulkhead-strip" aria-label="Free concurrency permits per service">
      {entries.map(([service, n]) => (
        <div key={service}>
          <dt className="text-xs capitalize text-muted">{service} permits free</dt>
          <dd className={`text-lg font-bold tabular-nums ${n === 0 ? 'text-error' : 'text-fg'}`}>{n}</dd>
        </div>
      ))}
      <p className="basis-full text-xs text-muted">Real Semaphore permits, read at this step. A call with no permit is refused locally and never reaches the service.</p>
    </dl>
  )
}

const SAGA_TONE = {
  COMPLETED: 'border-success text-success',
  COMPENSATED: 'border-warning text-warning',
  FAILED: 'border-error text-error',
  COMPENSATION_FAILED: 'border-error text-error',
  STARTED: 'border-line-strong text-fg-2',
}

/** The saga log kept by the Order service, with each state transition. Compensation rows are marked as new business operations. */
export function SagaLogTable({ step }) {
  const rows = sagaLog(step)
  if (rows.length === 0) return null
  return (
    <div className="overflow-x-auto rounded-lg border border-line bg-surface p-3" data-testid="saga-log">
      <h4 className="text-sm font-semibold text-fg">Saga log (kept by the Order service)</h4>
      <table className="mt-2 w-full text-left text-xs">
        <caption className="sr-only">Each saga step and the state it ended in</caption>
        <thead className="text-muted">
          <tr>
            <th scope="col" className="py-1 pr-3">#</th>
            <th scope="col" className="pr-3">Step</th>
            <th scope="col" className="pr-3">State</th>
            <th scope="col">Detail</th>
          </tr>
        </thead>
        <tbody className="font-mono">
          {rows.map((r) => (
            <tr key={r.seq} className="border-t border-line-subtle align-top">
              <td className="py-1 pr-3 tabular-nums">{r.seq}</td>
              <td className="pr-3">
                {r.step.startsWith('COMPENSATE_') && <span className="mr-1" aria-hidden="true">↺</span>}
                {r.step}
              </td>
              <td className="pr-3">
                <span className={`rounded-full border px-2 py-0.5 font-sans font-semibold ${SAGA_TONE[r.state] ?? 'border-line-strong text-fg-2'}`}>{r.state}</span>
              </td>
              <td className="font-sans text-fg-2">{r.detail}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <p className="mt-2 text-xs text-muted">A COMPENSATED row is a new business operation that reversed an earlier committed step. It is not a database rollback.</p>
    </div>
  )
}

/** The headline a step reports to its caller, when it records one. */
export function HeadlineStrip({ step }) {
  const value = headline(step)
  if (!value) return null
  return (
    <p className="rounded-md border border-line bg-surface px-3 py-2 text-sm" data-testid="headline">
      <span className="font-semibold text-fg">The caller sees: </span>
      <span className="font-mono text-fg-2">{value}</span>
    </p>
  )
}

/**
 * Every span of the run, as a tree. SIMULATED milliseconds (the logical clock) and MEASURED microseconds (the real elapsed time of the in-process
 * call) are separate, labelled columns. This is a simulation of tracing, not OpenTelemetry.
 */
export function SpanTable({ spans, highlighted }) {
  const rows = spanRows(spans ?? [])
  if (rows.length === 0) return null
  return (
    <Disclosure title="Trace of this run" meta={`${rows.length} spans · simulated tracing`}>
      <div className="overflow-x-auto" data-testid="span-table">
        <table className="w-full text-left text-xs">
          <caption className="sr-only">Spans of the run: service, operation, status, parent, correlation id, simulated and measured time</caption>
          <thead className="text-muted">
            <tr>
              <th scope="col" className="py-1 pr-3">Span</th>
              <th scope="col" className="pr-3">Service.operation</th>
              <th scope="col" className="pr-3">Status</th>
              <th scope="col" className="pr-3">Parent</th>
              <th scope="col" className="pr-3">Correlation</th>
              <th scope="col" className="pr-3" title="Logical clock: SIMULATED">Simulated ms</th>
              <th scope="col" className="pr-3" title="Real elapsed time of the in-process call: MEASURED">Measured µs</th>
              <th scope="col">Self ms</th>
            </tr>
          </thead>
          <tbody className="font-mono">
            {rows.map(({ span, depth, selfMillis }) => (
              <tr key={span.spanId} className={`border-t border-line-subtle ${highlighted?.has(span.spanId) ? 'bg-accent-soft' : ''}`} data-testid="span-row">
                <td className="py-1 pr-3">{span.spanId}</td>
                <td className="pr-3">
                  <span style={{ paddingLeft: `${depth * 14}px` }}>
                    {depth > 0 ? '└ ' : ''}
                    {span.service}.{span.operation}
                    {span.kind === 'EVENT' ? ' (event)' : ''}
                  </span>
                </td>
                <td className="pr-3 font-sans">{span.status}</td>
                <td className="pr-3">{span.parentSpanId ?? '–'}</td>
                <td className="pr-3">{span.correlationId}{span.correlationPropagated ? '' : ' (not passed on)'}</td>
                <td className="pr-3 tabular-nums">{span.simulatedMillis}</td>
                <td className="pr-3 tabular-nums">{span.measuredMicros}</td>
                <td className="tabular-nums">{selfMillis}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <p className="mt-2 text-xs text-muted">
          Simulated ms come from the logical clock; measured µs are real but differ on every run. Neither is a performance claim. Correlation ids and parent links are
          passed between method calls in one process, not over a network.
        </p>
      </div>
    </Disclosure>
  )
}
