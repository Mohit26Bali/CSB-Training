import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import AppHeader from '../components/AppHeader'
import Card from '../components/ui/Card'
import { ErrorState, LoadingState } from '../components/ui/States'
import StatusBadge from '../components/ui/StatusBadge'
import CodeEditor from './CodeEditor'
import { getSubmission } from './codingApi'
import { describeLoadError } from './codingErrors'
import { CompilerOutput, TestList, VerdictBadge } from './components'
import { formatTime, verdictInfo } from './verdicts'

/** One of the caller's own past submissions. Someone else's id is a 404 and is shown as "not found", with no hint that it exists. */
export default function CodingSubmissionPage() {
  const { id } = useParams()
  const [loaded, setLoaded] = useState({ id: null })

  useEffect(() => {
    let cancelled = false
    getSubmission(id)
      .then((data) => !cancelled && setLoaded({ id, status: 'ready', data }))
      .catch((err) => !cancelled && setLoaded({ id, status: 'error', error: describeLoadError(err) }))
    return () => {
      cancelled = true
    }
  }, [id])

  // a result belongs to the id it was loaded for; until the one for the current id arrives the page is loading
  const state = loaded.id === id ? loaded : { status: 'loading' }
  const s = state.data
  return (
    <div className="min-h-screen bg-app text-fg">
      <AppHeader />
      <main className="mx-auto max-w-5xl space-y-4 p-4 sm:p-6">
        <Link to={s ? `/learn/coding/${s.problemSlug}` : '/learn/coding'} className="text-sm text-accent underline">
          {s ? '← Back to the problem' : '← Coding practice'}
        </Link>
        <h1 className="text-2xl font-bold sm:text-3xl">Submission #{id}</h1>

        {state.status === 'loading' && <LoadingState label="Loading the submission…" />}
        {state.status === 'error' && <ErrorState>{state.error.status === 404 ? 'This submission was not found.' : state.error.message}</ErrorState>}

        {s && (
          <>
            <Card className="space-y-3">
              <div className="flex flex-wrap items-center gap-2">
                <VerdictBadge verdict={s.verdict} />
                <StatusBadge>problem version {s.problemVersion}</StatusBadge>
                <span className="text-sm text-muted">{formatTime(s.submittedAt)}</span>
              </div>
              <p className="text-sm text-fg-2">{verdictInfo(s.verdict).meaning}</p>
              {s.feedback && <p className="rounded-md border border-line-subtle bg-elevated px-3 py-2 text-sm text-fg-2">{s.feedback}</p>}
              <p className="text-sm text-fg" data-testid="counts">
                <strong>{s.passed}</strong> of <strong>{s.total}</strong> tests passed
                {s.hiddenTotal > 0 && (
                  <span className="text-fg-2">
                    {' '}
                    (hidden tests: {s.hiddenPassed} of {s.hiddenTotal} passed)
                  </span>
                )}
              </p>
              <CompilerOutput text={s.compilerOutput} />
              <TestList tests={s.visibleTests} label="Visible test results" />
              {s.hiddenTotal > 0 && <p className="text-xs text-muted">Hidden tests report only how many passed. Their inputs, expected values and names are never shown.</p>}
            </Card>

            <Card>
              <h2 className="mb-2 text-base font-semibold">The code you submitted</h2>
              <CodeEditor value={s.sourceCode} readOnly label={`Submitted source code of submission ${s.id}`} className="h-[24rem]" testId="submission-source" />
            </Card>
          </>
        )}
      </main>
    </div>
  )
}
