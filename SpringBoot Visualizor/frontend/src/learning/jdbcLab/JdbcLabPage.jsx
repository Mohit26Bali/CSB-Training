import { useEffect, useState } from 'react'
import { listTopics } from '../../api/arenaApi'
import { describeError } from '../../api/errors'
import AppHeader from '../../components/AppHeader'
import { Button } from '../../components/ui/Button'
import Icon from '../../components/ui/Icon'
import { ErrorState, LoadingState } from '../../components/ui/States'
import { getCodeReferences, getMeta } from '../api/labApi'
import { getJdbcCatalog, getJdbcMapping, runJdbcExperiment } from '../api/labJdbcApi'
import AssumptionsList from '../components/AssumptionsList'
import Explain5W1H from '../components/Explain5W1H'
import ModuleShell from '../components/ModuleShell'
import SimulationView from '../components/SimulationView'
import TestYourself from '../components/TestYourself'
import { COMPARISON, MODULE, SIMULATED_NOTES, STATIC_ASSUMPTIONS, TAKEAWAYS, TOPICS, simulatedScenario } from '../scenarios/jdbcLab.sim'
import { goToStage } from '../schema/stageNav'
import JdbcRunView from './JdbcRunView'

const tab = (active) =>
  `rounded-md border px-3 py-1.5 text-sm font-medium transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-accent ${
    active ? 'border-accent bg-accent text-on-accent' : 'border-line-strong bg-surface text-fg-2 hover:bg-elevated'
  }`

