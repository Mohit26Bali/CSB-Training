import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import AppHeader from '../components/AppHeader'
import Card from '../components/ui/Card'
import { EmptyState, ErrorState, LoadingState } from '../components/ui/States'
import StatusBadge from '../components/ui/StatusBadge'
import { getCodingProgress } from './codingApi'
import { describeLoadError } from './codingErrors'
import { VerdictBadge } from './components'
import { formatTime } from './verdicts'

/** The caller's coding progress, exactly as the API computes it from official submissions. Sample runs are not part of it. */
export default function CodingProgressPage() {
  const [state, setState] = useState({ status: 'loading' })

  useEffect(() => {
    let cancelled = false
    getCodingProgress()
      .then((data) => !cancelled && setState({ status: 'ready', data }))
      .catch((err) => !cancelled && setState({ status: 'error', message: describeLoadError(err).message }))
    return () => {
      cancelled = true
    }
  }, [])

  const p = state.data
  return (
    <div className="min-h-screen bg-app text-fg">
      <AppHeader />
      <main className="mx-auto max-w-4xl space-y-4 p-4 sm:p-6">
        <Link to="/learn/coding" className="text-sm text-accent underline">
          ← Coding practice
        </Link>
        <h1 className="text-2xl font-bold sm:text-3xl">Your coding progress</h1>
        <p className="text-sm text-fg-2">Counted from your official submissions. Sample runs are not counted, and neither are runs that failed because of a runner problem.</p>

        {state.status === 'loading' && <LoadingState label="Loading your progress…" />}
        {state.status === 'error' && <ErrorState>{state.message}</ErrorState>}

        {p && (
          <>
            <div className="grid gap-3 sm:grid-cols-3" data-testid="progress-totals">
              {[
                ['Problems accepted', p.problemsAccepted],
                ['Problems attempted', p.problemsAttempted],
                ['Total submissions', p.totalFullSubmissions],
              ].map(([label, value]) => (
                <Card key={label}>
                  <p className="text-sm text-fg-2">{label}</p>
                  <p className="mt-1 text-3xl font-bold text-fg">{value}</p>
                </Card>
              ))}
            </div>

            {p.problems.length === 0 ? (
              <EmptyState title="Nothing here yet">
                Submit a solution and it will appear here. <Link to="/learn/coding" className="text-accent underline">Browse the coding problems</Link>.
              </EmptyState>
            ) : (
              <Card padded={false}>
                <ul className="divide-y divide-line-subtle" aria-label="Problems you have attempted">
                  {p.problems.map((x) => (
                    <li key={x.slug} className="flex flex-wrap items-center justify-between gap-3 px-4 py-3 text-sm">
                      <div>
                        <Link to={`/learn/coding/${x.slug}`} className="font-medium text-fg hover:text-accent">
                          {x.title}
                        </Link>
                        <p className="mt-0.5 text-xs text-muted">
                          {x.attempts} submission{x.attempts === 1 ? '' : 's'}
                          {x.accepted && x.acceptedAt ? ` · accepted ${formatTime(x.acceptedAt)}` : ''}
                        </p>
                      </div>
                      <div className="flex flex-wrap items-center gap-2">
                        {x.accepted ? <StatusBadge tone="success">Accepted</StatusBadge> : <StatusBadge tone="warning">Not solved yet</StatusBadge>}
                        <span className="text-xs text-muted">latest:</span>
                        <VerdictBadge verdict={x.latestVerdict} />
                        <Link to={`/learn/coding/submissions/${x.latestSubmissionId}`} className="text-xs text-accent underline">
                          view submission #{x.latestSubmissionId}
                        </Link>
                      </div>
                    </li>
                  ))}
                </ul>
              </Card>
            )}
          </>
        )}
      </main>
    </div>
  )
}
