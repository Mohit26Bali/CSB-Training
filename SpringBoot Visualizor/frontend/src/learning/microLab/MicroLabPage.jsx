import { useEffect, useMemo, useRef, useState } from 'react'
import { listTopics } from '../../api/arenaApi'
import { describeError } from '../../api/errors'
import AppHeader from '../../components/AppHeader'
import { Button } from '../../components/ui/Button'
import Disclosure from '../../components/ui/Disclosure'
import Icon from '../../components/ui/Icon'
import { ErrorState, LoadingState } from '../../components/ui/States'
import { getCodeReferences, getMeta } from '../api/labApi'
import { createMicroRun, executeMicroRun, getMicroExperiments, getMicroMeta, resetMicroRun } from '../api/labMicroApi'
import AssumptionsList from '../components/AssumptionsList'
import Explain5W1H from '../components/Explain5W1H'
import ModuleShell from '../components/ModuleShell'
import SimulationView from '../components/SimulationView'
import TestYourself from '../components/TestYourself'
import { MODULE, SIMULATED_NOTES, STATIC_ASSUMPTIONS, TAKEAWAYS, TOPICS, simulatedScenario } from '../scenarios/microLab.sim'
import { changedOptions, defaultsOf, toWhatIfRequest, validateWhatIf } from '../schema/microWhatIf'
import { goToStage } from '../schema/stageNav'
import MicroRunView from './MicroRunView'
import { DiagramArea, RealityPanel } from './MicroParts'
import WhatIfForm from './WhatIfForm'

const tab = (active) =>
  `rounded-md border px-3 py-1.5 text-sm font-medium transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-accent ${
    active ? 'border-accent bg-accent text-on-accent' : 'border-line-strong bg-surface text-fg-2 hover:bg-elevated'
  }`

const LEVELS = [
  { id: 'beginner', label: 'Beginner' },
  { id: 'intermediate', label: 'Intermediate' },
  { id: 'advanced', label: 'Advanced' },
]

/** Topic tabs with the keyboard behaviour of the ARIA tabs pattern: arrows move between tabs, Home and End jump. */
function TopicTabs({ topicId, onSelect }) {
  const refs = useRef([])
  function onKeyDown(event, index) {
    const last = TOPICS.length - 1
    const next = { ArrowRight: index === last ? 0 : index + 1, ArrowLeft: index === 0 ? last : index - 1, Home: 0, End: last }[event.key]
    if (next === undefined) return
    event.preventDefault()
    onSelect(TOPICS[next].id)
    refs.current[next]?.focus()
  }
  return (
    <div role="tablist" aria-label="Topics" className="flex flex-wrap gap-2">
      {TOPICS.map((t, i) => (
        <button
          key={t.id}
          ref={(el) => { refs.current[i] = el }}
          type="button"
          role="tab"
          aria-selected={t.id === topicId}
          tabIndex={t.id === topicId ? 0 : -1}
          onClick={() => onSelect(t.id)}
          onKeyDown={(e) => onKeyDown(e, i)}
          className={tab(t.id === topicId)}
        >
          {t.title}
        </button>
      ))}
    </div>
  )
}

function LevelNotes({ levels }) {
  const [level, setLevel] = useState('beginner')
  return (
    <section aria-label="Explained at three levels" className="rounded-xl border border-line bg-surface p-4">
      <div role="radiogroup" aria-label="Explanation level" className="flex flex-wrap gap-2">
        {LEVELS.map((l) => (
          <button key={l.id} type="button" role="radio" aria-checked={level === l.id} onClick={() => setLevel(l.id)} className={tab(level === l.id)}>
            {l.label}
          </button>
        ))}
      </div>
      <p className="mt-3 text-sm leading-6 text-fg-2" data-testid="level-text">
        {levels[level]}
      </p>
    </section>
  )
}

