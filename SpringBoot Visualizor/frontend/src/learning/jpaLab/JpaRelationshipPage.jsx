import { useEffect, useMemo, useState } from 'react'
import { listTopics } from '../../api/arenaApi'
import { describeError } from '../../api/errors'
import AppHeader from '../../components/AppHeader'
import { Button } from '../../components/ui/Button'
import Icon from '../../components/ui/Icon'
import { ErrorState, LoadingState } from '../../components/ui/States'
import { getCodeReferences, getMeta } from '../api/labApi'
import { getJpaCatalog, getJpaMapping, runJpaExperiment } from '../api/labJpaApi'
import AssumptionsList from '../components/AssumptionsList'
import ErDiagram from '../components/ErDiagram'
import Explain5W1H from '../components/Explain5W1H'
import ModuleShell from '../components/ModuleShell'
import { goToStage } from '../schema/stageNav'
import SimulationView from '../components/SimulationView'
import TestYourself from '../components/TestYourself'
import { useMediaQuery } from '../engine/useMediaQuery'
import { describeRelationship, relationshipList } from '../schema/er'
import { FAMILIES, MODULE, SIMULATED_NOTES, SIMULATED_SCENARIO, STATIC_ASSUMPTIONS, TAKEAWAYS, TOPICS } from '../scenarios/jpaLab.sim'
import JpaRunView from './JpaRunView'

const tab = (active) =>
  `rounded-md border px-3 py-1.5 text-sm font-medium transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-accent ${
    active ? 'border-accent bg-accent text-on-accent' : 'border-line-strong bg-surface text-fg-2 hover:bg-elevated'
  }`

