import { useEffect, useState } from 'react'
import { listMyChallenges, getMyProgress, listTopics } from '../api/arenaApi'
import { describeError } from '../api/errors'
import { HERO_DASHBOARD } from '../assets/heroImages'
import { useAuth } from '../auth/useAuth'
import AppHeader from '../components/AppHeader'
import ModuleCard from '../components/ModuleCard'
import Card from '../components/ui/Card'
import { ButtonLink } from '../components/ui/Button'
import Icon from '../components/ui/Icon'
import ProgressBar from '../components/ui/ProgressBar'
import SectionHeading from '../components/ui/SectionHeading'
import StatusBadge from '../components/ui/StatusBadge'
import { ErrorState, LoadingState } from '../components/ui/States'
import { readLastLesson } from '../learning/lastLesson'
import { MODULES } from '../learning/modules'

export default function DashboardPage() {
  const { user } = useAuth()
  const [progress, setProgress] = useState(null)
  const [topics, setTopics] = useState(null)
  const [history, setHistory] = useState(null)
  const [error, setError] = useState(null)
  const [lastLesson] = useState(() => readLastLesson(user.id))

  useEffect(() => {
    let cancelled = false
    Promise.all([getMyProgress(), listTopics(), listMyChallenges({ size: 10 })])
      .then(([p, t, h]) => {
        if (cancelled) return
        setProgress(p)
        setTopics(t)
        setHistory(h)
      })
      .catch((err) => {
        if (!cancelled) setError(describeError(err).message)
      })
    return () => {
      cancelled = true
    }
  }, [])

  const resume = history?.content.find((a) => a.status === 'IN_PROGRESS')
  const lastModule = MODULES.find((m) => m.id === lastLesson?.id && m.status === 'available')
  const loading = !progress && !error

  return (
    <div className="min-h-screen bg-app text-fg">
      <AppHeader />
      <main className="mx-auto max-w-6xl space-y-8 p-4 sm:p-6">
        <section className="relative overflow-hidden rounded-xl border border-line">
          <img src={HERO_DASHBOARD.src} alt="" className="absolute inset-0 h-full w-full object-cover" />
          <div className="absolute inset-0 bg-gradient-to-r from-app via-app/90 to-app/50" />
          <div className="relative flex flex-wrap items-end justify-between gap-4 p-5 sm:p-8">
            <div className="max-w-2xl">
              <StatusBadge tone="brand">{user.role}</StatusBadge>
              <h1 className="mt-3 text-2xl font-bold text-fg sm:text-3xl">Welcome back, {user.username}</h1>
              <p className="mt-2 text-sm text-fg-2 sm:text-base">
                Pick a lesson to watch Spring Boot run, or test yourself in the Challenge Arena.
              </p>
            </div>
            <ButtonLink to="/arena" variant="secondary">
              <Icon name="trophy" className="h-4 w-4" />
              Challenge Arena
            </ButtonLink>
          </div>
          <p className="relative px-5 pb-2 text-[11px] text-muted sm:px-8">{HERO_DASHBOARD.credit}</p>
        </section>

        {error && <ErrorState>{error}</ErrorState>}

        <section className="space-y-3">
          <SectionHeading title="Continue learning" description="Pick up where you left off." />
          <div className="grid gap-4 md:grid-cols-2">
            {resume ? (
              <Card className="flex flex-col justify-between gap-3 border-accent/30 bg-accent-soft/40">
                <div>
                  <StatusBadge tone="warning">Challenge in progress</StatusBadge>
                  <p className="mt-2 font-semibold">{resume.topicName ?? 'All topics'}</p>
                  <p className="text-sm text-fg-2">
                    {resume.totalQuestions} questions, started {new Date(resume.startedAt).toLocaleString()}
                  </p>
                </div>
                <div>
                  <ButtonLink to={`/arena/attempts/${resume.id}`}>
                    Resume challenge <Icon name="arrow" className="h-4 w-4" />
                  </ButtonLink>
                </div>
              </Card>
            ) : (
              !loading && (
                <Card className="flex flex-col justify-between gap-3">
                  <div>
                    <StatusBadge>No challenge in progress</StatusBadge>
                    <p className="mt-2 text-sm text-fg-2">Start a short quiz on any topic to test what you know.</p>
                  </div>
                  <div>
                    <ButtonLink to="/arena" variant="secondary">
                      Start a challenge
                    </ButtonLink>
                  </div>
                </Card>
              )
            )}
            <Card className="flex flex-col justify-between gap-3">
              <div>
                <StatusBadge tone="brand">{lastModule ? 'Last opened lesson' : 'Suggested first lesson'}</StatusBadge>
                <p className="mt-2 font-semibold">{(lastModule ?? MODULES[0]).title}</p>
                <p className="text-sm text-fg-2">{(lastModule ?? MODULES[0]).description}</p>
              </div>
              <div>
                <ButtonLink to={(lastModule ?? MODULES[0]).path}>
                  {lastModule ? 'Continue learning' : 'Start learning'} <Icon name="arrow" className="h-4 w-4" />
                </ButtonLink>
              </div>
            </Card>
          </div>
        </section>

        <section className="space-y-3">
          <SectionHeading title="Learning modules" description="Each lesson follows Understand, Visualize, Experiment, Observe, Explain and Test." />
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {MODULES.map((m) => {
              const topic = topics?.find((t) => t.slug === m.topicSlug)
              const meta = topic ? `${topic.activeQuestionCount} practice question(s) in the Arena` : undefined
              return <ModuleCard key={m.id} module={m} meta={meta} lastOpened={lastModule?.id === m.id} />
            })}
          </div>
        </section>

        <section className="space-y-3">
          <SectionHeading
            title="Your Arena statistics"
            description="Calculated by the server from your submitted challenges."
            action={
              <ButtonLink to="/progress" variant="ghost" size="sm">
                Full progress
              </ButtonLink>
            }
          />
          {loading && <LoadingState label="Loading your statistics…" />}
          {progress && (
            <>
              <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
                <Stat label="Challenges completed" value={progress.attemptsCompleted} />
                <Stat label="Questions answered" value={progress.questionsAnswered} />
                <Stat label="Correct answers" value={progress.correctAnswers} />
                <Stat label="Accuracy" value={`${progress.accuracyPercent}%`}>
                  <ProgressBar value={progress.accuracyPercent} label="Accuracy" />
                </Stat>
              </div>
              {progress.attemptsCompleted === 0 && (
                <p className="text-sm text-fg-2">Nothing here yet. Submit a challenge and your statistics will appear.</p>
              )}
            </>
          )}
        </section>

        <Card className="text-sm">
          <h2 className="font-semibold">Your account</h2>
          <dl className="mt-2 grid grid-cols-[6rem_1fr] gap-y-1 sm:grid-cols-[6rem_1fr_6rem_1fr]">
            <dt className="text-muted">Username</dt>
            <dd className="break-all">{user.username}</dd>
            <dt className="text-muted">Role</dt>
            <dd>{user.role}</dd>
            <dt className="text-muted">Email</dt>
            <dd className="break-all">{user.email}</dd>
            <dt className="text-muted">User ID</dt>
            <dd>{user.id}</dd>
          </dl>
        </Card>
      </main>
    </div>
  )
}

function Stat({ label, value, children }) {
  return (
    <Card className="space-y-2">
      <div className="text-2xl font-bold text-fg">{value}</div>
      <div className="text-xs text-muted">{label}</div>
      {children}
    </Card>
  )
}
