import { useCallback, useEffect, useRef, useState } from 'react'
import { useLocation } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'
import { Button } from '../../components/ui/Button'
import { writeLastLesson } from '../lastLesson'
import { moduleForPath } from '../modules'
import { resolveStage } from '../schema/stageNav'
import { STAGES } from '../schema/stages'

/**
 * The lesson skeleton shared by every module (see schema/stages.js for the flow).
 *
 * Only ONE stage is on screen at a time. A lesson has six stages and each can be long; showing them one after another made
 * the page a long scroll that is hard to present from. The stage bar, the Previous/Next buttons and links such as
 * "#experiment-..." all move between them, and the address bar keeps the current stage (#stage-observe) so it can be shared.
 * The page component owns the data, so switching stages never loses a result.
 */
export default function ModuleShell({ title, summary, sections }) {
  const { user } = useAuth()
  const { pathname } = useLocation()
  const [current, setCurrent] = useState(() => resolveStage(window.location.hash) ?? STAGES[0].id)
  const topRef = useRef(null)
  const pendingScroll = useRef(null)

  // Remember (in this browser only) that this lesson was opened, for the dashboard's "Continue learning".
  useEffect(() => {
    const module = moduleForPath(pathname)
    if (module && user?.id != null) writeLastLesson(user.id, module.id)
  }, [pathname, user?.id])

  const go = useCallback((id, { scrollToTop = true, target = null } = {}) => {
    setCurrent(id)
    pendingScroll.current = target ?? (scrollToTop ? 'top' : null)
    window.history.replaceState(null, '', `#stage-${id}`)
  }, [])

  // Links inside a lesson ("#experiment-n-plus-one") and code that sets location.hash switch to the stage that owns them.
  useEffect(() => {
    function onHashChange() {
      const hash = window.location.hash
      const stage = resolveStage(hash)
      if (!stage) return
      const id = hash.replace(/^#/, '')
      setCurrent(stage)
      pendingScroll.current = id.startsWith('stage-') ? 'top' : id
    }
    window.addEventListener('hashchange', onHashChange)
    return () => window.removeEventListener('hashchange', onHashChange)
  }, [])

  useEffect(() => {
    const target = pendingScroll.current
    if (!target) return
    pendingScroll.current = null
    const frame = requestAnimationFrame(() => {
      const el = target === 'top' ? topRef.current : document.getElementById(target)
      el?.scrollIntoView({ block: 'start' })
    })
    return () => cancelAnimationFrame(frame)
  }, [current])

  const index = STAGES.findIndex((s) => s.id === current)
  const stage = STAGES[index]
  const previous = STAGES[index - 1]
  const next = STAGES[index + 1]

  return (
    <div className="mx-auto max-w-7xl space-y-5 p-4 pb-16 sm:p-6 sm:pb-16">
      <header>
        <h1 className="text-2xl font-bold text-fg sm:text-3xl">{title}</h1>
        <p className="mt-1 max-w-3xl text-fg-2">{summary}</p>
      </header>

      <nav ref={topRef} aria-label="Lesson stages" className="sticky top-[3.6rem] z-20 -mx-4 border-y border-line bg-sidebar/95 px-4 py-2 backdrop-blur sm:mx-0 sm:rounded-xl sm:border">
        <ol className="flex gap-1 overflow-x-auto text-sm">
          {STAGES.map((s, i) => {
            const active = s.id === current
            const done = i < index
            return (
              <li key={s.id} className="shrink-0">
                <button
                  type="button"
                  title={s.hint}
                  aria-current={active ? 'step' : undefined}
                  onClick={() => go(s.id)}
                  className={`flex items-center gap-2 rounded-md px-3 py-1.5 font-medium transition-colors ${
                    active ? 'bg-accent text-on-accent' : 'text-fg-2 hover:bg-hover hover:text-fg'
                  }`}
                >
                  <span
                    className={`flex h-5 w-5 items-center justify-center rounded-full text-xs ${
                      active ? 'bg-on-accent/15' : done ? 'bg-success-soft text-success' : 'bg-subtle text-fg-2'
                    }`}
                    aria-hidden="true"
                  >
                    {done ? '✓' : i + 1}
                  </span>
                  {s.label}
                </button>
              </li>
            )
          })}
        </ol>
      </nav>

      <section id={`stage-${stage.id}`} aria-labelledby={`stage-${stage.id}-title`} className="space-y-4">
        <div className="border-b border-line pb-3">
          <p className="text-xs font-semibold uppercase tracking-wide text-muted">
            Stage {index + 1} of {STAGES.length}
          </p>
          <h2 id={`stage-${stage.id}-title`} className="mt-0.5 text-xl font-semibold text-fg">
            {stage.label}
          </h2>
          <p className="mt-0.5 text-sm text-fg-2">{stage.hint}</p>
        </div>
        {sections[stage.id]}
      </section>

      <div className="flex items-center justify-between gap-3 border-t border-line pt-4">
        {previous ? (
          <Button variant="secondary" onClick={() => go(previous.id)}>
            <span aria-hidden="true">←</span> {previous.label}
          </Button>
        ) : (
          <span />
        )}
        {next ? (
          <Button onClick={() => go(next.id)}>
            Next: {next.label} <span aria-hidden="true">→</span>
          </Button>
        ) : (
          <span className="text-sm text-muted">End of the lesson</span>
        )}
      </div>
    </div>
  )
}