export default function JdbcLabPage() {
  const [meta, setMeta] = useState(null)
  const [metaError, setMetaError] = useState(null)
  const [mapping, setMapping] = useState(null)
  const [catalog, setCatalog] = useState(null)
  const [codeRefs, setCodeRefs] = useState(null)
  const [topics, setTopics] = useState(null)
  const [loadError, setLoadError] = useState(null)

  const [topicId, setTopicId] = useState(TOPICS[0].id)
  const [run, setRun] = useState(null)
  const [running, setRunning] = useState(null)
  const [runError, setRunError] = useState(null)

  useEffect(() => {
    let cancelled = false
    getMeta().then((m) => !cancelled && setMeta(m)).catch((err) => !cancelled && setMetaError(describeError(err).message))
    Promise.all([getCodeReferences(), getJdbcCatalog(), getJdbcMapping(), listTopics()])
      .then(([refs, cat, map, t]) => {
        if (cancelled) return
        setCodeRefs(refs)
        setCatalog(cat)
        setMapping(map)
        setTopics(t)
      })
      .catch((err) => !cancelled && setLoadError(describeError(err).message))
    return () => {
      cancelled = true
    }
  }, [])

  async function execute(info) {
    setRunning(info.id)
    setRunError(null)
    try {
      const result = await runJdbcExperiment(info.id)
      setRun({ info, result })
      goToStage('observe')
    } catch (err) {
      setRunError(describeError(err).message)
    } finally {
      setRunning(null)
    }
  }

  const topic = TOPICS.find((t) => t.id === topicId)
  const groupExperiments = catalog?.experiments.filter((e) => e.group === topic.group) ?? []
  const groupInfo = catalog?.groups.find((g) => g.id === topic.group)

  const topicTabs = (
    <div role="tablist" aria-label="Topics" className="flex flex-wrap gap-2">
      {TOPICS.map((t) => (
        <button key={t.id} type="button" role="tab" aria-selected={t.id === topicId} onClick={() => setTopicId(t.id)} className={tab(t.id === topicId)}>
          {t.title}
        </button>
      ))}
    </div>
  )

  const sections = {
    understand: (
      <div className="space-y-3">
        {topicTabs}
        <Explain5W1H key={topic.id} explain={topic.explain} heading={topic.title} />
      </div>
    ),

    visualize: (
      <div className="space-y-4">
        {topicTabs}
        <p className="rounded-md border border-dashed border-conceptual/60 bg-conceptual-soft px-3 py-2 text-sm text-fg">
          <strong>SIMULATED.</strong> This walkthrough explains the concept. Nothing is executed and no step reports an observation; the LIVE
          experiments show what really happens.
        </p>
        <SimulationView key={topic.id} scenario={simulatedScenario(topic.id)} codeRefs={codeRefs} />
        {topic.id === 'compare' && (
          <section aria-label="Comparison" className="rounded-lg border border-line bg-surface p-3 shadow-sm">
            <h3 className="text-sm font-semibold text-fg">JDBC vs Spring JDBC vs JPA / Hibernate (explanation, not a measurement)</h3>
            <div className="mt-2 overflow-x-auto">
              <table className="w-full text-left text-sm" data-testid="comparison-table">
                <thead className="text-muted">
                  <tr>{COMPARISON.columns.map((c) => <th key={c} className="py-1 pr-3">{c}</th>)}</tr>
                </thead>
                <tbody>
                  {COMPARISON.rows.map((r) => (
                    <tr key={r[0]} className="border-t border-line-subtle">
                      {r.map((c, i) => <td key={`${r[0]}-${i}`} className={`py-1 pr-3 ${i === 0 ? 'font-medium text-fg' : 'text-fg-2'}`}>{c}</td>)}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        )}
      </div>
    ),

    experiment: (
      <div className="space-y-4">
        <p className="rounded-md border border-success-line bg-success-soft px-3 py-2 text-sm text-success">
          <strong>LIVE.</strong> Each experiment runs predefined code on the server: real JDBC, Spring JDBC and Hibernate against the local MySQL
          database, using temporary rows in the jdbc_lab_* tables that are deleted afterwards. You choose an experiment; you cannot send SQL,
          table names, URLs or credentials, and no SpringLab application data is touched.
        </p>
        {topicTabs}
        {!catalog && !loadError && <LoadingState label="Loading experiments…" />}
        {loadError && <ErrorState>{loadError}</ErrorState>}
        {catalog && (
          <>
            {groupInfo && <p className="text-sm text-fg-2">{groupInfo.description}</p>}
            {runError && <ErrorState>{runError}</ErrorState>}
            <ul className="grid gap-3 md:grid-cols-2">
              {groupExperiments.map((info) => (
                <li key={info.id} id={`experiment-${info.id}`} className="flex flex-col rounded-lg border border-line bg-surface p-4 shadow-sm" data-testid={`experiment-${info.id}`}>
                  <div className="flex items-start justify-between gap-2">
                    <h3 className="text-base font-semibold">{info.title}</h3>
                    <span className="shrink-0 rounded-full border border-success bg-success-soft px-2.5 py-0.5 text-xs font-semibold text-success">● LIVE</span>
                  </div>
                  <p className="mt-2 text-sm"><span className="font-medium">What if: </span>{info.question}</p>
                  <p className="mt-1 flex-1 text-sm text-fg-2"><span className="font-medium text-fg">The lesson claims: </span>{info.claim}</p>
                  <div className="mt-3">
                    <Button onClick={() => execute(info)} disabled={running !== null}>
                      {running === info.id ? 'Running…' : run?.info.id === info.id ? 'Run again' : 'Run LIVE experiment'}
                      <Icon name="arrow" className="h-4 w-4" />
                    </Button>
                  </div>
                </li>
              ))}
            </ul>
          </>
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
        {!run && <p className="rounded-md border border-line bg-surface px-3 py-3 text-sm text-fg-2">Run an experiment above. Its steps, the SQL handed to the JDBC driver, the rows read back, the transaction or pool state and the code that ran appear here.</p>}
        {run && (
          <>
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h3 className="text-base font-semibold">{run.info.title}</h3>
              <span className="text-xs text-muted">
                Ran on the server in {run.result.durationMillis} ms · {run.result.sqlCount} statements · cleanup removed {run.result.cleanupRowsRemoved} lab rows
              </span>
            </div>
            <p className="text-sm text-fg-2"><span className="font-medium">The lesson claims: </span>{run.info.claim}</p>
            <JdbcRunView info={run.info} run={run.result} codeRefs={codeRefs} />
            <ul className="list-disc space-y-1 pl-5 text-xs text-muted">
              {run.result.limitations.map((l) => <li key={l}>{l}</li>)}
            </ul>
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
                  const info = catalog?.experiments.find((e) => e.id === id)
                  return (
                    <span key={id}>
                      {i > 0 && ', '}
                      <a
                        href={`#experiment-${id}`}
                        className="text-accent hover:underline"
                        onClick={() => info && setTopicId(TOPICS.find((x) => x.group === info.group)?.id ?? topicId)}
                      >
                        {info?.title ?? id}
                      </a>
                    </span>
                  )
                })}
                . These outcomes are also checked by automated tests against MySQL.
              </span>
            </li>
          ))}
        </ul>
        {mapping && (
          <section aria-label="Lab tables" className="rounded-lg border border-line bg-surface p-3 shadow-sm">
            <h3 className="text-sm font-semibold text-fg">The isolated lab tables (read from MySQL, driver {mapping.driver})</h3>
            <div className="mt-2 overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="text-muted">
                  <tr><th className="py-1 pr-3">Table</th><th className="pr-3">Columns</th><th>Foreign keys</th></tr>
                </thead>
                <tbody className="font-mono">
                  {mapping.tables.map((t) => (
                    <tr key={t.id} className="border-t border-line-subtle align-top">
                      <td className="py-1 pr-3">{t.id}</td>
                      <td className="pr-3">{t.columns.map((c) => c.name).join(', ')}</td>
                      <td>{t.foreignKeys.length ? t.foreignKeys.map((f) => `${f.column} → ${f.referencedTable}`).join(', ') : 'none'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        )}
        <AssumptionsList assumptions={[...STATIC_ASSUMPTIONS, ...(catalog?.limitations ?? [])]} meta={meta} metaError={metaError} />
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
