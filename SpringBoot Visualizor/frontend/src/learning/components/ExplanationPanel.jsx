import ModeBadge from './ModeBadge'

const SQL_CAVEAT =
  'This is the SQL text Hibernate prepared, with ? placeholders instead of values. It does not show the values bound, ' +
  'what MySQL returned, or how the database executed it.'

/** Detailed explanation of the current step. The region is announced politely when the step changes. */
export default function ExplanationPanel({ step, index, total }) {
  if (!step) return null
  return (
    <section aria-live="polite" aria-label="Step explanation" className="rounded-lg border border-line bg-surface p-4 shadow-sm">
      <header className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="text-base font-semibold">
          <span className="mr-2 font-mono text-sm text-muted">
            {index + 1}/{total}
          </span>
          {step.title}
        </h3>
        <ModeBadge provenance={step.provenance} />
      </header>
      <p className="mt-2 text-sm text-fg-2">{step.explanation}</p>

      {step.evidence?.length > 0 && (
        <dl className="mt-3 grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm" aria-label="Observed evidence">
          {step.evidence.map((e) => (
            <div key={e.label} className="contents">
              <dt className="text-muted">{e.label}</dt>
              <dd className="break-words font-mono text-xs leading-5">{e.value}</dd>
            </div>
          ))}
        </dl>
      )}

      {step.sql?.length > 0 && (
        <div className="mt-3" data-testid="sql-block">
          <p className="rounded bg-warning-soft p-2 text-xs text-warning">{SQL_CAVEAT}</p>
          <ol className="mt-2 space-y-2">
            {step.sql.map((s) => (
              <li key={s.index}>
                <div className="text-xs text-muted">
                  Statement {s.index} - {s.phase === 'SECURITY' ? 'before any controller' : s.phase === 'EXPERIMENT' ? 'prepared during this step' : s.phase === 'SERVICE DB' ? 'run by a service on its own table' : s.phase === 'JDBC' ? 'handed to the JDBC driver' : s.phase === 'HIBERNATE' ? 'prepared by Hibernate' : 'while handling the request'}
                  {s.trigger ? ` - issued during ${s.trigger}` : ''}
                </div>
                <pre className="overflow-x-auto rounded-md bg-code p-3 text-xs text-fg" tabIndex={0}>
                  <code>{s.sql}</code>
                </pre>
              </li>
            ))}
          </ol>
        </div>
      )}
    </section>
  )
}
