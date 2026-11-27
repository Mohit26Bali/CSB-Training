import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getLeaderboard, getMyProgress, listTopics } from '../api/arenaApi'
import { describeError } from '../api/errors'
import { useAuth } from '../auth/useAuth'
import AppHeader from '../components/AppHeader'

export default function ProgressPage() {
  const { user } = useAuth()
  const [progress, setProgress] = useState(null)
  const [topics, setTopics] = useState([])
  const [board, setBoard] = useState(null)
  const [boardTopic, setBoardTopic] = useState('')
  const [error, setError] = useState(null)

  useEffect(() => {
    let cancelled = false
    Promise.all([getMyProgress(), listTopics()])
      .then(([p, t]) => {
        if (!cancelled) {
          setProgress(p)
          setTopics(t)
        }
      })
      .catch((err) => {
        if (!cancelled) setError(describeError(err).message)
      })
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    let cancelled = false
    getLeaderboard({ topic: boardTopic, limit: 10 })
      .then((b) => {
        if (!cancelled) setBoard(b)
      })
      .catch((err) => {
        if (!cancelled) setError(describeError(err).message)
      })
    return () => {
      cancelled = true
    }
  }, [boardTopic])

  return (
    <div className="min-h-screen bg-app text-fg">
      <AppHeader />
      <main className="mx-auto max-w-4xl space-y-6 p-4 sm:p-6">
        <h1 className="text-2xl font-bold">Your progress</h1>
        {error && (
          <p role="alert" className="rounded-md border border-error-line bg-error-soft px-3 py-2 text-sm text-error">
            {error}
          </p>
        )}
        {!progress && !error && <p className="text-muted">Loading…</p>}

        {progress && (
          <>
            <div className="grid gap-3 sm:grid-cols-4">
              <Stat label="Challenges completed" value={progress.attemptsCompleted} />
              <Stat label="Questions answered" value={progress.questionsAnswered} />
              <Stat label="Correct answers" value={progress.correctAnswers} />
              <Stat label="Accuracy" value={`${progress.accuracyPercent}%`} />
            </div>
            {progress.attemptsCompleted === 0 && (
              <p className="text-sm text-fg-2">
                Nothing here yet.{' '}
                <Link to="/arena" className="text-accent hover:underline">
                  Take a challenge
                </Link>{' '}
                to start tracking progress.
              </p>
            )}
            <div className="grid gap-4 md:grid-cols-2">
              <BreakdownTable title="By topic" rows={progress.byTopic} />
              <BreakdownTable title="By difficulty" rows={progress.byDifficulty} />
            </div>
            {progress.recentAttempts.length > 0 && (
              <section>
                <h2 className="text-lg font-semibold">Recent challenges</h2>
                <ul className="mt-2 divide-y divide-line-subtle rounded-lg border border-line bg-surface shadow-sm">
                  {progress.recentAttempts.map((a) => (
                    <li key={a.id} className="flex justify-between px-4 py-2 text-sm">
                      <span>{a.topicName ?? 'All topics'}</span>
                      <Link to={`/arena/attempts/${a.id}/result`} className="text-accent hover:underline">
                        {a.score}/{a.totalQuestions}
                      </Link>
                    </li>
                  ))}
                </ul>
              </section>
            )}
          </>
        )}

        <section>
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h2 className="text-lg font-semibold">Leaderboard</h2>
            <label className="text-sm">
              Topic{' '}
              <select
                value={boardTopic}
                onChange={(e) => setBoardTopic(e.target.value)}
                className="rounded-md border border-line-strong px-2 py-1.5"
              >
                <option value="">All topics</option>
                {topics.map((t) => (
                  <option key={t.slug} value={t.slug}>
                    {t.name}
                  </option>
                ))}
              </select>
            </label>
          </div>
          <p className="text-xs text-muted">
            Students only, ranked by correct answers across submitted challenges (fewer questions attempted wins ties).
          </p>
          <div className="mt-2 overflow-x-auto rounded-lg border border-line bg-surface shadow-sm">
            <table className="w-full text-left text-sm">
              <thead className="bg-subtle text-fg-2">
                <tr>
                  <th className="px-3 py-2">#</th>
                  <th className="px-3 py-2">Student</th>
                  <th className="px-3 py-2">Points</th>
                  <th className="px-3 py-2">Challenges</th>
                  <th className="px-3 py-2">Accuracy</th>
                </tr>
              </thead>
              <tbody>
                {board?.length === 0 && (
                  <tr>
                    <td colSpan={5} className="px-3 py-3 text-muted">
                      No submitted challenges yet.
                    </td>
                  </tr>
                )}
                {board?.map((row) => (
                  <tr
                    key={row.username}
                    className={`border-t border-line-subtle ${row.username === user.username ? 'bg-accent-soft font-medium' : ''}`}
                  >
                    <td className="px-3 py-2">{row.rank}</td>
                    <td className="px-3 py-2">{row.username}</td>
                    <td className="px-3 py-2">{row.points}</td>
                    <td className="px-3 py-2">{row.attempts}</td>
                    <td className="px-3 py-2">{row.accuracyPercent}%</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      </main>
    </div>
  )
}

function Stat({ label, value }) {
  return (
    <div className="rounded-lg border border-line bg-surface p-4 shadow-sm">
      <div className="text-2xl font-bold">{value}</div>
      <div className="text-xs text-muted">{label}</div>
    </div>
  )
}

function BreakdownTable({ title, rows }) {
  return (
    <section>
      <h2 className="text-lg font-semibold">{title}</h2>
      <ul className="mt-2 space-y-2 rounded-lg border border-line bg-surface p-4 shadow-sm">
        {rows.length === 0 && <li className="text-sm text-muted">No data yet.</li>}
        {rows.map((r) => (
          <li key={r.key} className="text-sm">
            <div className="flex justify-between">
              <span>{r.label}</span>
              <span className="text-muted">
                {r.correct}/{r.questions} ({r.accuracyPercent}%)
              </span>
            </div>
            <div className="mt-1 h-2 rounded bg-subtle" role="img" aria-label={`${r.accuracyPercent}% correct`}>
              <div className="h-2 rounded bg-accent" style={{ width: `${r.accuracyPercent}%` }} />
            </div>
          </li>
        ))}
      </ul>
    </section>
  )
}
