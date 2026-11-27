import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { listMyChallenges, listTopics, startChallenge } from '../api/arenaApi'
import { describeError } from '../api/errors'
import AppHeader from '../components/AppHeader'
import DifficultyBadge from '../components/DifficultyBadge'
import { Button } from '../components/ui/Button'
import Card from '../components/ui/Card'
import Icon from '../components/ui/Icon'
import SectionHeading from '../components/ui/SectionHeading'
import StatusBadge from '../components/ui/StatusBadge'
import { EmptyState, ErrorState, LoadingState } from '../components/ui/States'

const DIFFICULTIES = ['EASY', 'MEDIUM', 'HARD']

/**
 * Challenge Arena entry. Also the "Test yourself" destination for learning modules:
 *   /arena?topic=<topic-slug>&difficulty=<EASY|MEDIUM|HARD>&count=<1-20>
 * pre-selects the options (the learner still presses Start).
 */
export default function ArenaPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const [topics, setTopics] = useState(null)
  const [history, setHistory] = useState(null)
  const [topic, setTopic] = useState(params.get('topic') ?? '')
  const [difficulty, setDifficulty] = useState(
    DIFFICULTIES.includes(params.get('difficulty')) ? params.get('difficulty') : '',
  )
  const [count, setCount] = useState(clampCount(params.get('count')))
  const [error, setError] = useState(null)
  const [starting, setStarting] = useState(false)

  useEffect(() => {
    let cancelled = false
    Promise.all([listTopics(), listMyChallenges({ size: 5 })])
      .then(([t, h]) => {
        if (!cancelled) {
          setTopics(t)
          setHistory(h)
        }
      })
      .catch((err) => {
        if (!cancelled) setError(describeError(err).message)
      })
    return () => {
      cancelled = true
    }
  }, [])

  const selected = topics?.find((t) => t.slug === topic)
  const available = topic ? selected?.activeQuestionCount ?? 0 : topics?.reduce((n, t) => n + t.activeQuestionCount, 0) ?? 0

  async function handleStart(event) {
    event.preventDefault()
    setStarting(true)
    setError(null)
    try {
      const attempt = await startChallenge({ topic, difficulty, count })
      navigate(`/arena/attempts/${attempt.id}`)
    } catch (err) {
      setError(describeError(err).message)
      setStarting(false)
    }
  }

  return (
    <div className="min-h-screen bg-app text-fg">
      <AppHeader />
      <main className="mx-auto max-w-4xl space-y-6 p-4 sm:p-6">
        <header>
          <h1 className="text-2xl font-bold sm:text-3xl">Challenge Arena</h1>
          <p className="mt-1 text-fg-2">
            Pick a topic and difficulty. Answers are checked on the server; correct answers and explanations are shown after
            you submit.
          </p>
        </header>

        {error && <ErrorState>{error}</ErrorState>}

        <form onSubmit={handleStart} className="space-y-5 rounded-lg border border-line bg-surface p-4 shadow-sm sm:p-5">
          <fieldset>
            <legend className="text-sm font-semibold text-fg">1. Topic</legend>
            {!topics && !error && <LoadingState label="Loading topics…" />}
            <div className="mt-2 grid gap-3 sm:grid-cols-2">
              {topics && (
                <TopicOption
                  checked={topic === ''}
                  onChange={() => setTopic('')}
                  title="All topics"
                  detail={`${available} question(s) available`}
                  icon="book"
                />
              )}
              {topics?.map((t) => (
                <TopicOption
                  key={t.slug}
                  checked={topic === t.slug}
                  onChange={() => setTopic(t.slug)}
                  title={t.name}
                  detail={`${t.activeQuestionCount} question(s) - ${t.description}`}
                  disabled={t.activeQuestionCount === 0}
                />
              ))}
            </div>
          </fieldset>

          <div className="grid gap-4 sm:grid-cols-2">
            <fieldset>
              <legend className="text-sm font-semibold text-fg">2. Difficulty</legend>
              <div className="mt-2 flex flex-wrap gap-2">
                {['', ...DIFFICULTIES].map((d) => (
                  <label
                    key={d || 'any'}
                    className={`cursor-pointer rounded-md border px-3 py-1.5 text-sm font-medium transition-colors focus-within:ring-2 focus-within:ring-accent ${
                      difficulty === d ? 'border-accent bg-accent text-on-accent' : 'border-line-strong bg-surface text-fg-2 hover:bg-elevated'
                    }`}
                  >
                    <input
                      type="radio"
                      name="difficulty"
                      value={d}
                      checked={difficulty === d}
                      onChange={() => setDifficulty(d)}
                      className="sr-only"
                    />
                    {d === '' ? 'Any' : d.charAt(0) + d.slice(1).toLowerCase()}
                  </label>
                ))}
              </div>
            </fieldset>
            <div>
              <label htmlFor="question-count" className="text-sm font-semibold text-fg">
                3. Questions
              </label>
              <div className="mt-2 flex items-center gap-2">
                <input
                  id="question-count"
                  type="number"
                  min={1}
                  max={20}
                  value={count}
                  onChange={(e) => setCount(clampCount(e.target.value))}
                  className="w-24 rounded-md border border-line-strong px-3 py-1.5 text-sm"
                />
                <span className="text-xs text-muted">1 to 20</span>
              </div>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-3 border-t border-line-subtle pt-4">
            <Button type="submit" disabled={starting || !topics || available === 0}>
              {starting ? 'Starting…' : 'Start challenge'}
              <Icon name="arrow" className="h-4 w-4" />
            </Button>
            {topics && available === 0 && <p className="text-sm text-muted">No active questions in this topic yet.</p>}
          </div>
        </form>

        <section className="space-y-3">
          <SectionHeading title="Your recent challenges" />
          {!history && !error && <LoadingState />}
          {history?.content.length === 0 && <EmptyState title="No challenges yet">Start your first one above.</EmptyState>}
          {history?.content.length > 0 && (
            <Card padded={false}>
              <ul className="divide-y divide-line-subtle">
                {history.content.map((a) => (
                  <li key={a.id} className="flex flex-wrap items-center justify-between gap-x-4 gap-y-1 px-4 py-3 text-sm">
                    <span className="flex items-center gap-2 font-medium">
                      {a.topicName ?? 'All topics'} <DifficultyBadge difficulty={a.difficulty} />
                    </span>
                    <span className="text-muted">{new Date(a.startedAt).toLocaleString()}</span>
                    {a.status === 'SUBMITTED' ? (
                      <Link to={`/arena/attempts/${a.id}/result`} className="font-medium text-accent hover:underline">
                        Score {a.score}/{a.totalQuestions}
                      </Link>
                    ) : (
                      <Link to={`/arena/attempts/${a.id}`} className="inline-flex items-center gap-2 font-medium text-accent hover:underline">
                        <StatusBadge tone="warning">In progress</StatusBadge> Continue
                      </Link>
                    )}
                  </li>
                ))}
              </ul>
            </Card>
          )}
        </section>
      </main>
    </div>
  )
}

function clampCount(value) {
  const n = parseInt(value, 10)
  if (Number.isNaN(n)) return 10
  return Math.min(20, Math.max(1, n))
}

function TopicOption({ checked, onChange, title, detail, disabled, icon = 'route' }) {
  return (
    <label
      className={`flex gap-3 rounded-lg border p-3 text-sm transition-colors focus-within:ring-2 focus-within:ring-accent ${
        checked ? 'border-accent bg-accent-soft shadow-sm' : 'border-line bg-surface hover:border-line-strong hover:bg-elevated'
      } ${disabled ? 'cursor-not-allowed opacity-50' : 'cursor-pointer'}`}
    >
      <input type="radio" name="topic" checked={checked} onChange={onChange} disabled={disabled} className="sr-only" />
      <span
        className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-md ${
          checked ? 'bg-accent text-on-accent' : 'bg-subtle text-fg-2'
        }`}
      >
        <Icon name={checked ? 'check' : icon} className="h-4 w-4" />
      </span>
      <span className="min-w-0 break-words">
        <span className="block font-semibold text-fg">{title}</span>
        <span className="block text-xs text-muted">{detail}</span>
      </span>
    </label>
  )
}
