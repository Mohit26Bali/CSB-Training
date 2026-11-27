import { useEffect, useState } from 'react'
import { Navigate, useParams } from 'react-router-dom'
import { getChallengeResult } from '../api/arenaApi'
import { describeError } from '../api/errors'
import AppHeader from '../components/AppHeader'
import DifficultyBadge from '../components/DifficultyBadge'
import { ButtonLink } from '../components/ui/Button'
import ProgressBar from '../components/ui/ProgressBar'
import StatusBadge from '../components/ui/StatusBadge'
import { ErrorState, LoadingState } from '../components/ui/States'

export default function ResultPage() {
  const { id } = useParams()
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)
  const [notSubmitted, setNotSubmitted] = useState(false)

  useEffect(() => {
    let cancelled = false
    getChallengeResult(id)
      .then((r) => {
        if (!cancelled) setResult(r)
      })
      .catch((err) => {
        if (cancelled) return
        // The server refuses results until the attempt is submitted (409): go back to the quiz.
        if (err.response?.status === 409) setNotSubmitted(true)
        else setError(describeError(err).message)
      })
    return () => {
      cancelled = true
    }
  }, [id])

  if (notSubmitted) {
    return <Navigate to={`/arena/attempts/${id}`} replace />
  }

  return (
    <div className="min-h-screen bg-app text-fg">
      <AppHeader />
      <main className="mx-auto max-w-3xl space-y-4 p-4 sm:p-6">
        {error && <ErrorState>{error}</ErrorState>}
        {!result && !error && <LoadingState label="Loading your result…" />}
        {result && (
          <>
            <section className="rounded-lg border border-line bg-surface p-4 shadow-sm sm:p-5">
              <h1 className="text-2xl font-bold">
                Score: {result.score} / {result.totalQuestions}{' '}
                <span className="text-lg font-normal text-fg-2">({result.percentage}%)</span>
              </h1>
              <p className="mt-1 text-sm text-fg-2">
                {result.topicName ?? 'All topics'} - submitted {new Date(result.submittedAt).toLocaleString()}
              </p>
              <div className="mt-3">
                <ProgressBar
                  value={result.percentage}
                  label="Score"
                  tone={result.percentage >= 70 ? 'bg-success' : result.percentage >= 40 ? 'bg-warning' : 'bg-error'}
                />
              </div>
              <div className="mt-4 flex flex-wrap gap-2">
                <ButtonLink to={result.topicSlug ? `/arena?topic=${result.topicSlug}` : '/arena'}>Try again</ButtonLink>
                <ButtonLink to="/progress" variant="secondary">
                  View progress
                </ButtonLink>
              </div>
            </section>

            {result.questions.map((q, index) => (
              <section key={q.questionId} className="rounded-lg border border-line bg-surface p-4 shadow-sm sm:p-5">
                <div className="flex flex-wrap items-center gap-2">
                  <StatusBadge tone={q.correct ? 'success' : q.answered ? 'danger' : 'neutral'}>
                    {q.correct ? 'Correct' : q.answered ? 'Incorrect' : 'Not answered'}
                  </StatusBadge>
                  <DifficultyBadge difficulty={q.difficulty} />
                </div>
                <p className="mt-2 font-medium">
                  {index + 1}. {q.text}
                </p>
                <ul className="mt-3 space-y-1.5 text-sm">
                  {q.options.map((o) => (
                    <li
                      key={o.id}
                      className={`rounded-md border px-3 py-2 ${
                        o.correct
                          ? 'border-success-line bg-success-soft text-success'
                          : o.selected
                            ? 'border-error-line bg-error-soft text-error'
                            : 'border-transparent text-fg-2'
                      }`}
                    >
                      {o.correct ? '✓ ' : o.selected ? '✗ ' : '○ '}
                      {o.text}
                      {o.selected && ' (your answer)'}
                      {o.correct && ' (correct answer)'}
                    </li>
                  ))}
                </ul>
                <p className="mt-3 rounded-md border-l-4 border-accent-line bg-elevated p-3 text-sm text-fg-2">
                  <span className="font-medium">Explanation: </span>
                  {q.explanation}
                </p>
              </section>
            ))}
          </>
        )}
      </main>
    </div>
  )
}
