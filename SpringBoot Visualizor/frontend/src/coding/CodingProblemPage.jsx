import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import AppHeader from '../components/AppHeader'
import { Button } from '../components/ui/Button'
import Card from '../components/ui/Card'
import DifficultyBadge from '../components/DifficultyBadge'
import { ErrorState, LoadingState } from '../components/ui/States'
import StatusBadge from '../components/ui/StatusBadge'
import CodeEditor from './CodeEditor'
import { getCodingProblem, getCodingProgress, listSubmissions, runCode, submitCode } from './codingApi'
import { describeLoadError, describeRequestError } from './codingErrors'
import { OutcomePanel, SubmissionList, Tabs } from './components'
import SimpleMarkdown from './SimpleMarkdown'
import { starterClassName } from './starterClass'
import { useUnsavedChangesWarning } from './useUnsavedChangesWarning'
import { languageLabel } from './verdicts'

/** The server's source limit (64 KiB); the page tells the learner before the server has to. */
export const MAX_SOURCE_BYTES = 64 * 1024
const byteLength = (s) => new TextEncoder().encode(s).length

const TABS = [
  { id: 'problem', label: 'Problem' },
  { id: 'code', label: 'Code' },
  { id: 'results', label: 'Results' },
]

function ProblemPanel({ problem }) {
  return (
    <div className="space-y-5">
      <section aria-labelledby="statement-heading">
        <h2 id="statement-heading" className="sr-only">
          Statement
        </h2>
        <SimpleMarkdown text={problem.statement} />
      </section>

      {problem.objectives?.length > 0 && (
        <section aria-labelledby="objectives-heading">
          <h3 id="objectives-heading" className="mb-2 text-base font-semibold text-fg">
            What you will practise
          </h3>
          <ul className="list-disc space-y-1 pl-5 text-sm text-fg-2">
            {problem.objectives.map((o) => (
              <li key={o}>{o}</li>
            ))}
          </ul>
        </section>
      )}

      {problem.constraints && (
        <section aria-labelledby="constraints-heading">
          <h3 id="constraints-heading" className="mb-2 text-base font-semibold text-fg">
            Constraints
          </h3>
          <SimpleMarkdown text={problem.constraints} />
        </section>
      )}

      {problem.examples?.length > 0 && (
        <section aria-labelledby="examples-heading">
          <h3 id="examples-heading" className="mb-2 text-base font-semibold text-fg">
            Examples
          </h3>
          <div className="space-y-3">
            {problem.examples.map((e) => (
              <article key={e.title} className="rounded-md border border-line-subtle bg-elevated p-3 text-sm">
                <h4 className="font-medium text-fg">{e.title}</h4>
                <dl className="mt-2 space-y-1.5 text-fg-2">
                  <div>
                    <dt className="text-xs font-semibold uppercase tracking-wide text-muted">Input</dt>
                    <dd className="whitespace-pre-wrap font-mono text-xs">{e.input}</dd>
                  </div>
                  <div>
                    <dt className="text-xs font-semibold uppercase tracking-wide text-muted">Output</dt>
                    <dd className="whitespace-pre-wrap font-mono text-xs">{e.output}</dd>
                  </div>
                  <div>
                    <dt className="text-xs font-semibold uppercase tracking-wide text-muted">Why</dt>
                    <dd>{e.explanation}</dd>
                  </div>
                </dl>
              </article>
            ))}
          </div>
        </section>
      )}

      <section aria-labelledby="tests-heading">
        <h3 id="tests-heading" className="mb-2 text-base font-semibold text-fg">
          Sample tests
        </h3>
        <p className="mb-2 text-sm text-fg-2">
          <strong className="text-fg">Run</strong> executes these {problem.sampleTests?.length ?? 0} tests.{' '}
          <strong className="text-fg">Submit</strong> also executes {problem.hiddenTestCount} hidden test{problem.hiddenTestCount === 1 ? '' : 's'}, whose details are never shown.
        </p>
        <ul className="space-y-2">
          {problem.sampleTests?.map((s) => (
            <li key={s.name} className="rounded-md border border-line-subtle p-3 text-sm">
              <p className="font-medium text-fg">{s.name}</p>
              <p className="mt-1 whitespace-pre-wrap font-mono text-xs text-fg-2">{s.input}</p>
              <p className="mt-1 whitespace-pre-wrap font-mono text-xs text-fg-2">→ {s.expected}</p>
            </li>
          ))}
        </ul>
      </section>
    </div>
  )
}

/** A different problem is a different workspace: keying by the slug starts every piece of state afresh. */
export default function CodingProblemPage() {
  const { slug } = useParams()
  return <Workspace key={slug} slug={slug} />
}

