import { useMemo } from 'react'
import ErDiagram from '../components/ErDiagram'
import FlowCaption from '../components/FlowCaption'
import SimulationView from '../components/SimulationView'
import { ENTITY_STATE_BY_STEP, liveFlowScenario } from '../scenarios/jpaLab.sim'
import { edgeKey } from '../schema/diagram'
import { statementsSoFar, totalStatements } from '../schema/jpaRun'

const STATES = ['TRANSIENT', 'MANAGED', 'DETACHED', 'REMOVED']

export function QueryCounter({ steps, index }) {
  const total = totalStatements(steps)
  const sofar = statementsSoFar(steps, index)
  return (
    <div className="flex flex-wrap items-center gap-x-6 gap-y-1 rounded-lg border border-line bg-surface px-4 py-2 shadow-sm" data-testid="query-counter">
      <div>
        <span className="text-2xl font-bold tabular-nums text-fg">{sofar}</span>
        <span className="ml-1 text-sm text-fg-2">{" "}of {total} statements prepared by Hibernate so far</span>
      </div>
      <p className="text-xs text-muted">Counted from the statements captured while each step ran. Hibernate-prepared SQL is not proof of MySQL execution.</p>
    </div>
  )
}

/** The entity lifecycle for the entity-states run. The state names are inferred from contains() and the id. */
export function EntityStateStrip({ stepId }) {
  const label = ENTITY_STATE_BY_STEP[stepId]
  const current = label ? STATES.find((s) => label.startsWith(s)) : null
  return (
    <div className="rounded-lg border border-line bg-surface px-4 py-2 shadow-sm" data-testid="entity-state-strip">
      <div className="flex flex-wrap items-center gap-2">
        {STATES.map((s, i) => (
          <span key={s} className="flex items-center gap-2">
            <span className={`rounded-full px-3 py-1 text-xs font-semibold ${s === current ? 'bg-accent text-on-accent' : 'bg-subtle text-fg-2'}`} aria-current={s === current ? 'step' : undefined}>
              {s}
            </span>
            {i < STATES.length - 1 && <span aria-hidden="true" className="text-muted">→</span>}
          </span>
        ))}
        <span className="ml-auto text-xs text-muted">INFERRED from em.contains() and the id{label ? `: ${label}` : ''}</span>
      </div>
    </div>
  )
}

/**
 * One finished LIVE run. Experiments about relationships show the entity/table diagram; experiments about the
 * EntityManager show the persistence flow. Both reuse the module framework (controls, step list, explanation panel with
 * the SQL inspector, code inspector, keyboard support, provenance badges).
 */
export default function JpaRunView({ info, run, mapping, codeRefs }) {
  const scenario = useMemo(() => {
    if (info.diagram === 'flow') return liveFlowScenario(run, info.title)
    return {
      id: `jpa-live-${run.experimentId}`,
      title: info.title,
      mode: 'LIVE',
      nodes: [],
      edges: [],
      layout: { fullWidth: true, minWidth: 900 },
      steps: run.steps,
    }
  }, [info, run])

  const renderCanvas =
    info.diagram === 'flow'
      ? undefined
      : ({ step, index, compact }) => {
          const key = edgeKey(step)
          return (
            <div className="space-y-2">
              <FlowCaption scenario={scenario} index={index} />
              <ErDiagram
                mapping={mapping}
                focus={info.focus}
                activeIds={new Set(step.nodes)}
                activeEdges={new Set(key ? [key] : [])}
                compact={compact}
              />
            </div>
          )
        }

  const renderExtra = (step, index) => (
    <div className="space-y-2">
      <QueryCounter steps={run.steps} index={index} />
      {run.experimentId === 'entity-states' && <EntityStateStrip stepId={step.id} />}
    </div>
  )

  return <SimulationView key={run.ranAt} scenario={scenario} codeRefs={codeRefs} renderCanvas={renderCanvas} renderExtra={renderExtra} />
}
