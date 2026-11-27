import { useEffect, useMemo, useState } from 'react'
import { listTopics } from '../../api/arenaApi'
import { describeError } from '../../api/errors'
import { useAuth } from '../../auth/useAuth'
import AppHeader from '../../components/AppHeader'
import { getCatalog, getCodeReferences, getMeta, runTracedCall } from '../api/labApi'
import Disclosure from '../../components/ui/Disclosure'
import AssumptionsList from '../components/AssumptionsList'
import EvidenceSummary from '../components/EvidenceSummary'
import Explain5W1H from '../components/Explain5W1H'
import ModuleShell from '../components/ModuleShell'
import { goToStage } from '../schema/stageNav'
import SimulationView from '../components/SimulationView'
import TestYourself from '../components/TestYourself'
import WhatIfPanel from '../components/WhatIfPanel'
import { expectationForRole } from '../schema/compare'
import { validateScenario } from '../schema/scenario'
import { EDGES, EXPLAIN, LAYOUT, MODULE, NODES, SIMULATED_SCENARIO, STATIC_ASSUMPTIONS, TAKEAWAYS } from '../scenarios/restLifecycle.sim'

export default function RestLifecyclePage() {
  const { user } = useAuth()
  const [meta, setMeta] = useState(null)
  const [metaError, setMetaError] = useState(null)
  const [codeRefs, setCodeRefs] = useState(null)
  const [catalog, setCatalog] = useState(null)
  const [topics, setTopics] = useState(null)
  const [loadError, setLoadError] = useState(null)

  const [mode, setMode] = useState('SIMULATED')
  const [targetId, setTargetId] = useState('me')
  const [liveRun, setLiveRun] = useState(null)
  const [liveRunning, setLiveRunning] = useState(false)
  const [liveError, setLiveError] = useState(null)
  const [results, setResults] = useState({})

  useEffect(() => {
    let cancelled = false
    getMeta()
      .then((m) => !cancelled && setMeta(m))
      .catch((err) => !cancelled && setMetaError(describeError(err).message))
    Promise.all([getCodeReferences(), getCatalog(), listTopics()])
      .then(([refs, cat, t]) => {
        if (cancelled) return
        setCodeRefs(refs)
        setCatalog(cat)
        setTopics(t)
      })
      .catch((err) => !cancelled && setLoadError(describeError(err).message))
    return () => {
      cancelled = true
    }
  }, [])

  const liveScenario = useMemo(() => {
    if (!liveRun) return null
    const scenario = {
      id: `live-${liveRun.trace.id}`,
      title: `Live: GET ${liveRun.target.path}${liveRun.variant === 'AS_IS' ? '' : ` (${liveRun.variant.toLowerCase().replace('_', ' ')})`}`,
      mode: 'LIVE',
      nodes: NODES,
      edges: EDGES,
      layout: LAYOUT,
      steps: liveRun.trace.steps,
    }
    return { scenario, problems: validateScenario(scenario, { codeRefIds: codeRefs ? new Set(Object.keys(codeRefs)) : undefined }) }
  }, [liveRun, codeRefs])

  async function runLive() {
    const target = catalog.targets.find((t) => t.id === targetId)
    setLiveRunning(true)
    setLiveError(null)
    try {
      setLiveRun(await runTracedCall(target, 'AS_IS'))
    } catch (err) {
      setLiveError(err.response ? describeError(err).message : err.message)
    } finally {
      setLiveRunning(false)
    }
  }

  async function runExperiment(exp) {
    setResults((prev) => ({ ...prev, [exp.id]: { status: 'running' } }))
    try {
      const target = catalog.targets.find((t) => t.id === exp.target)
      // The comparison point is always a normal request: the same endpoint for token variants,
      // or the "who am I" endpoint when the experiment already uses the caller's real token.
      const baselineTarget = exp.variant === 'AS_IS' ? catalog.targets.find((t) => t.id === 'me') : target
      const baseline = await runTracedCall(baselineTarget, 'AS_IS')
      const experiment = await runTracedCall(target, exp.variant)
      setResults((prev) => ({ ...prev, [exp.id]: { status: 'done', baseline, experiment } }))
    } catch (err) {
      setResults((prev) => ({ ...prev, [exp.id]: { status: 'error', error: err.response ? describeError(err).message : err.message } }))
    }
  }

  function showInDiagram(run) {
    setLiveRun(run)
    setTargetId(run.target.id)
    setMode('LIVE')
    goToStage('visualize')
  }

  const experiments = catalog?.experiments ?? []
  const target = catalog?.targets.find((t) => t.id === targetId)

  const sections = {
    understand: (
      <div className="space-y-4">
        <Explain5W1H explain={EXPLAIN} />
        <Disclosure title="Assumptions this lesson makes" meta="versions and settings are read live">
          <AssumptionsList assumptions={STATIC_ASSUMPTIONS} meta={meta} metaError={metaError} />
        </Disclosure>
      </div>
    ),

    visualize: (
      <div className="space-y-4">
        {loadError && (
          <p role="alert" className="rounded-md border border-error-line bg-error-soft px-3 py-2 text-sm text-error">
            {loadError}
          </p>
        )}
        <div role="tablist" aria-label="Visualization mode" className="flex gap-2">
          {[
            ['SIMULATED', '○ SIMULATED walkthrough', 'A conceptual explanation. Nothing is executed.'],
            ['LIVE', '● LIVE request', 'Sends a real request and shows what the server observed.'],
          ].map(([value, label, hint]) => (
            <button
              key={value}
              role="tab"
              type="button"
              aria-selected={mode === value}
              title={hint}
              onClick={() => setMode(value)}
              className={`rounded-md border px-3 py-1.5 text-sm font-medium shadow-sm transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-accent ${
                mode === value
                  ? value === 'LIVE'
                    ? 'border-success bg-success text-on-accent'
                    : 'border-line bg-elevated text-fg'
                  : 'border-line-strong bg-surface text-fg-2 hover:bg-elevated'
              }`}
            >
              {label}
            </button>
          ))}
        </div>

        {mode === 'SIMULATED' && (
          <>
            <p className="rounded-md border border-dashed border-conceptual/60 bg-conceptual-soft px-3 py-2 text-sm text-fg">
              <strong>SIMULATED.</strong> This walkthrough explains how the framework works. No request is sent and no step
              reports an observation. The code shown is real SpringLab source; the SQL is deliberately not shown here.
            </p>
            <SimulationView key="simulated" scenario={SIMULATED_SCENARIO} codeRefs={codeRefs} />
          </>
        )}

        {mode === 'LIVE' && (
          <div className="space-y-3">
            <p className="rounded-md border border-success-line bg-success-soft px-3 py-2 text-sm text-success">
              <strong>LIVE.</strong> Pressing the button sends one real GET request, with your own token, to an existing
              SpringLab endpoint. Steps marked LIVE were observed during that request; steps marked INFERRED or SIMULATED
              are labelled as such.
            </p>
            {catalog ? (
              <div className="flex flex-wrap items-end gap-3 text-sm">
                <label>
                  Endpoint{' '}
                  <select
                    value={targetId}
                    onChange={(e) => setTargetId(e.target.value)}
                    className="rounded-md border border-line-strong px-2 py-1.5.5"
                  >
                    {catalog.targets.map((t) => (
                      <option key={t.id} value={t.id}>
                        {t.title}
                      </option>
                    ))}
                  </select>
                </label>
                <button
                  type="button"
                  onClick={runLive}
                  disabled={liveRunning}
                  className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-on-accent shadow-sm transition-colors hover:bg-accent-hover focus:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {liveRunning ? 'Sending…' : 'Send real request'}
                </button>
              </div>
            ) : (
              <p className="text-sm text-muted">Loading the list of endpoints…</p>
            )}
            {target && <p className="text-sm text-fg-2">{target.description}</p>}
            {liveError && (
              <p role="alert" className="rounded-md border border-error-line bg-error-soft px-3 py-2 text-sm text-error">
                {liveError}
              </p>
            )}
            {liveScenario && liveScenario.problems.length > 0 && (
              <p role="alert" className="rounded-md border border-error-line bg-error-soft px-3 py-2 text-sm text-error">
                The trace did not pass validation and is not shown: {liveScenario.problems.join('; ')}
              </p>
            )}
            {liveScenario && liveScenario.problems.length === 0 && (
              <SimulationView key={liveScenario.scenario.id} scenario={liveScenario.scenario} codeRefs={codeRefs} />
            )}
            {!liveRun && !liveRunning && <p className="text-sm text-muted">No request sent yet.</p>}
          </div>
        )}
      </div>
    ),

    experiment: (
      <div className="space-y-3">
        <p className="text-sm text-fg-2">
          Each experiment is predefined. LIVE ones send a modified copy of a real request; none of them can change the
          application's configuration. You are signed in as <strong>{user.role}</strong>, which affects some outcomes.
        </p>
        {catalog ? (
          <WhatIfPanel
            experiments={experiments}
            results={results}
            role={user.role}
            meta={meta}
            codeRefs={codeRefs}
            onRun={runExperiment}
            onShowTrace={showInDiagram}
          />
        ) : (
          <p className="text-sm text-muted">Loading experiments…</p>
        )}
      </div>
    ),

    observe: (
      <EvidenceSummary
        liveRun={liveRun}
        expectation={liveRun && liveRun.variant === 'AS_IS' ? expectationForRole(liveRun.target.expectation, user.role) : null}
        emptyHint="Send a real request in the Visualize stage (LIVE) or run an experiment, and the evidence will appear here."
      />
    ),

    explain: (
      <ul className="space-y-2">
        {TAKEAWAYS.map((t) => {
          const linked = experiments.filter((e) => t.evidenceExperiments.includes(e.id))
          return (
            <li key={t.text} className="rounded-lg border border-line bg-surface p-3 shadow-sm text-sm">
              {t.text}
              {linked.length > 0 && (
                <span className="mt-1 block text-xs text-muted">
                  Evidence:{' '}
                  {linked.map((e, i) => (
                    <span key={e.id}>
                      {i > 0 && ', '}
                      <a href={`#experiment-${e.id}`} className="text-accent hover:underline">
                        {e.title}
                      </a>
                    </span>
                  ))}
                  . These outcomes are also checked by automated tests against the real application.
                </span>
              )}
            </li>
          )
        })}
      </ul>
    ),

    test: <TestYourself topicSlug={MODULE.topicSlug} topics={topics} />,
  }

  return (
    <div className="min-h-screen bg-app text-fg">
      <AppHeader />
      <ModuleShell title={MODULE.title} summary={MODULE.summary} sections={sections} />
    </div>
  )
}
