import { flowCaption } from '../schema/diagram'

/**
 * One line above the diagram that says, in words, where the request is and what happens next. It makes the order
 * readable without the animation and gives screen-reader users the same information the picture shows.
 */
export default function FlowCaption({ scenario, index }) {
  const { movement, next } = flowCaption(scenario, index)
  return (
    <p className="flex flex-wrap items-center gap-x-4 gap-y-1 rounded-md bg-subtle px-3 py-1.5 text-sm text-fg-2" data-testid="flow-caption">
      {movement && <span className="font-medium text-fg">{movement}</span>}
      <span className="text-fg-2">{next ? `Next: ${next}` : 'End of the walkthrough'}</span>
    </p>
  )
}