function Workspace({ slug }) {
  const [problem, setProblem] = useState(null)
  const [loadError, setLoadError] = useState(null)
  const [source, setSource] = useState('')
  const [baseline, setBaseline] = useState('')
  const [pending, setPending] = useState(null)
  const [outcome, setOutcome] = useState(null)
  const [requestError, setRequestError] = useState(null)
  const [history, setHistory] = useState({ status: 'loading', items: [] })
  const [progress, setProgress] = useState(null)
  const [tab, setTab] = useState('problem')
  const inFlight = useRef(false)
  const controller = useRef(null)
  const mounted = useRef(true)

  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
      controller.current?.abort()
    }
  }, [])

  const refreshHistory = useCallback(() => {
    listSubmissions(slug)
      .then((items) => mounted.current && setHistory({ status: 'ready', items }))
      .catch((err) => mounted.current && setHistory({ status: 'error', items: [], message: describeLoadError(err).message }))
  }, [slug])

  const refreshProgress = useCallback(() => {
    getCodingProgress()
      .then((p) => mounted.current && setProgress(p.problems.find((x) => x.slug === slug) ?? { attempts: 0, accepted: false }))
      .catch(() => mounted.current && setProgress(null))
  }, [slug])

  useEffect(() => {
    let cancelled = false
    getCodingProblem(slug)
      .then((p) => {
        if (cancelled) return
        setProblem(p)
        setSource(p.starterCode)
        setBaseline(p.starterCode)
      })
      .catch((err) => !cancelled && setLoadError(describeLoadError(err)))
    refreshHistory()
    refreshProgress()
    return () => {
      cancelled = true
    }
  }, [slug, refreshHistory, refreshProgress])

  const dirty = problem !== null && source !== baseline
  useUnsavedChangesWarning(dirty)

  const bytes = useMemo(() => byteLength(source), [source])
  const tooLarge = bytes > MAX_SOURCE_BYTES
  const className = starterClassName(problem?.starterCode)
  const empty = source.trim() === ''
  const blocked = pending !== null || tooLarge || empty

  async function execute(kind) {
    if (inFlight.current || tooLarge || empty) return
    inFlight.current = true
    const ctrl = new AbortController()
    controller.current = ctrl
    const sent = source
    setPending(kind)
    setRequestError(null)
    setOutcome(null)
    setTab('results')
    try {
      const data = kind === 'run' ? await runCode(slug, sent, ctrl.signal) : await submitCode(slug, sent, ctrl.signal)
      if (!mounted.current) return
      setOutcome({ kind, data })
      if (kind === 'submit') {
        setBaseline(sent) // an official submission is saved in the history
        refreshHistory()
        refreshProgress()
      }
    } catch (err) {
      const described = describeRequestError(err, kind)
      if (described && mounted.current) setRequestError(described)
    } finally {
      inFlight.current = false
      if (mounted.current) setPending(null)
    }
  }

  function reset() {
    if (pending) return
    if (source !== problem.starterCode && !window.confirm('Replace your code with the starter code? Your current code will be lost.')) return
    setSource(problem.starterCode)
  }

  if (loadError) {
    return (
      <Shell>
        <ErrorState>{loadError.status === 404 ? 'This coding problem does not exist or is no longer available.' : loadError.message}</ErrorState>
        <p className="mt-4">
          <Link to="/learn/coding" className="text-sm text-accent underline">
            ← Back to coding practice
          </Link>
        </p>
      </Shell>
    )
  }
  if (!problem) {
    return (
      <Shell>
        <LoadingState label="Loading the problem…" />
      </Shell>
    )
  }

  return (
    <Shell>
      <div className="mb-4">
        <Link to="/learn/coding" className="text-sm text-accent underline">
          ← Coding practice
        </Link>
        <h1 className="mt-1 text-2xl font-bold sm:text-3xl">{problem.title}</h1>
        <div className="mt-2 flex flex-wrap items-center gap-2 text-sm">
          <DifficultyBadge difficulty={problem.difficulty} />
          <StatusBadge>{problem.topicName}</StatusBadge>
          <StatusBadge tone="info">{languageLabel(problem.language)}</StatusBadge>
          <span className="text-xs text-muted">problem version {problem.version}</span>
          {progress &&
            (progress.accepted ? (
              <StatusBadge tone="success">Solved</StatusBadge>
            ) : progress.attempts > 0 ? (
              <StatusBadge tone="warning">
                {progress.attempts} attempt{progress.attempts === 1 ? '' : 's'}, not solved yet
              </StatusBadge>
            ) : (
              <StatusBadge>Not attempted</StatusBadge>
            ))}
        </div>
      </div>

      <Tabs label="Workspace sections" tabs={TABS} value={tab} onChange={setTab} className="mb-4 lg:hidden" />

      <div className="grid gap-4 lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)]">
        <Card
          id="panel-problem"
          role="tabpanel"
          aria-labelledby="tab-problem"
          aria-label="Problem"
          className={`${tab === 'problem' ? 'block' : 'hidden'} lg:block lg:max-h-[calc(100vh-10rem)] lg:overflow-y-auto`}
        >
          <ProblemPanel problem={problem} />
        </Card>

        <div className="min-w-0 space-y-4">
          <Card id="panel-code" role="tabpanel" aria-labelledby="tab-code" aria-label="Code" className={`${tab === 'code' ? 'block' : 'hidden'} lg:block`}>
            <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
              <div className="flex items-center gap-2">
                <h2 className="text-base font-semibold">Your solution</h2>
                <StatusBadge tone="info">{languageLabel(problem.language)}</StatusBadge>
                {dirty && <StatusBadge tone="warning">Not submitted</StatusBadge>}
              </div>
              <Button variant="ghost" size="sm" onClick={reset} disabled={pending !== null || source === problem.starterCode}>
                Reset to starter code
              </Button>
            </div>
            <CodeEditor value={source} onChange={setSource} label={`${problem.title}: Java source code`} className="h-[22rem] sm:h-[28rem]" />
            <p className="mt-1.5 text-xs text-muted">
              Tab indents. Press Escape, then Tab, to move focus out of the editor. Write the whole file, declaring only <code className="font-mono">{className}</code>.
            </p>
            {tooLarge && (
              <p role="alert" className="mt-2 text-sm text-error">
                Your code is {Math.ceil(bytes / 1024)} KiB; the limit is {MAX_SOURCE_BYTES / 1024} KiB.
              </p>
            )}
            {empty && <p className="mt-2 text-sm text-muted">The editor is empty. Reset to starter code to start again.</p>}

            <div className="mt-3 grid gap-3 sm:grid-cols-2">
              <div>
                <Button variant="secondary" className="w-full" onClick={() => execute('run')} disabled={blocked} aria-describedby="run-help">
                  {pending === 'run' ? 'Running sample tests…' : 'Run sample tests'}
                </Button>
                <p id="run-help" className="mt-1 text-xs text-muted">
                  Sample tests only. Not an official attempt; your progress does not change.
                </p>
              </div>
              <div>
                <Button className="w-full" onClick={() => execute('submit')} disabled={blocked} aria-describedby="submit-help">
                  {pending === 'submit' ? 'Submitting…' : 'Submit solution'}
                </Button>
                <p id="submit-help" className="mt-1 text-xs text-muted">
                  Runs every test, hidden ones included. Saved to your history and progress.
                </p>
              </div>
            </div>
          </Card>

          <div id="panel-results" role="tabpanel" aria-labelledby="tab-results" aria-label="Results" className={`${tab === 'results' ? 'block' : 'hidden'} space-y-4 lg:block`}>
            <Card aria-live="polite">
              <h2 className="mb-3 text-base font-semibold">Result</h2>
              {pending && (
                <div role="status" className="space-y-1">
                  <LoadingState label={pending === 'run' ? 'Running the sample tests…' : 'Submitting and judging every test…'} />
                  <p className="text-xs text-muted">This waits for the server. It usually takes a few seconds, and longer when the code runner was idle.</p>
                </div>
              )}
              {!pending && requestError && <ErrorState>{requestError.message}</ErrorState>}
              {!pending && !requestError && outcome && <OutcomePanel kind={outcome.kind} data={outcome.data} />}
              {!pending && !requestError && !outcome && <p className="text-sm text-muted">Run the sample tests to try your code, or submit it for an official result.</p>}
            </Card>

            <Card>
              <div className="mb-2 flex items-center justify-between gap-2">
                <h2 className="text-base font-semibold">Your submissions</h2>
                <Link to="/learn/coding/progress" className="text-sm text-accent underline">
                  Your coding progress
                </Link>
              </div>
              {history.status === 'loading' && <LoadingState label="Loading your submissions…" />}
              {history.status === 'error' && <ErrorState>{history.message}</ErrorState>}
              {history.status === 'ready' && history.items.length === 0 && <p className="text-sm text-muted">No official submissions yet. Sample runs are not listed here.</p>}
              {history.status === 'ready' && history.items.length > 0 && <SubmissionList items={history.items} />}
            </Card>
          </div>
        </div>
      </div>
    </Shell>
  )
}

function Shell({ children }) {
  return (
    <div className="min-h-screen bg-app text-fg">
      <AppHeader />
      <main className="mx-auto max-w-7xl p-4 sm:p-6">{children}</main>
    </div>
  )
}
