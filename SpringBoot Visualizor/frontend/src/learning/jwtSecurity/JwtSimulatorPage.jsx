import { useEffect, useMemo, useState } from 'react'
import { listTopics } from '../../api/arenaApi'
import { describeError } from '../../api/errors'
import { useAuth } from '../../auth/useAuth'
import AppHeader from '../../components/AppHeader'
import { getCodeReferences, getMeta } from '../api/labApi'
import { getComponentFacts, getJwtCatalog, issueToken, runLabCall } from '../api/labJwtApi'
import Disclosure from '../../components/ui/Disclosure'
import AssumptionsList from '../components/AssumptionsList'
import ComponentGlossary from '../components/ComponentGlossary'
import EvidenceSummary from '../components/EvidenceSummary'
import Explain5W1H from '../components/Explain5W1H'
import JwtAnatomy from '../components/JwtAnatomy'
import ModuleShell from '../components/ModuleShell'
import { goToStage } from '../schema/stageNav'
import SimulationView from '../components/SimulationView'
import TestYourself from '../components/TestYourself'
import TokenWorkshop from '../components/TokenWorkshop'
import WhatIfPanel from '../components/WhatIfPanel'
import { expectationForRole } from '../schema/compare'
import { validateScenario } from '../schema/scenario'
import {
  AUTHN_VS_AUTHZ, EDGES, EXPLAIN, GLOSSARY, LAYOUT, MODULE, NODES, SIMULATED_SCENARIO, STATIC_ASSUMPTIONS, TAKEAWAYS,
} from '../scenarios/jwtSecurity.sim'

const ROLES = ['STUDENT', 'TEACHER', 'ADMIN']

