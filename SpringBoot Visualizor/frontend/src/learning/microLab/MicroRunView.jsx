import { useMemo } from 'react'
import SimulationView from '../components/SimulationView'
import { liveScenario } from '../scenarios/microLab.sim'
import { BreakerStrip, BulkheadStrip, DiagramArea, EnvironmentBanner, HeadlineStrip, SagaLogTable, SpanTable } from './MicroParts'

/**
 * One finished LIVE run on the architecture diagram, with the strips that matter for the experiment (circuit breaker state, bulkhead permits, saga
 * log). On a phone the diagram is replaced by the vertical list of the step's calls. Every step carries its own provenance badge through the shared
 * explanation panel; the SQL and the source code of the step are in the shared inspectors.
 */
export default function MicroRunView({ info, result, runKey, codeRefs }) {
  const scenario = useMemo(() => liveScenario(result, info), [result, info])

  const renderCanvas = (args) => <DiagramArea scenario={scenario} {...args} />

  const renderExtra = (step, index) => (
    <div className="space-y-2">
      <EnvironmentBanner text={result.environment} />
      <HeadlineStrip step={step} />
      <BreakerStrip steps={scenario.steps} index={index} />
      <BulkheadStrip steps={scenario.steps} index={index} />
      <SagaLogTable step={step} />
    </div>
  )

  return (
    <div className="space-y-3">
      <SimulationView key={runKey ?? scenario.id} scenario={scenario} codeRefs={codeRefs} renderCanvas={renderCanvas} renderExtra={renderExtra} />
      <SpanTable spans={result.spans} />
    </div>
  )
}
