import { useMemo } from 'react'
import {
  BaseEdge,
  EdgeLabelRenderer,
  getSmoothStepPath,
  Handle,
  Position,
  ReactFlow,
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import { edgeAppearance, KIND_STYLES, nodeAriaLabel, showsPacket } from '../schema/diagram'
import Disclosure from '../../components/ui/Disclosure'
import { buildEdges, buildNodes } from '../schema/flowElements'
import { canvasHeight, NODE_H, NODE_W } from '../schema/layout'

/** Connection points on every side; the thirds let two edges leave one side without sharing a line. */
const HANDLE_POINTS = [
  ['l', Position.Left, undefined],
  ['r', Position.Right, undefined],
  ['t', Position.Top, undefined],
  ['b', Position.Bottom, undefined],
  ['tl', Position.Top, '30%'],
  ['tr', Position.Top, '70%'],
  ['bl', Position.Bottom, '30%'],
  ['br', Position.Bottom, '70%'],
]

/** Text and icon for each state, so the state is never conveyed by colour alone. */
const STATE_LABEL = {
  active: { text: 'Now', icon: '●' },
  completed: { text: 'Passed', icon: '✓' },
  unreached: { text: 'Not reached', icon: '–' },
}

/**
 * A small icon that says WHAT KIND of thing a node is, so a database does not look like a service and simulated infrastructure does not
 * look like a deployed one. Drawn with currentColor; decorative (the node's own text already names it).
 */
function NodeGlyph({ glyph }) {
  const common = { width: 16, height: 16, viewBox: '0 0 16 16', fill: 'none', stroke: 'currentColor', strokeWidth: 1.4, strokeLinecap: 'round', strokeLinejoin: 'round', 'aria-hidden': true }
  switch (glyph) {
    case 'database':
      return (
        <svg {...common}>
          <ellipse cx="8" cy="3.5" rx="5" ry="2" />
          <path d="M3 3.5v9c0 1.1 2.2 2 5 2s5-.9 5-2v-9" />
          <path d="M3 8c0 1.1 2.2 2 5 2s5-.9 5-2" />
        </svg>
      )
    case 'broker':
      return (
        <svg {...common}>
          <rect x="1.5" y="5" width="3" height="6" rx="0.5" />
          <rect x="6.5" y="5" width="3" height="6" rx="0.5" />
          <path d="M11.5 8h3M13 6.5 14.5 8 13 9.5" />
        </svg>
      )
    case 'client':
      return (
        <svg {...common}>
          <rect x="2" y="3" width="12" height="8" rx="1" />
          <path d="M5.5 14h5M8 11v3" />
        </svg>
      )
    case 'gateway':
      return (
        <svg {...common}>
          <path d="M3 14V7a5 5 0 0 1 10 0v7" />
          <path d="M8 14V9M6 11.5h4" />
        </svg>
      )
    case 'registry':
      return (
        <svg {...common}>
          <rect x="3" y="2" width="10" height="12" rx="1" />
          <path d="M5.5 5.5h5M5.5 8h5M5.5 10.5h3" />
        </svg>
      )
    case 'monolith':
      return (
        <svg {...common}>
          <rect x="2.5" y="2.5" width="11" height="11" rx="1" />
          <path d="M2.5 6.2h11M2.5 9.8h11" />
        </svg>
      )
    default:
      return (
        <svg {...common}>
          <rect x="2" y="3" width="12" height="10" rx="2" />
          <path d="M2 6.5h12" />
          <circle cx="4.2" cy="4.8" r="0.4" />
        </svg>
      )
  }
}

/**
 * One component of the architecture. The layer colour (left bar, stage number) says where it lives; the border and the
 * pill say where the request is: lime and "Now" for the current step, green and "Passed" for completed ones, red and
 * "Rejected" when the current step is a refusal, muted for what comes later, dashed for what this request never reaches.
 */
function LabNode({ data }) {
  const style = KIND_STYLES[data.kind] ?? KIND_STYLES.mvc
  const { state } = data
  const rejected = state === 'active' && data.kind === 'error'
  const database = data.shape === 'database'
  const broker = data.shape === 'broker'
  const shape = database ? 'rounded-[40px/16px]' : broker ? 'rounded-lg border-dashed' : 'rounded-lg'
  const frame =
    rejected
      ? 'border-error bg-elevated ring-4 ring-error/25'
      : state === 'active'
        ? 'border-accent bg-elevated ring-4 ring-accent/25'
        : state === 'completed'
          ? `${style.node} bg-surface`
          : state === 'upcoming'
            ? 'border-line bg-surface/70'
            : 'border-dashed border-line bg-app opacity-70'
  const title = state === 'upcoming' ? 'text-fg-2' : state === 'unreached' ? 'text-muted' : 'text-fg'
  const label = STATE_LABEL[state]
  const pill = rejected
    ? 'bg-error text-on-accent'
    : state === 'active'
      ? 'bg-accent text-on-accent'
      : state === 'completed'
        ? 'border border-success-line bg-surface text-success'
        : 'border border-line bg-surface text-muted'
  return (
    <div
      className={`lab-motion relative flex flex-col justify-center overflow-visible ${shape} border-2 px-3 text-center ${frame}`}
      style={{ width: NODE_W, height: NODE_H }}
      role="group"
      aria-label={nodeAriaLabel({ label: data.label, stage: data.stage, state })}
      data-state={state}
      data-testid={`node-${data.id}`}
      title={data.description}
    >
      <span className={`absolute inset-y-1 left-0 w-1 rounded-r ${style.bar}`} aria-hidden="true" />
      {data.stage != null && (
        <span
          className={`absolute -left-2 -top-2.5 flex h-6 min-w-6 items-center justify-center rounded-full px-1.5 text-xs font-bold ${style.badge}`}
          aria-hidden="true"
        >
          {data.stage}
        </span>
      )}
      {label && (
        <span className={`absolute -right-1 -top-2.5 rounded-full px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide ${pill}`} aria-hidden="true">
          {rejected ? '✕ Rejected' : `${label.icon} ${label.text}`}
        </span>
      )}
      {data.glyph && (
        <span className={`absolute bottom-1 right-1.5 ${style.accent}`} aria-hidden="true">
          <NodeGlyph glyph={data.glyph} />
        </span>
      )}
      {data.simulated && (
        <span className="absolute -bottom-2.5 left-2 rounded-full border border-dashed border-conceptual/70 bg-conceptual-soft px-1.5 py-px text-[9px] font-semibold uppercase tracking-wide text-conceptual">
          simulated
        </span>
      )}
      <div className={`text-[13px] font-semibold leading-tight [overflow-wrap:anywhere] ${title}`}>{data.label}</div>
      <div className={`line-clamp-1 text-[11px] leading-snug ${state === 'unreached' ? 'text-disabled' : 'text-fg-2'}`}>{data.sublabel}</div>
      {data.chips?.length > 0 && (
        <div className="mt-1 flex flex-wrap justify-center gap-1">
          {data.chips.map((chip) => {
            const text = typeof chip === 'string' ? chip : chip.text
            const hint = typeof chip === 'string' ? undefined : chip.title
            return (
              <span key={text} title={hint} className={`rounded border border-line bg-subtle px-1.5 py-px text-[10px] font-medium ${style.accent}`}>
                {text}
              </span>
            )
          })}
        </div>
      )}
      {HANDLE_POINTS.map(([id, position, offset]) => (
        <span key={id}>
          <Handle type="source" id={`${id}-s`} position={position} isConnectable={false} style={{ opacity: 0, pointerEvents: 'none', ...(offset ? { left: offset } : {}) }} />
          <Handle type="target" id={`${id}-t`} position={position} isConnectable={false} style={{ opacity: 0, pointerEvents: 'none', ...(offset ? { left: offset } : {}) }} />
        </span>
      ))}
    </div>
  )
}

/** A layer container (swimlane) drawn behind the nodes it groups. */
function LaneNode({ data }) {
  const style = KIND_STYLES[data.kind] ?? KIND_STYLES.mvc
  return (
    <div className={`h-full w-full rounded-xl border-[1.5px] ${style.lane}`} style={{ pointerEvents: 'none' }} aria-hidden="true">
      <div className={`px-3 pt-1.5 text-[11px] font-bold uppercase tracking-wide ${style.laneLabel}`}>
        {data.label}
        {data.caption && <span className="ml-2 font-normal normal-case tracking-normal text-fg-2">{data.caption}</span>}
      </div>
    </div>
  )
}

/** A directed connection with an arrowhead; the active one carries a travelling packet unless motion is reduced. */
function LabEdge({ id, sourceX, sourceY, targetX, targetY, sourcePosition, targetPosition, data, markerEnd, markerStart }) {
  const [path, labelX, labelY] = getSmoothStepPath({
    sourceX,
    sourceY,
    targetX,
    targetY,
    sourcePosition,
    targetPosition,
    borderRadius: 10,
    offset: data.offset ?? 24,
  })
  const look = edgeAppearance({ state: data.state, tone: data.tone, direction: data.direction, mechanism: data.mechanism, failed: data.failed })
  const response = data.direction === 'RESPONSE'
  const square = data.mechanism === 'EVENT' || data.mechanism === 'OUTBOX'
  return (
    <>
      <BaseEdge
        id={id}
        path={path}
        markerEnd={markerEnd}
        markerStart={markerStart}
        style={{ stroke: look.stroke, strokeWidth: look.width, strokeDasharray: look.dash, opacity: look.opacity }}
      />
      {showsPacket({ state: data.state, reducedMotion: data.reducedMotion }) && (
        // The packet is a square for events and outbox publishing, a circle for calls: the shape repeats what the line pattern says.
        <PacketShape square={square} fill={look.stroke}>
          {/* A response packet travels the same edge backwards (keyPoints 1 -> 0). */}
          <animateMotion
            dur="1.6s"
            repeatCount="indefinite"
            path={path}
            {...(response ? { keyPoints: '1;0', keyTimes: '0;1', calcMode: 'linear' } : {})}
          />
        </PacketShape>
      )}
      {data.label && (
        <EdgeLabelRenderer>
          <div
            className={`nodrag nopan pointer-events-none absolute rounded-full px-2 py-0.5 text-[11px] font-semibold shadow-sm ring-1 ${
              data.tone === 'error' || data.failed
                ? 'bg-surface text-error ring-error-line'
                : data.mechanism === 'COMPENSATION'
                  ? 'bg-surface text-warning ring-warning-line'
                  : 'bg-surface text-fg-2 ring-line-strong'
            } ${data.state === 'unused' ? 'opacity-70' : ''}`}
            style={{ transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px)` }}
          >
            {data.label}
          </div>
        </EdgeLabelRenderer>
      )}
    </>
  )
}

function PacketShape({ square, fill, children }) {
  return square ? (
    <rect x="-5" y="-5" width="10" height="10" rx="1.5" style={{ fill }} aria-hidden="true">
      {children}
    </rect>
  ) : (
    <circle r="6" style={{ fill }} aria-hidden="true">
      {children}
    </circle>
  )
}

// Defined once, outside the component: React Flow requires stable references for these maps.
const NODE_TYPES = { lab: LabNode, lane: LaneNode }
const EDGE_TYPES = { lab: LabEdge }

export function DiagramLegend() {
  const swatch = 'inline-block h-3 w-5 rounded-sm border-2'
  return (
    <div className="space-y-1 text-xs leading-5 text-fg-2">
      <div>Arrowheads show the direction: forward for the request, backward for a response. Numbers show the order of the stages.</div>
      <div className="flex flex-wrap items-center gap-x-3">
        <span><span className={`${swatch} border-accent bg-elevated`} /> now</span>
        <span><span className={`${swatch} border-success bg-surface`} /> passed</span>
        <span><span className={`${swatch} border-line bg-surface/70`} /> later</span>
        <span><span className={`${swatch} border-dashed border-line bg-app`} /> not reached</span>
      </div>
      <div>
        <span className="font-semibold text-error">Red dashed</span> = rejection path (401 / 403)
      </div>
    </div>
  )
}

/**
 * A module's architecture diagram. Nodes, edges, lanes and branches are derived from the scenario and the current step,
 * so one component serves every module. Layers are drawn as containers, stages are numbered, arrows have heads, and each
 * node shows whether it is current, already passed, still to come, or not reached in this request. The canvas is not
 * editable and does not capture scrolling or touch gestures, so it stays usable on phones; below the module's breakpoint
 * it switches to a single vertical column.
 */
export default function FlowCanvas({ scenario, index, compact, reducedMotion }) {
  const nodes = useMemo(() => buildNodes({ scenario, index, compact }), [scenario, index, compact])
  const edges = useMemo(() => buildEdges({ scenario, index, compact, reducedMotion }), [scenario, index, compact, reducedMotion])

  return (
    <div className="space-y-2">
    <div
      className="overflow-hidden rounded-xl border border-line bg-code"
      style={{ height: canvasHeight(scenario.layout, compact) }}
      role="img"
      aria-label="Architecture diagram of the request path. The step list and explanation describe the same path in text."
    >
      <ReactFlow
        key={compact ? 'compact' : 'wide'}
        nodes={nodes}
        edges={edges}
        nodeTypes={NODE_TYPES}
        edgeTypes={EDGE_TYPES}
        fitView
        fitViewOptions={{ padding: compact ? 0.02 : 0.03 }}
        minZoom={0.2}
        maxZoom={1.2}
        nodesDraggable={false}
        nodesConnectable={false}
        elementsSelectable={false}
        zoomOnScroll={false}
        zoomOnPinch={false}
        zoomOnDoubleClick={false}
        panOnDrag={false}
        preventScrolling={false}
      >
      </ReactFlow>
    </div>
    <Disclosure title="How to read this diagram" meta="arrows, numbers, colours">
      <DiagramLegend />
    </Disclosure>
    {scenario.layout.legend?.note && <p className="rounded-md border border-line bg-surface px-3 py-2 text-xs text-fg-2">{scenario.layout.legend.note}</p>}
    </div>
  )
}
