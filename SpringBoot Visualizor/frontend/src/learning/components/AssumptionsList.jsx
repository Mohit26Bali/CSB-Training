const VERSION_LABELS = {
  java: 'Java',
  springBoot: 'Spring Boot',
  springFramework: 'Spring Framework',
  springSecurity: 'Spring Security',
  hibernate: 'Hibernate ORM',
}

/**
 * States what the lesson assumes. Versions and settings are read from the running backend (GET /api/lab/meta),
 * so they describe this installation rather than being typed from memory.
 */
export default function AssumptionsList({ assumptions, meta, metaError }) {
  return (
    <section aria-label="Assumptions" className="rounded-lg border border-line bg-surface p-3 shadow-sm">
      <h3 className="text-sm font-semibold text-fg">Assumptions</h3>
      <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-fg-2">
        {assumptions.map((a) => (
          <li key={a}>{a}</li>
        ))}
      </ul>
      <h4 className="mt-3 text-xs font-semibold uppercase tracking-wide text-muted">
        Running backend (read live)
      </h4>
      {metaError && <p className="text-sm text-error">Could not read runtime details: {metaError}</p>}
      {!meta && !metaError && <p className="text-sm text-muted">Loading…</p>}
      {meta && (
        <dl className="mt-1 grid grid-cols-[auto_1fr] gap-x-4 gap-y-0.5 text-sm" data-testid="runtime-meta">
          {Object.entries(meta.versions).map(([key, value]) => (
            <div key={key} className="contents">
              <dt className="text-muted">{VERSION_LABELS[key] ?? key}</dt>
              <dd className="font-mono">{value}</dd>
            </div>
          ))}
          {Object.entries(meta.settings).map(([key, value]) => (
            <div key={key} className="contents">
              <dt className="text-muted">{key}</dt>
              <dd className="font-mono">{value}</dd>
            </div>
          ))}
        </dl>
      )}
    </section>
  )
}
