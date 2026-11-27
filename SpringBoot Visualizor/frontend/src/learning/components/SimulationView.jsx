import { useMediaQuery } from '../engine/useMediaQuery'
import { usePrefersReducedMotion } from '../engine/usePrefersReducedMotion'
import { useSimulation } from '../engine/useSimulation'
import Disclosure from '../../components/ui/Disclosure'
import CodeInspector from './CodeInspector'
import ExplanationPanel from './ExplanationPanel'
import FlowCanvas from './FlowCanvas'
import FlowCaption from './FlowCaption'
import { ProvenanceSummary } from './ModeBadge'
import SimulationControls from './SimulationControls'
import StepList from './StepList'

/**
 * One complete visualizer for a scenario in either mode. Give it a different `key` when the scenario changes: a new
 * scenario means a new simulation (the state machine never swaps steps underneath a running timer).
 */
export default function SimulationView({ scenario, codeRefs, renderCanvas, renderExtra }) {
  const reducedMotion = usePrefersReducedMotion()
  // A scenario whose diagram is very wide can ask for a higher threshold, so it falls back to the vertical layout sooner.
  const wide = useMediaQuery(`(min-width: ${scenario.layout?.minWidth ?? 768}px)`)
  const fullWidth = Boolean(scenario.layout?.fullWidth)
  const total = scenario.steps.length
  const sim = useSimulation(total, { autoplayAllowed: !reducedMotion })
  const step = scenario.steps[sim.index]


  function onKeyDown(event) {
    if (event.target.closest('select, textarea, input')) return
    if (event.key === 'ArrowRight') {
      event.preventDefault()
      sim.next()
    } else if (event.key === 'ArrowLeft') {
      event.preventDefault()
      sim.back()
    } else if (event.key === 'Home') {
      event.preventDefault()
      sim.reset()
    }
  }

  // A module with a different kind of diagram (for example the JPA entity/table diagram) supplies its own canvas.
  const canvas = renderCanvas ? (
    renderCanvas({ step, index: sim.index, compact: !wide, reducedMotion })
  ) : (
    <div className="space-y-2">
      <FlowCaption scenario={scenario} index={sim.index} />
      <FlowCanvas scenario={scenario} index={sim.index} compact={!wide} reducedMotion={reducedMotion} />
    </div>
  )
  const reference = step.codeRefId ? codeRefs?.[step.codeRefId] : null
  const stepList = (
    <Disclosure title={`All ${total} steps`} meta="jump to any step">
      <StepList steps={scenario.steps} currentIndex={sim.index} onSelect={sim.goTo} />
    </Disclosure>
  )
  const explanation = <ExplanationPanel step={step} index={sim.index} total={total} />
  const inspector = (
    <Disclosure title="Source code" meta={reference ? `${reference.file.split('/').pop()} · lines ${reference.startLine}-${reference.endLine}` : 'none for this step'}>
      <CodeInspector reference={reference} />
    </Disclosure>
  )

  return (
    <div
      className="space-y-3"
      onKeyDown={onKeyDown}
      role="region"
      aria-label={`${scenario.mode === 'LIVE' ? 'Live' : 'Simulated'} walkthrough: ${scenario.title}`}
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="text-base font-semibold">{scenario.title}</h3>
        <ProvenanceSummary steps={scenario.steps} />
      </div>
      <SimulationControls sim={sim} total={total} />
      <p className="text-xs text-muted">Keyboard: ← → step, Home resets.</p>
      {renderExtra?.(step, sim.index)}

      {fullWidth ? (
        // Wide diagrams get the whole row (readable node text); explanation and code share the row below.
        <>
          {canvas}
          <div className="grid gap-4 lg:grid-cols-5">
            <div className="min-w-0 space-y-3 lg:col-span-2">
              {explanation}
              {stepList}
            </div>
            <div className="min-w-0 lg:col-span-3">{inspector}</div>
          </div>
        </>
      ) : (
        <div className="grid gap-4 lg:grid-cols-2">
          <div className="min-w-0 space-y-3 lg:order-1">
            {canvas}
            {stepList}
          </div>
          <div className="min-w-0 space-y-3 lg:order-2">
            {explanation}
            {inspector}
          </div>
        </div>
      )}
    </div>
  )
}
