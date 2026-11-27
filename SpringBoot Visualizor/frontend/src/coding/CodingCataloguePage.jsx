import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import AppHeader from '../components/AppHeader'
import { ButtonLink } from '../components/ui/Button'
import Card from '../components/ui/Card'
import DifficultyBadge from '../components/DifficultyBadge'
import { EmptyState, ErrorState, LoadingState } from '../components/ui/States'
import StatusBadge from '../components/ui/StatusBadge'
import { getCodingProgress, listCodingProblems } from './codingApi'
import { describeLoadError } from './codingErrors'
import { languageLabel } from './verdicts'

function Status({ problem }) {
  if (problem.accepted) return <StatusBadge tone="success">Accepted</StatusBadge>
  if (problem.attempts > 0) return <StatusBadge tone="warning">Attempted, {problem.attempts} submission{problem.attempts === 1 ? '' : 's'}</StatusBadge>
  return <StatusBadge>Not attempted</StatusBadge>
}

const DIFFICULTY_ORDER = ['EASY', 'MEDIUM', 'HARD']

/** Topic and difficulty filters. They only narrow the list the API already returned; nothing is requested again. */
function Filters({ problems, topic, difficulty, onTopic, onDifficulty, shown }) {
  const topics = useMemo(() => {
    const seen = new Map()
    problems.forEach((p) => seen.set(p.topicSlug, p.topicName))
    return [...seen.entries()].map(([slug, name]) => ({ slug, name }))
  }, [problems])
  const difficulties = useMemo(() => DIFFICULTY_ORDER.filter((d) => problems.some((p) => p.difficulty === d)), [problems])
  const selectClass = 'rounded-md border border-line-strong bg-surface px-2 py-1.5 text-sm text-fg'
  return (
    <div className="flex flex-wrap items-end gap-4" role="group" aria-label="Filter the coding problems">
      <label className="flex flex-col gap-1 text-sm font-medium text-fg">
        Topic
        <select value={topic} onChange={(e) => onTopic(e.target.value)} className={selectClass}>
          <option value="">All topics</option>
          {topics.map((t) => (
            <option key={t.slug} value={t.slug}>
              {t.name}
            </option>
          ))}
        </select>
      </label>
      <label className="flex flex-col gap-1 text-sm font-medium text-fg">
        Difficulty
        <select value={difficulty} onChange={(e) => onDifficulty(e.target.value)} className={selectClass}>
          <option value="">All difficulties</option>
          {difficulties.map((d) => (
            <option key={d} value={d}>
              {d.charAt(0) + d.slice(1).toLowerCase()}
            </option>
          ))}
        </select>
      </label>
      <p className="pb-2 text-sm text-fg-2" role="status" data-testid="filter-count">
        Showing {shown} of {problems.length} problem{problems.length === 1 ? '' : 's'}
      </p>
    </div>
  )
}

export default function CodingCataloguePage() {
  const [state, setState] = useState({ status: 'loading', problems: [], message: '' })
  const [progress, setProgress] = useState(null)
  const [topic, setTopic] = useState('')
  const [difficulty, setDifficulty] = useState('')
  const visible = useMemo(
    () =>
      state.problems
        .filter((p) => (!topic || p.topicSlug === topic) && (!difficulty || p.difficulty === difficulty))
        // easier problems first; problems of equal difficulty keep the order the server gave
        .sort((a, b) => DIFFICULTY_ORDER.indexOf(a.difficulty) - DIFFICULTY_ORDER.indexOf(b.difficulty)),
    [state.problems, topic, difficulty],
  )

  useEffect(() => {
    let cancelled = false
    listCodingProblems()
      .then((problems) => !cancelled && setState({ status: 'ready', problems, message: '' }))
      .catch((err) => !cancelled && setState({ status: 'error', problems: [], message: describeLoadError(err).message }))
    // The summary is a convenience: if it cannot be read, the catalogue is still useful.
    getCodingProgress()
      .then((p) => !cancelled && setProgress(p))
      .catch(() => !cancelled && setProgress(null))
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <div className="min-h-screen bg-app text-fg">
      <AppHeader />
      <main className="mx-auto max-w-5xl space-y-6 p-4 sm:p-6">
        <header>
          <h1 className="text-2xl font-bold sm:text-3xl">Coding practice</h1>
          <p className="mt-1 max-w-3xl text-fg-2">
            Write real Java, try it against sample tests, then submit it to be judged on every test, hidden ones included. Sample runs are for debugging; only a submission
            counts.
          </p>
        </header>

        {progress && (
          <Card className="flex flex-wrap items-center justify-between gap-3">
            <p className="text-sm text-fg-2" data-testid="progress-summary">
              <strong className="text-fg">{progress.problemsAccepted}</strong> accepted · <strong className="text-fg">{progress.problemsAttempted}</strong> attempted ·{' '}
              <strong className="text-fg">{progress.totalFullSubmissions}</strong> submission{progress.totalFullSubmissions === 1 ? '' : 's'}
            </p>
            <Link to="/learn/coding/progress" className="text-sm text-accent underline">
              Your coding progress
            </Link>
          </Card>
        )}

        {state.status === 'loading' && <LoadingState label="Loading the coding problems…" />}
        {state.status === 'error' && <ErrorState>{state.message}</ErrorState>}
        {state.status === 'ready' && state.problems.length === 0 && (
          <EmptyState title="No coding problems yet">There are no coding problems available right now. Check back later.</EmptyState>
        )}
        {state.status === 'ready' && state.problems.length > 1 && (
          <Filters problems={state.problems} topic={topic} difficulty={difficulty} onTopic={setTopic} onDifficulty={setDifficulty} shown={visible.length} />
        )}
        {state.status === 'ready' && state.problems.length > 0 && visible.length === 0 && (
          <EmptyState title="No problems match these filters">
            Try another topic or difficulty.{' '}
            <button type="button" className="text-accent underline" onClick={() => { setTopic(''); setDifficulty('') }}>
              Show all problems
            </button>
          </EmptyState>
        )}
        {state.status === 'ready' && visible.length > 0 && (
          <ul className="grid gap-4 sm:grid-cols-2" aria-label="Coding problems">
            {visible.map((p) => (
              <li key={p.slug}>
                <Card as="article" className="flex h-full flex-col" data-testid={`problem-${p.slug}`}>
                  <div className="flex flex-wrap items-center gap-2">
                    <DifficultyBadge difficulty={p.difficulty} />
                    <StatusBadge>{p.topicName}</StatusBadge>
                    <StatusBadge tone="info">{languageLabel(p.language)}</StatusBadge>
                  </div>
                  <h2 className="mt-3 text-lg font-semibold">
                    <Link to={`/learn/coding/${p.slug}`} className="hover:text-accent focus:outline-none focus-visible:underline">
                      {p.title}
                    </Link>
                  </h2>
                  <p className="mt-1 flex-1 text-sm text-fg-2">{p.summary}</p>
                  <div className="mt-4 flex flex-wrap items-center justify-between gap-2">
                    <Status problem={p} />
                    <ButtonLink to={`/learn/coding/${p.slug}`} size="sm">
                      {p.attempts > 0 ? 'Continue' : 'Open problem'}
                    </ButtonLink>
                  </div>
                </Card>
              </li>
            ))}
          </ul>
        )}
      </main>
    </div>
  )
}