export default function JpaRelationshipPage() {
  const [meta, setMeta] = useState(null)
  const [metaError, setMetaError] = useState(null)
  const [mapping, setMapping] = useState(null)
  const [catalog, setCatalog] = useState(null)
  const [codeRefs, setCodeRefs] = useState(null)
  const [topics, setTopics] = useState(null)
  const [loadError, setLoadError] = useState(null)

  const [topicId, setTopicId] = useState(TOPICS[0].id)
  const [familyId, setFamilyId] = useState(FAMILIES[1].id)
  const [relationshipKey, setRelationshipKey] = useState(null)
  const [groupId, setGroupId] = useState('owning-side')
  const [run, setRun] = useState(null)
  const [running, setRunning] = useState(null)
  const [runError, setRunError] = useState(null)
  const wide = useMediaQuery('(min-width: 900px)')

  useEffect(() => {
    let cancelled = false
    getMeta().then((m) => !cancelled && setMeta(m)).catch((err) => !cancelled && setMetaError(describeError(err).message))
    Promise.all([getCodeReferences(), getJpaCatalog(), getJpaMapping(), listTopics()])
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

  const family = FAMILIES.find((f) => f.id === familyId)
  const relationships = useMemo(
    () => (mapping ? relationshipList(mapping).filter((r) => family.focus.includes(r.entity)) : []),
    [mapping, family],
  )
  const selected = useMemo(() => {
    if (!mapping || relationships.length === 0) return null
    const pick = relationships.find((r) => r.key === relationshipKey) ?? relationships[0]
    return describeRelationship(mapping, pick.entity, pick.attribute)
  }, [mapping, relationships, relationshipKey])

  async function execute(info) {
    setRunning(info.id)
    setRunError(null)
    try {
      const result = await runJpaExperiment(info.id)
      setRun({ info, result })
      goToStage('observe')
    } catch (err) {
      setRunError(describeError(err).message)
    } finally {
      setRunning(null)
    }
  }

  const topic = TOPICS.find((t) => t.id === topicId)
  const groupExperiments = catalog?.experiments.filter((e) => e.group === groupId) ?? []

  const sections = {
    understand: (
      <div className="space-y-3">
        <div role="tablist" aria-label="Topics" className="flex flex-wrap gap-2">
          {TOPICS.map((t) => (
            <button key={t.id} type="button" role="tab" aria-selected={t.id === topicId} onClick={() => setTopicId(t.id)} className={tab(t.id === topicId)}>
              {t.title}
            </button>
          ))}
        </div>
        <Explain5W1H key={topic.id} explain={topic.explain} heading={topic.title} />
      </div>
    ),

    visualize: (
      <div className="space-y-4">
        {loadError && <ErrorState>{loadError}</ErrorState>}
        {!mapping && !loadError && <LoadingState label="Reading the mapping from the server…" />}
        {mapping && (
          <>
            <p className="rounded-md border border-success-line bg-success-soft px-3 py-2 text-sm text-success">
              <strong>Read from the running system.</strong> The Java side comes from the real entity annotations and the table side from MySQL&apos;s
              information_schema ({mapping.facts.database}, Hibernate {mapping.facts.hibernateVersion}). Nothing in this diagram is typed in.
            </p>
            <div role="tablist" aria-label="Relationship families" className="flex flex-wrap gap-2">
              {FAMILIES.map((f) => (
                <button key={f.id} type="button" role="tab" aria-selected={f.id === familyId} title={f.detail}
                  onClick={() => { setFamilyId(f.id); setRelationshipKey(null) }} className={tab(f.id === familyId)}>
                  {f.label}
                </button>
              ))}
            </div>
            <p className="text-sm text-fg-2">{family.detail}. Select an annotation to see which entity, column and foreign key it involves.</p>
            <div className="flex flex-wrap gap-2" role="group" aria-label="Annotations">
              {relationships.map((r) => (
                <button key={r.key} type="button" aria-pressed={selected?.title === r.key} onClick={() => setRelationshipKey(r.key)}
                  className={`${tab(selected?.title === r.key)} font-mono text-xs`}>
                  {r.label} <span className="opacity-80">{r.annotation}</span>
                </button>
              ))}
            </div>
            <ErDiagram mapping={mapping} focus={family.focus} activeIds={new Set(selected?.nodes ?? [])} activeEdges={new Set(selected?.edges ?? [])} compact={!wide} />
            {selected && (
              <article className="rounded-lg border border-line bg-surface p-4 shadow-sm" aria-live="polite" data-testid="relationship-explanation">
                <h3 className="font-mono text-base font-semibold">{selected.title}</h3>
                <p className="mt-1 inline-block rounded bg-code px-2 py-1 font-mono text-xs text-fg">{selected.annotation}</p>
                <p className="mt-2 text-sm leading-6 text-fg-2">{selected.text}</p>
              </article>
            )}
          </>
        )}
        <div className="space-y-2">
          <h3 className="text-base font-semibold">How a change travels (SIMULATED walkthrough)</h3>
          <p className="rounded-md border border-dashed border-conceptual/60 bg-conceptual-soft px-3 py-2 text-sm text-fg">
            <strong>SIMULATED.</strong> This walkthrough explains what an EntityManager does. Nothing is executed and no step reports an
            observation; the LIVE experiments below show the real statements.
          </p>
          <SimulationView key="simulated" scenario={SIMULATED_SCENARIO} codeRefs={codeRefs} />
        </div>
      </div>
    ),

    experiment: (
      <div className="space-y-4">
        <p className="rounded-md border border-success-line bg-success-soft px-3 py-2 text-sm text-success">
          <strong>LIVE.</strong> Each experiment runs predefined code on the server: real Hibernate against the local MySQL database, on
          temporary rows in the jpa_lab_* tables that are deleted afterwards. You choose an experiment; you cannot send SQL, ids or table
          names, and no SpringLab application data is touched.
        </p>
        {!catalog && !loadError && <LoadingState label="Loading experiments…" />}
        {catalog && (
          <>
            <div role="tablist" aria-label="Experiment groups" className="flex flex-wrap gap-2">
              {catalog.groups.map((g) => (
                <button key={g.id} type="button" role="tab" aria-selected={g.id === groupId} onClick={() => setGroupId(g.id)} className={tab(g.id === groupId)}>
                  {g.title}
                </button>
              ))}
            </div>
            <p className="text-sm text-fg-2">{catalog.groups.find((g) => g.id === groupId)?.description}</p>
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
        {!run && <p className="rounded-md border border-line bg-surface px-3 py-3 text-sm text-fg-2">Run an experiment above. Its steps, the SQL Hibernate prepared, the rows read back from MySQL and the statement count appear here.</p>}
        {run && mapping && (
          <>
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h3 className="text-base font-semibold">{run.info.title}</h3>
              <span className="text-xs text-muted">Ran on the server in {run.result.durationMillis} ms · {run.result.sqlCount} Hibernate statements</span>
            </div>
            <p className="text-sm text-fg-2"><span className="font-medium">The lesson claims: </span>{run.info.claim}</p>
            <JpaRunView info={run.info} run={run.result} mapping={mapping} codeRefs={codeRefs} />
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
                      <a href={`#experiment-${id}`} className="text-accent hover:underline" onClick={() => info && setGroupId(info.group)}>{info?.title ?? id}</a>
                    </span>
                  )
                })}
                . These outcomes are also checked by automated tests against MySQL.
              </span>
            </li>
          ))}
        </ul>
        {mapping && (
          <section aria-label="Mapping reference" className="rounded-lg border border-line bg-surface p-3 shadow-sm">
            <h3 className="text-sm font-semibold text-fg">Every association the lab maps (read from the running system)</h3>
            <div className="mt-2 overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="text-muted">
                  <tr><th className="py-1 pr-3">Field</th><th className="pr-3">Annotation</th><th className="pr-3">Side</th><th className="pr-3">Fetch</th><th>Cascade</th></tr>
                </thead>
                <tbody className="font-mono">
                  {mapping.entities.flatMap((e) => e.attributes.filter((a) => a.association).map((a) => (
                    <tr key={`${e.id}.${a.name}`} className="border-t border-line-subtle">
                      <td className="py-1 pr-3">{e.id}.{a.name}</td>
                      <td className="pr-3">{a.annotation}</td>
                      <td className="pr-3">{a.owning ? 'owning' : 'inverse'}</td>
                      <td className="pr-3">{a.fetch}</td>
                      <td>{a.cascade.length ? a.cascade.join(', ') + (a.orphanRemoval ? ' + orphanRemoval' : '') : 'none'}</td>
                    </tr>
                  )))}
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