export default function JwtSimulatorPage() {
  const { user } = useAuth()
  const [meta, setMeta] = useState(null)
  const [metaError, setMetaError] = useState(null)
  const [codeRefs, setCodeRefs] = useState(null)
  const [catalog, setCatalog] = useState(null)
  const [facts, setFacts] = useState(null)
  const [factsError, setFactsError] = useState(null)
  const [topics, setTopics] = useState(null)
  const [loadError, setLoadError] = useState(null)

  const [mode, setMode] = useState('SIMULATED')
  const [targetId, setTargetId] = useState('whoami')
  const [variantId, setVariantId] = useState('VALID')
  const [claimedRole, setClaimedRole] = useState('')
  const [liveRun, setLiveRun] = useState(null)
  const [liveRunning, setLiveRunning] = useState(false)
  const [liveError, setLiveError] = useState(null)
  const [results, setResults] = useState({})

  useEffect(() => {
    let cancelled = false
    getMeta()
      .then((m) => !cancelled && setMeta(m))
      .catch((err) => !cancelled && setMetaError(describeError(err).message))
    getComponentFacts()
      .then((f) => !cancelled && setFacts(f))
      .catch((err) => !cancelled && setFactsError(describeError(err).message))
    Promise.all([getCodeReferences(), getJwtCatalog(), listTopics()])
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
      title: `Live: GET ${liveRun.target.path} with ${liveRun.variantTitle}`,
      mode: 'LIVE',
      nodes: NODES,
      edges: EDGES,
      layout: LAYOUT,
      steps: liveRun.trace.steps,
    }
    return { scenario, problems: validateScenario(scenario, { codeRefIds: codeRefs ? new Set(Object.keys(codeRefs)) : undefined }) }
  }, [liveRun, codeRefs])

  /** Issues the requested lab token (unless NO_TOKEN) and sends ONE traced request with it. */
  async function execute(target, variant, claim) {
    const issued = variant === 'NO_TOKEN' ? null : await issueToken(variant, claim)
    const run = await runLabCall(target, issued ? issued.token : null)
    const variantTitle = catalog.variants.find((v) => v.id === variant)?.title ?? variant
    return { ...run, variant, variantTitle: variantTitle.toLowerCase(), issued }
  }

  async function runLive() {
    const target = catalog.targets.find((t) => t.id === targetId)
    setLiveRunning(true)
    setLiveError(null)
    try {
      setLiveRun(await execute(target, variantId, claimedRole))
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
      const baseline = await execute(catalog.targets.find((t) => t.id === 'whoami'), 'VALID', '')
      const experiment = await execute(target, exp.variant, exp.claimedRole ?? '')
      experiment.expectation = expectationForRole(exp.expectation, user.role)
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
  const byGroup = (group) => experiments.filter((e) => e.group === group)
  const target = catalog?.targets.find((t) => t.id === targetId)
  const variant = catalog?.variants.find((v) => v.id === variantId)
  const selectClass = 'mt-1 block rounded-md border border-line-strong px-2 py-1.5.5'

  /** Under every experiment result: the token that was sent, taken apart. */
  const renderDetails = (exp, result) => {
    const issued = result.experiment.issued
    if (!issued) {
      return <p className="rounded bg-elevated p-2 text-sm">No token was sent with this request.</p>
    }
    return (
      <details>
        <summary className="cursor-pointer text-sm text-accent">
          Take the token apart (claims role {issued.claimedRole}; the database says {issued.actualRole})
        </summary>
        <div className="mt-2">
          <JwtAnatomy inspection={issued.inspection} token={issued.token} />
        </div>
      </details>
    )
  }

  const sections = {
    understand: (
      <div className="space-y-4">
        <Explain5W1H explain={EXPLAIN} />
        <Disclosure title="Take a token apart" meta="issue a lab token, decode it, verify it">
          {catalog ? <TokenWorkshop variants={catalog.variants} /> : <p className="text-sm text-muted">Loading…</p>}
        </Disclosure>
        <Disclosure title="The Spring Security components and how SpringLab uses them" meta="glossary with source references">
          <ComponentGlossary glossary={GLOSSARY} facts={facts} factsError={factsError} codeRefs={codeRefs} />
        </Disclosure>
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
              <strong>SIMULATED.</strong> A conceptual walkthrough of one bearer request through Spring Security. Nothing is
              executed and no step reports an observation; the code shown is real SpringLab source.
            </p>
            <SimulationView key="simulated" scenario={SIMULATED_SCENARIO} codeRefs={codeRefs} />
          </>
        )}

        {mode === 'LIVE' && (
          <div className="space-y-3">
            <p className="rounded-md border border-success-line bg-success-soft px-3 py-2 text-sm text-success">
              <strong>LIVE.</strong> The server issues a <em>lab</em> token of the kind you choose (signed with a separate lab
              key), and one real request carrying it goes through a real Spring Security filter chain to a lab endpoint.
              Steps marked LIVE were observed; INFERRED steps say what was inferred; SIMULATED steps only explain.
            </p>
            {catalog ? (
              <div className="flex flex-wrap items-end gap-3 text-sm">
                <label>
                  Endpoint
                  <select value={targetId} onChange={(e) => setTargetId(e.target.value)} className={selectClass}>
                    {catalog.targets.map((t) => (
                      <option key={t.id} value={t.id}>
                        {t.title}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Token
                  <select value={variantId} onChange={(e) => setVariantId(e.target.value)} className={selectClass}>
                    {catalog.variants.map((v) => (
                      <option key={v.id} value={v.id}>
                        {v.title}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Role the token claims
                  <select
                    value={claimedRole}
                    onChange={(e) => setClaimedRole(e.target.value)}
                    disabled={variantId === 'NO_TOKEN'}
                    className={selectClass}
                  >
                    <option value="">My real role ({user.role})</option>
                    {ROLES.map((r) => (
                      <option key={r}>{r}</option>
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
              <p className="text-sm text-muted">Loading the simulator…</p>
            )}
            {target && (
              <p className="text-sm text-fg-2">
                {target.description} <span className="font-medium">Requires: {target.requires}.</span>
              </p>
            )}
            {variant && <p className="text-sm text-fg-2">{variant.description}</p>}
            {liveError && (
              <p role="alert" className="rounded-md border border-error-line bg-error-soft px-3 py-2 text-sm text-error">
                {liveError}
              </p>
            )}
            {liveRun?.issued && <JwtAnatomy inspection={liveRun.issued.inspection} token={liveRun.issued.token} />}
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
      <div className="space-y-5">
        <p className="text-sm text-fg-2">
          Every experiment is predefined. LIVE ones issue a lab token with one specific fault and send a real request with it;
          they cannot change any application setting and the lab key never signs anything that works elsewhere. You are signed
          in as <strong>{user.role}</strong>, which decides the outcome of the authorization experiments.
        </p>
        {catalog ? (
          <>
            {[
              ['authentication', 'Authentication: is this token genuine and current?  (401)'],
              ['authorization', 'Authorization: may this caller do this?  (403)'],
            ].map(([group, heading]) => (
              <section key={group} aria-label={heading} className="space-y-3">
                <h3 className="text-lg font-semibold">{heading}</h3>
                <WhatIfPanel
                  experiments={byGroup(group)}
                  results={results}
                  role={user.role}
                  meta={meta}
                  codeRefs={codeRefs}
                  onRun={runExperiment}
                  onShowTrace={showInDiagram}
                  renderDetails={renderDetails}
                />
              </section>
            ))}
          </>
        ) : (
          <p className="text-sm text-muted">Loading experiments…</p>
        )}
      </div>
    ),

    observe: (
      <EvidenceSummary
        liveRun={liveRun}
        expectation={liveRun?.expectation ?? null}
        emptyHint="Send a real request in the Visualize stage (LIVE) or run an experiment, and the evidence will appear here."
      />
    ),

    explain: (
      <div className="space-y-5">
        <div className="grid gap-3 md:grid-cols-2">
          {[AUTHN_VS_AUTHZ.authentication, AUTHN_VS_AUTHZ.authorization].map((side) => (
            <article key={side.title} className="rounded-lg border border-line bg-surface p-3 shadow-sm text-sm" data-testid="authn-authz">
              <h3 className="font-semibold">{side.title}</h3>
              <dl className="mt-2 grid grid-cols-[auto_1fr] gap-x-3 gap-y-1">
                <dt className="text-muted">Status</dt>
                <dd className="font-medium">{side.status}</dd>
                <dt className="text-muted">When</dt>
                <dd>{side.happens}</dd>
                <dt className="text-muted">Typical cause</dt>
                <dd>{side.cause}</dd>
                <dt className="text-muted">Answered by</dt>
                <dd>{side.component}</dd>
              </dl>
              <p className="mt-2 text-xs text-muted">
                Evidence:{' '}
                {side.experiments
                  .map((id) => experiments.find((e) => e.id === id))
                  .filter(Boolean)
                  .map((e, i) => (
                    <span key={e.id}>
                      {i > 0 && ', '}
                      <a href={`#experiment-${e.id}`} className="text-accent hover:underline">
                        {e.title}
                      </a>
                    </span>
                  ))}
              </p>
            </article>
          ))}
        </div>
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
      </div>
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
