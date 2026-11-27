import { useRef } from 'react'
import { Link } from 'react-router-dom'
import StatusBadge from '../components/ui/StatusBadge'
import { formatTime, testStatusInfo, verdictInfo } from './verdicts'

/** An accessible tab list (roving tabindex, arrow keys, Home and End). The panels are rendered by the caller with `role="tabpanel"`. */
export function Tabs({ label, tabs, value, onChange, className = '' }) {
  const refs = useRef({})
  function onKeyDown(event) {
    const i = tabs.findIndex((t) => t.id === value)
    let next = null
    if (event.key === 'ArrowRight') next = (i + 1) % tabs.length
    else if (event.key === 'ArrowLeft') next = (i - 1 + tabs.length) % tabs.length
    else if (event.key === 'Home') next = 0
    else if (event.key === 'End') next = tabs.length - 1
    if (next === null) return
    event.preventDefault()
    onChange(tabs[next].id)
    refs.current[tabs[next].id]?.focus()
  }
  return (
    <div role="tablist" aria-label={label} onKeyDown={onKeyDown} className={`flex gap-1 rounded-lg border border-line bg-sidebar p-1 ${className}`}>
      {tabs.map((t) => (
        <button
          key={t.id}
          ref={(el) => {
            refs.current[t.id] = el
          }}
          type="button"
          role="tab"
          id={`tab-${t.id}`}
          aria-selected={value === t.id}
          aria-controls={`panel-${t.id}`}
          tabIndex={value === t.id ? 0 : -1}
          onClick={() => onChange(t.id)}
          className={`flex-1 rounded-md px-3 py-2 text-sm font-medium transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-accent ${
            value === t.id ? 'bg-accent text-on-accent' : 'text-fg-2 hover:bg-hover hover:text-fg'
          }`}
        >
          {t.label}
        </button>
      ))}
    </div>
  )
}

export function VerdictBadge({ verdict }) {
  const info = verdictInfo(verdict)
  return <StatusBadge tone={info.tone}>{info.label}</StatusBadge>
}

/** One test line: the name, a status in words, and the message only when the API sent one (visible tests only). */
export function TestList({ tests, label }) {
  if (!tests?.length) return null
  return (
    <ul aria-label={label} className="divide-y divide-line-subtle rounded-md border border-line-subtle">
      {tests.map((t, i) => {
        const info = testStatusInfo(t.status)
        return (
          <li key={`${t.name}-${i}`} className="px-3 py-2 text-sm">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <span className="font-medium text-fg">{t.name}</span>
              <StatusBadge tone={info.tone}>{info.label}</StatusBadge>
            </div>
            {t.message && <p className="mt-1 whitespace-pre-wrap font-mono text-xs text-fg-2">{t.message}</p>}
          </li>
        )
      })}
    </ul>
  )
}

export function CompilerOutput({ text }) {
  if (!text) return null
  return (
    <div>
      <h4 className="mb-1 text-sm font-semibold text-fg">Compiler output</h4>
      <pre tabIndex={0} className="max-h-48 overflow-auto whitespace-pre-wrap rounded-md border border-warning-line bg-code p-3 font-mono text-xs text-fg">{text}</pre>
    </div>
  )
}

/**
 * The outcome of a Run or a Submit, using only the fields the API returned.
 *  - A sample run that passes is NOT shown as "Accepted": only an official submission can be accepted.
 *  - Hidden tests appear only as the aggregate counts the API gives.
 */
export function OutcomePanel({ kind, data }) {
  const isRun = kind === 'run'
  const info = verdictInfo(data.verdict)
  const samplesPassed = isRun && data.verdict === 'ACCEPTED'
  const tests = isRun ? data.tests : data.visibleTests
  return (
    <section aria-label={isRun ? 'Sample run result' : 'Submission result'} data-testid="outcome" className="space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <StatusBadge tone={isRun ? 'neutral' : 'brand'}>{isRun ? 'Sample run, not an official submission' : `Official submission #${data.id}`}</StatusBadge>
        {samplesPassed ? <StatusBadge tone="info">All sample tests passed</StatusBadge> : <VerdictBadge verdict={data.verdict} />}
        {!isRun && data.problemVersion != null && <span className="text-xs text-muted">problem version {data.problemVersion}</span>}
        {!isRun && data.submittedAt && <span className="text-xs text-muted">{formatTime(data.submittedAt)}</span>}
      </div>

      {samplesPassed ? (
        <p className="text-sm text-fg-2">
          Your code passed the sample tests. That is <strong className="text-fg">not</strong> an official result: the hidden tests have not been run and your progress has not
          changed. Submit to be judged on every test.
        </p>
      ) : (
        <p className="text-sm text-fg-2">{info.meaning}</p>
      )}
      {data.feedback && !samplesPassed && <p className="rounded-md border border-line-subtle bg-elevated px-3 py-2 text-sm text-fg-2">{data.feedback}</p>}

      <p className="text-sm text-fg" data-testid="counts">
        {isRun ? (
          <>
            <strong>{data.passed}</strong> of <strong>{data.total}</strong> sample tests passed
          </>
        ) : (
          <>
            <strong>{data.passed}</strong> of <strong>{data.total}</strong> tests passed
            {data.hiddenTotal > 0 && (
              <span className="text-fg-2">
                {' '}
                (hidden tests: {data.hiddenPassed} of {data.hiddenTotal} passed)
              </span>
            )}
          </>
        )}
      </p>

      <CompilerOutput text={data.compilerOutput} />
      <TestList tests={tests} label={isRun ? 'Sample test results' : 'Visible test results'} />
      {!isRun && data.hiddenTotal > 0 && (
        <p className="text-xs text-muted">Hidden tests report only how many passed. Their inputs, expected values and names are never shown.</p>
      )}
    </section>
  )
}

export function SubmissionList({ items }) {
  return (
    <ul className="divide-y divide-line-subtle rounded-md border border-line-subtle" aria-label="Your submissions for this problem">
      {items.map((s) => (
        <li key={s.id}>
          <Link
            to={`/learn/coding/submissions/${s.id}`}
            className="flex flex-wrap items-center justify-between gap-2 px-3 py-2 text-sm hover:bg-hover focus:outline-none focus-visible:bg-hover focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-accent"
          >
            <span className="flex flex-wrap items-center gap-2">
              <span className="font-medium text-fg">#{s.id}</span>
              <VerdictBadge verdict={s.verdict} />
              <span className="text-fg-2">
                {s.passed} of {s.total} passed
              </span>
            </span>
            <span className="text-xs text-muted">
              version {s.problemVersion} · {formatTime(s.submittedAt)}
            </span>
          </Link>
        </li>
      ))}
    </ul>
  )
}
