import { useEffect, useState } from 'react'
import { Navigate, useNavigate, useParams } from 'react-router-dom'
import { getChallenge, submitChallenge } from '../api/arenaApi'
import { describeError } from '../api/errors'
import AppHeader from '../components/AppHeader'
import DifficultyBadge from '../components/DifficultyBadge'
import { Button } from '../components/ui/Button'
import ProgressBar from '../components/ui/ProgressBar'
import { ErrorState, LoadingState } from '../components/ui/States'

export default function QuizPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [attempt, setAttempt] = useState(null)
  const [chosen, setChosen] = useState({}) // questionId -> optionId
  const [error, setError] = useState(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    let cancelled = false
    getChallenge(id)
      .then((a) => {
        if (!cancelled) setAttempt(a)
      })
      .catch((err) => {
        if (!cancelled) setError(describeError(err).message)
      })
    return () => {
      cancelled = true
    }
  }, [id])

  if (attempt?.status === 'SUBMITTED') {
    return <Navigate to={`/arena/attempts/${id}/result`} replace />
  }

  const answered = Object.keys(chosen).length

  async function handleSubmit() {
    const unanswered = attempt.totalQuestions - answered
    if (unanswered > 0 && !window.confirm(`${unanswered} question(s) unanswered; they count as incorrect. Submit anyway?`)) {
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      const answers = Object.entries(chosen).map(([questionId, optionId]) => ({
        questionId: Number(questionId),
        optionId,
      }))
      await submitChallenge(id, answers)
      navigate(`/arena/attempts/${id}/result`, { replace: true })
    } catch (err) {
      if (err.response?.status === 409) {
        // Already submitted (e.g. in another tab): the stored result is the truth.
        navigate(`/arena/attempts/${id}/result`, { replace: true })
        return
      }
      setError(describeError(err).message)
      setSubmitting(false)
    }
  }

  return (
    <div className="min-h-screen bg-app text-fg">
      <AppHeader />
      <main className="mx-auto max-w-3xl space-y-4 p-4 pb-28 sm:p-6 sm:pb-28">
        {error && <ErrorState>{error}</ErrorState>}
        {!attempt && !error && <LoadingState label="Loading your challenge…" />}
        {attempt && (
          <>
            <div className="space-y-2">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <h1 className="text-2xl font-bold">{attempt.topicName ?? 'All topics'}</h1>
                <span className="text-sm font-medium text-fg-2">
                  {answered} of {attempt.totalQuestions} answered
                </span>
              </div>
              <ProgressBar value={(answered / attempt.totalQuestions) * 100} label="Questions answered" />
            </div>
            {attempt.questions.map((q, index) => (
              <fieldset key={q.id} className="rounded-lg border border-line bg-surface p-4 shadow-sm sm:p-5">
                <legend className="sr-only">Question {index + 1}</legend>
                <p className="flex items-start gap-3 font-medium">
                  <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-accent-soft text-sm font-bold text-accent">
                    {index + 1}
                  </span>
                  <span>
                    {q.text}{' '}
                    <span className="align-middle">
                      <DifficultyBadge difficulty={q.difficulty} />
                    </span>
                  </span>
                </p>
                <div className="mt-3 space-y-2">
                  {q.options.map((o) => {
                    const picked = chosen[q.id] === o.id
                    return (
                      <label
                        key={o.id}
                        className={`flex cursor-pointer items-start gap-3 rounded-md border p-3 text-sm transition-colors focus-within:ring-2 focus-within:ring-accent ${
                          picked ? 'border-accent bg-accent-soft' : 'border-line hover:border-line-strong hover:bg-elevated'
                        }`}
                      >
                        <input
                          type="radio"
                          name={`q-${q.id}`}
                          checked={picked}
                          onChange={() => setChosen((prev) => ({ ...prev, [q.id]: o.id }))}
                          className="mt-0.5 h-4 w-4 accent-indigo-600"
                        />
                        <span>{o.text}</span>
                      </label>
                    )
                  })}
                </div>
              </fieldset>
            ))}
            <div className="fixed inset-x-0 bottom-0 z-20 border-t border-line bg-surface/95 px-4 py-3 backdrop-blur">
              <div className="mx-auto flex max-w-3xl items-center justify-between gap-3">
                <span className="text-sm text-fg-2">
                  {answered === attempt.totalQuestions ? 'All questions answered.' : `${attempt.totalQuestions - answered} unanswered`}
                </span>
                <Button onClick={handleSubmit} disabled={submitting}>
                  {submitting ? 'Submitting…' : 'Submit answers'}
                </Button>
              </div>
            </div>
          </>
        )}
      </main>
    </div>
  )
}
