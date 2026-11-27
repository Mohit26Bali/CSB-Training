import { checkExpectation, expectationForRole, skippedSteps, summarizeTrace } from '../schema/compare'
import CodeInspector from './CodeInspector'

/**
 * Predefined What If experiments. LIVE ones send one real, modified request (never a change to server settings);
 * SIMULATED ones are explanations of configurations that cannot be changed from a browser, and say so.
 * Every result shows: what changed, what was observed (or, if simulated, what is documented), why, and the code.
 */
export default function WhatIfPanel({ experiments, results, role, meta, codeRefs, onRun, onShowTrace, renderDetails }) {
  return (
    <div className="space-y-4">
      {experiments.map((exp) => (
        <article key={exp.id} id={`experiment-${exp.id}`} className="rounded-lg border border-line bg-surface p-4">
          <header className="flex flex-wrap items-center justify-between gap-2">
            <h3 className="text-base font-semibold">{exp.title}</h3>
            <span
              className={`rounded border px-2 py-0.5 text-xs font-semibold ${
                exp.kind === 'LIVE'
                  ? 'border-success bg-success-soft text-success'
                  : 'border-dashed border-conceptual/70 bg-conceptual-soft text-conceptual'
              }`}
            >
              {exp.kind === 'LIVE' ? '● LIVE experiment: a real request' : '○ SIMULATED: explanation only'}
            </span>
          </header>
          <p className="mt-2 text-sm">
            <span className="font-medium">What changes: </span>
            {exp.changes}
          </p>
          {exp.kind === 'LIVE' ? (
            <LiveExperiment exp={exp} result={results[exp.id]} role={role} codeRefs={codeRefs} onRun={onRun} onShowTrace={onShowTrace} renderDetails={renderDetails} />
          ) : (
            <SimulatedExperiment exp={exp} meta={meta} codeRefs={codeRefs} />
          )}
        </article>
      ))}
    </div>
  )
}

function LiveExperiment({ exp, result, role, codeRefs, onRun, onShowTrace, renderDetails }) {
  const running = result?.status === 'running'
  const done = result?.status === 'done'
  const expectation = expectationForRole(exp.expectation, role)
  const observed = done ? summarizeTrace(result.experiment.trace) : null
  const baseline = done ? summarizeTrace(result.baseline.trace) : null
  const rows = observed ? checkExpectation(expectation, observed) : []
  const skipped = observed ? skippedSteps(baseline, observed) : []

  return (
    <div className="mt-3 space-y-3">
      <button
        type="button"
        onClick={() => onRun(exp)}
        disabled={running}
        className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-on-accent shadow-sm transition-colors hover:bg-accent-hover focus:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-60"
      >
        {running ? 'Running…' : done ? 'Run again' : 'Run this experiment'}
      </button>
      {result?.status === 'error' && (
        <p role="alert" className="rounded-md border border-error-line bg-error-soft px-3 py-2 text-sm text-error">
          {result.error}
        </p>
      )}
      {done && (
        <div className="space-y-3" aria-live="polite">
          <table className="w-full text-left text-sm">
            <caption className="sr-only">Normal request compared with the experiment</caption>
            <thead className="bg-subtle text-fg-2">
              <tr>
                <th className="px-2 py-1">Observed by the server</th>
                <th className="px-2 py-1">Normal request</th>
                <th className="px-2 py-1">This experiment</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td className="px-2 py-1">HTTP status</td>
                <td className="px-2 py-1">{baseline.status}</td>
                <td className="px-2 py-1 font-medium">{observed.status}</td>
              </tr>
              <tr>
                <td className="px-2 py-1">Controller invoked</td>
                <td className="px-2 py-1">{baseline.handlerInvoked ? 'yes' : 'no'}</td>
                <td className="px-2 py-1 font-medium">{observed.handlerInvoked ? 'yes' : 'no'}</td>
              </tr>
              <tr>
                <td className="px-2 py-1">SQL statements prepared</td>
                <td className="px-2 py-1">{baseline.sqlCount}</td>
                <td className="px-2 py-1 font-medium">{observed.sqlCount}</td>
              </tr>
              <tr>
                <td className="px-2 py-1">Steps skipped by the experiment</td>
                <td className="px-2 py-1">-</td>
                <td className="px-2 py-1 font-medium">{skipped.length ? skipped.join(', ') : 'none'}</td>
              </tr>
            </tbody>
          </table>

          {rows.length > 0 && (
            <ul className="text-sm" aria-label={`Documented expectation for the ${role} role`}>
              {rows.map((r) => (
                <li key={r.field} className={r.ok ? 'text-success' : 'text-error'}>
                  {r.ok ? '✓' : '✗'} {r.field}: documented {String(r.expected)}, observed {String(r.observed)}
                </li>
              ))}
              {!rows.every((r) => r.ok) && (
                <li className="text-error">The observation differs from what this lesson documents. Please report it.</li>
              )}
            </ul>
          )}

          <p className="text-sm">
            <span className="font-medium">Why: </span>
            {exp.consequence}
          </p>
          {renderDetails && renderDetails(exp, result)}
          <button
            type="button"
            onClick={() => onShowTrace(result.experiment)}
            className="text-sm text-accent hover:underline"
          >
            Open this request in the diagram
          </button>
          {exp.codeRefId && (
            <details>
              <summary className="cursor-pointer text-sm text-accent">See it in the code</summary>
              <div className="mt-2">
                <CodeInspector reference={codeRefs?.[exp.codeRefId]} />
              </div>
            </details>
          )}
        </div>
      )}
    </div>
  )
}

function SimulatedExperiment({ exp, meta, codeRefs }) {
  const current = exp.id === 'open-in-view' ? meta?.settings?.['spring.jpa.open-in-view'] : null
  return (
    <div className="mt-3 space-y-3 text-sm">
      <p>
        <span className="font-medium">Documented consequence: </span>
        {exp.consequence}
      </p>
      {current != null && (
        <p className="rounded bg-elevated p-2">
          Live value in this running application: <code>spring.jpa.open-in-view = {current}</code>
        </p>
      )}
      <p className="text-fg-2">
        <span className="font-medium">Where this is documented: </span>
        {exp.realBehaviourReference}
      </p>
      {exp.codeRefId && (
        <details>
          <summary className="cursor-pointer text-accent">See the related SpringLab code</summary>
          <div className="mt-2">
            <CodeInspector reference={codeRefs?.[exp.codeRefId]} />
          </div>
        </details>
      )}
    </div>
  )
}