export default function MicroLabPage() {
  const [meta, setMeta] = useState(null)
  const [metaError, setMetaError] = useState(null)
  const [micro, setMicro] = useState(null)
  const [experiments, setExperiments] = useState(null)
  const [codeRefs, setCodeRefs] = useState(null)
  const [arenaTopics, setArenaTopics] = useState(null)
  const [loadError, setLoadError] = useState(null)

  const [topicId, setTopicId] = useState(TOPICS[0].id)
  const [values, setValues] = useState({})
  const [run, setRun] = useState(null) // { info, runId, view }
  const [busy, setBusy] = useState(null)
  const [runError, setRunError] = useState(null)

  useEffect(() => {
    let cancelled = false
    getMeta().then((m) => !cancelled && setMeta(m)).catch((err) => !cancelled && setMetaError(describeError(err).message))
    Promise.all([getCodeReferences(), getMicroMeta(), getMicroExperiments(), listTopics()])
      .then(([refs, mm, ex, t]) => {
        if (cancelled) return
        setCodeRefs(refs)
        setMicro(mm)
        setExperiments(ex)
        setArenaTopics(t)
      })
      .catch((err) => !cancelled && setLoadError(describeError(err).message))
    return () => {
      cancelled = true
    }
  }, [])

  const topic = TOPICS.find((t) => t.id === topicId)
  const topicExperiments = useMemo(() => (experiments ?? []).filter((e) => e.topic === topicId), [experiments, topicId])
  const valuesOf = (info) => values[info.id] ?? defaultsOf(info.whatIfs)

  async function guarded(label, work) {
    setBusy(label)
    setRunError(null)
    try {
      return await work()
    } catch (err) {
      setRunError(describeError(err).message)
      return null
    } finally {
      setBusy(null)
    }
  }

  /** A new run: the What-If values are fixed when it is created, so changing them always means creating a run. */
  async function startRun(info) {
    const current = valuesOf(info)
    if (Object.keys(validateWhatIf(info.whatIfs, current)).length > 0) return
    const done = await guarded(info.id, async () => {
      const created = await createMicroRun(info.id, toWhatIfRequest(info.whatIfs, current))
      const executed = await executeMicroRun(created.runId)
      return { info, runId: created.runId, view: executed }
    })
    if (done) {
      setRun(done)
      goToStage('observe')
    }
  }

  /** Same run, same settings: reset it to CREATED (discarding its result), then execute it again on purpose. */
  async function runAgain() {
    const done = await guarded(run.info.id, async () => {
      await resetMicroRun(run.runId)
      return { ...run, view: await executeMicroRun(run.runId) }
    })
    if (done) setRun(done)
  }

  async function resetOnly() {
    const done = await guarded('reset', async () => ({ ...run, view: await resetMicroRun(run.runId) }))
    if (done) setRun(done)
  }

  async function executeAgain() {
    const done = await guarded(run.info.id, async () => ({ ...run, view: await executeMicroRun(run.runId) }))
    if (done) setRun(done)
  }

  const walkthrough = useMemo(() => simulatedScenario(topicId), [topicId])
  const topicTabs = <TopicTabs topicId={topicId} onSelect={setTopicId} />

  const sections = {
    understand: (
      <div className="space-y-3">
        {topicTabs}
        <Explain5W1H key={topic.id} explain={topic.explain} heading={topic.title} />
        <LevelNotes key={`level-${topic.id}`} levels={topic.levels} />
        <div className="grid gap-3 md:grid-cols-2">
          <section className="rounded-xl border border-line bg-surface p-4" aria-label="Common mistakes">
            <h3 className="text-sm font-semibold text-fg">Common mistakes</h3>
            <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-fg-2" data-testid="mistakes">
              {topic.mistakes.map((m) => <li key={m}>{m}</li>)}
            </ul>
          </section>
          <section className="rounded-xl border border-line bg-surface p-4" aria-label="What to look for">
            <h3 className="text-sm font-semibold text-fg">What to look for in the experiments</h3>
            <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-fg-2" data-testid="observations">
              {topic.observations.map((o) => <li key={o}>{o}</li>)}
            </ul>
          </section>
        </div>
        <RealityPanel />
      </div>
    ),

    visualize: (
      <div className="space-y-4">
        {topicTabs}
        <p className="rounded-md border border-dashed border-conceptual/60 bg-conceptual-soft px-3 py-2 text-sm text-fg">
          <strong>SIMULATED.</strong> This walkthrough explains the concept on the architecture diagram. Nothing is executed and no step reports an observation;
          the LIVE experiments show what really happens.
        </p>
        <SimulationView key={topic.id} scenario={walkthrough} codeRefs={codeRefs} renderCanvas={(args) => <DiagramArea scenario={walkthrough} {...args} />} />
      </div>
    ),

    experiment: (
      <div className="space-y-4">
        <p className="rounded-md border border-success-line bg-success-soft px-3 py-2 text-sm text-success">
          <strong>LIVE.</strong> Each experiment runs predefined code in this application: real service calls, and real SQL and transactions on temporary
          micro_lab_* rows that are deleted afterwards. You choose an experiment and its What-If values; nothing else is sent.
        </p>
        <RealityPanel defaultOpen />
        {topicTabs}
        {!experiments && !loadError && <LoadingState label="Loading experiments…" />}
        {loadError && <ErrorState>{loadError}</ErrorState>}
        {runError && <ErrorState>{runError}</ErrorState>}
        {experiments && (
          <ul className="grid gap-3 md:grid-cols-2" data-testid="experiment-list">
            {topicExperiments.map((info) => {
              const current = valuesOf(info)
              const problems = validateWhatIf(info.whatIfs, current)
              const changed = changedOptions(info.whatIfs, current)
              return (
                <li key={info.id} id={`experiment-${info.id}`} className="flex flex-col rounded-lg border border-line bg-surface p-4 shadow-sm" data-testid={`experiment-${info.id}`}>
                  <div className="flex items-start justify-between gap-2">
                    <h3 className="text-base font-semibold">{info.title}</h3>
                    <span className="shrink-0 rounded-full border border-success bg-success-soft px-2.5 py-0.5 text-xs font-semibold text-success">● LIVE</span>
                  </div>
                  <p className="mt-2 text-sm"><span className="font-medium">What if: </span>{info.question}</p>
                  <p className="mt-1 text-sm text-fg-2"><span className="font-medium text-fg">The lesson claims: </span>{info.claim}</p>
                  <div className="mt-3 flex-1">
                    <Disclosure title="What-If controls" meta={info.whatIfs.length ? `${info.whatIfs.length} option(s)${changed.length ? `, ${changed.length} changed` : ''}` : 'none'}>
                      <WhatIfForm
                        experimentId={info.id}
                        options={info.whatIfs}
                        values={current}
                        onChange={(next) => setValues((all) => ({ ...all, [info.id]: next }))}
                        disabled={busy !== null}
                      />
                      {info.whatIfs.length > 0 && (
                        <button type="button" className="mt-3 text-xs text-accent hover:underline" onClick={() => setValues((all) => ({ ...all, [info.id]: defaultsOf(info.whatIfs) }))}>
                          Reset to defaults
                        </button>
                      )}
                    </Disclosure>
                  </div>
                  <div className="mt-3">
                    <Button onClick={() => startRun(info)} disabled={busy !== null || Object.keys(problems).length > 0}>
                      {busy === info.id ? 'Running…' : run?.info.id === info.id ? 'Run again with these settings' : 'Run LIVE experiment'}
                      <Icon name="arrow" className="h-4 w-4" />
                    </Button>
                  </div>
                </li>
              )
            })}
          </ul>
        )}
        <div className="space-y-2">
          <h3 className="text-base font-semibold">Explained, not run (SIMULATED)</h3>
          <ul className="grid gap-3 md:grid-cols-2">
            {SIMULATED_NOTES.map((n) => (
              <li key={n.id} className="rounded-lg border border-dashed border-conceptual/50 bg-elevated p-4 text-sm">
                <div className="flex items-start justify-between gap-2">
                  <h4 className="font-semibold text-fg">{n.title}</h4>
                  <span className="shrink-0 rounded-full border border-dashed border-conceptual/70 bg-conceptual-soft px-2.5 py-0.5 text-xs font-semibold text-conceptual">○ SIMULATED</span>
                </div>
                <p className="mt-2 text-fg-2">{n.body}</p>
              </li>
            ))}
          </ul>
        </div>
      </div>
    ),

    observe: (
      <div className="space-y-3">
        {!run && <p className="rounded-md border border-line bg-surface px-3 py-3 text-sm text-fg-2">Run an experiment above. Its steps, the calls between services and their mechanism, the SQL each service ran, the transaction and trace evidence and the source code appear here.</p>}
        {run && (
          <>
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h3 className="text-base font-semibold">{run.info.title}</h3>
              <span className="text-xs text-muted" data-testid="run-status">
                Run state: <strong className="text-fg">{run.view.state}</strong>
                {run.view.result && ` · ${run.view.result.durationMillis} ms · ${run.view.result.sqlCount} SQL statements · cleanup removed ${run.view.result.cleanupRowsRemoved} lab rows`}
              </span>
            </div>
            <p className="text-sm text-fg-2"><span className="font-medium">The lesson claims: </span>{run.info.claim}</p>
            {Object.keys(run.view.whatIf ?? {}).length > 0 && (
              <p className="text-xs text-muted" data-testid="run-whatif">
                What-If values of this run:{' '}
                {Object.entries(run.view.whatIf).map(([k, v]) => `${k} = ${v}`).join(', ')}
              </p>
            )}
            <div className="flex flex-wrap gap-2" data-testid="run-controls">
              {run.view.state === 'CREATED' ? (
                <Button onClick={executeAgain} disabled={busy !== null}>Execute this run</Button>
              ) : (
                <>
                  <Button onClick={runAgain} disabled={busy !== null}>Reset and run again</Button>
                  <Button variant="secondary" onClick={resetOnly} disabled={busy !== null}>Reset only</Button>
                </>
              )}
            </div>
            {runError && <ErrorState>{runError}</ErrorState>}
            {run.view.state === 'CREATED' && <p className="text-sm text-fg-2">This run was reset to CREATED: its result was discarded. Execute it to run it again.</p>}
            {run.view.state === 'FAILED' && !run.view.result && <ErrorState>This run failed before it produced a result (it was interrupted).</ErrorState>}
            {run.view.result?.errorCode && <ErrorState>The experiment ended with {run.view.result.errorCode}. The steps recorded before the failure are shown.</ErrorState>}
            {run.view.result && <MicroRunView info={run.info} result={run.view.result} runKey={`${run.runId}-${run.view.updatedAt}`} codeRefs={codeRefs} />}
            {run.view.result && (
              <ul className="list-disc space-y-1 pl-5 text-xs text-muted">
                {run.view.result.limitations.map((l) => <li key={l}>{l}</li>)}
              </ul>
            )}
          </>
        )}
      </div>
    ),

    explain: (
      <div className="space-y-4">
        <ul className="space-y-2">
          {TAKEAWAYS.map((t) => (
            <li key={t.text} className="rounded-lg border border-line bg-surface p-3 text-sm shadow-sm">
              {t.text}
              <span className="mt-1 block text-xs text-muted">
                Evidence:{' '}
                {t.experiments.map((id, i) => {
                  const info = experiments?.find((e) => e.id === id)
                  return (
                    <span key={id}>
                      {i > 0 && ', '}
                      <a href={`#experiment-${id}`} className="text-accent hover:underline" onClick={() => info && setTopicId(info.topic)}>{info?.title ?? id}</a>
                    </span>
                  )
                })}
                . These outcomes are also checked by automated tests against MySQL.
              </span>
            </li>
          ))}
        </ul>
        {micro && (
          <section aria-label="The simulated services" className="rounded-lg border border-line bg-surface p-3 shadow-sm">
            <h3 className="text-sm font-semibold text-fg">The eight simulated services and what each owns</h3>
            <div className="mt-2 overflow-x-auto">
              <table className="w-full text-left text-xs" data-testid="services-table">
                <thead className="text-muted">
                  <tr><th scope="col" className="py-1 pr-3">Service</th><th scope="col" className="pr-3">Responsibility</th><th scope="col">Tables it owns</th></tr>
                </thead>
                <tbody>
                  {micro.services.map((s) => (
                    <tr key={s.id} className="border-t border-line-subtle align-top">
                      <td className="py-1 pr-3 font-medium text-fg">{s.title}</td>
                      <td className="pr-3 text-fg-2">{s.responsibility}</td>
                      <td className="font-mono">{s.ownedTables.length ? s.ownedTables.join(', ') : 'none'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        )}
        <AssumptionsList assumptions={[...STATIC_ASSUMPTIONS, ...(micro?.limitations ?? [])]} meta={meta} metaError={metaError} />
      </div>
    ),

    test: loadError ? (
      <ErrorState>The Arena topics could not be loaded, so the question count is unknown: {loadError}</ErrorState>
    ) : !arenaTopics ? (
      <LoadingState label="Loading the Arena topic…" />
    ) : (
      <TestYourself topicSlug={MODULE.topicSlug} topics={arenaTopics} />
    ),
  }

  return (
    <div className="min-h-screen bg-app text-fg">
      <AppHeader />
      <ModuleShell title={MODULE.title} summary={MODULE.summary} sections={sections} />
    </div>
  )
}
