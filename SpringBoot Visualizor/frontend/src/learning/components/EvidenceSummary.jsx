import { checkExpectation, summarizeTrace } from '../schema/compare'

/**
 * The "Observe" stage of any traced module: what the browser saw next to what the server observed, the documented
 * expectation (when there is one), the captured SQL with its caveat, and what the evidence cannot show.
 * `expectation` is already resolved for the signed-in role (or null).
 */
export default function EvidenceSummary({ liveRun, expectation, emptyHint }) {
  if (!liveRun) {
    return <p className="rounded-lg border border-line bg-surface p-3 shadow-sm text-sm text-fg-2">{emptyHint}</p>
  }
  const { browser, trace } = liveRun
  const rows = expectation ? checkExpectation(expectation, summarizeTrace(trace)) : []
  const mismatch = browser.status !== trace.status
  return (
    <div className="space-y-3">
      <div className="grid gap-3 md:grid-cols-2">
        <section className="rounded-lg border border-line bg-surface p-3 shadow-sm text-sm" aria-label="What your browser saw">
          <h3 className="font-semibold">What your browser saw</h3>
          <dl className="mt-2 grid grid-cols-[auto_1fr] gap-x-4 gap-y-1">
            <dt className="text-muted">Status</dt>
            <dd>{browser.status}</dd>
            <dt className="text-muted">Content type</dt>
            <dd className="font-mono text-xs">{browser.contentType}</dd>
            <dt className="text-muted">Body size</dt>
            <dd>{browser.bytes} bytes (body not displayed)</dd>
            <dt className="text-muted">Round trip</dt>
            <dd>{browser.elapsedMs} ms</dd>
          </dl>
        </section>
        <section className="rounded-lg border border-line bg-surface p-3 shadow-sm text-sm" aria-label="What the server observed">
          <h3 className="font-semibold">What the server observed</h3>
          <dl className="mt-2 grid grid-cols-[auto_1fr] gap-x-4 gap-y-1">
            <dt className="text-muted">Status</dt>
            <dd>{trace.status}</dd>
            <dt className="text-muted">Controller invoked</dt>
            <dd>{trace.handlerInvoked ? 'yes' : 'no'}</dd>
            <dt className="text-muted">SQL statements prepared</dt>
            <dd>{trace.sqlCount}</dd>
            <dt className="text-muted">Server time</dt>
            <dd>{trace.serverDurationMicros} µs</dd>
          </dl>
        </section>
      </div>
      {mismatch && (
        <p role="alert" className="rounded-md border border-error-line bg-error-soft px-3 py-2 text-sm text-error">
          The browser and the server report different status codes ({browser.status} vs {trace.status}). Treat this trace
          with caution.
        </p>
      )}
      {rows.length > 0 && (
        <ul className="text-sm" aria-label="Documented expectations">
          {rows.map((r) => (
            <li key={r.field} className={r.ok ? 'text-success' : 'text-error'}>
              {r.ok ? '✓' : '✗'} {r.field}: documented {String(r.expected)}, observed {String(r.observed)}
            </li>
          ))}
        </ul>
      )}
      {trace.sql.length > 0 && (
        <section aria-label="Captured SQL">
          <h3 className="text-sm font-semibold">SQL text prepared by Hibernate ({trace.sql.length})</h3>
          <p className="rounded bg-warning-soft p-2 text-xs text-warning">
            Placeholders (?) stand for values that are not captured. This text does not show what MySQL returned or how
            it executed the statement.
          </p>
          <ol className="mt-2 space-y-2">
            {trace.sql.map((s) => (
              <li key={s.index}>
                <div className="text-xs text-muted">
                  {s.index}. {s.phase === 'SECURITY' ? 'before any controller' : 'while handling the request'} - {s.trigger}
                </div>
                <pre className="overflow-x-auto rounded bg-code p-2 text-xs text-fg" tabIndex={0}>
                  <code>{s.sql}</code>
                </pre>
              </li>
            ))}
          </ol>
        </section>
      )}
      <section aria-label="Limits of this evidence">
        <h3 className="text-sm font-semibold">What this evidence cannot tell you</h3>
        <ul className="mt-1 list-disc space-y-1 pl-5 text-sm text-fg-2">
          {trace.limitations.map((l) => (
            <li key={l}>{l}</li>
          ))}
        </ul>
      </section>
    </div>
  )
}
