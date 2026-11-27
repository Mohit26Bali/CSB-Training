import { MarkerType } from '@xyflow/react'
import { branchStates, edgeAppearance, edgeMarkers, edgeStates, nodeStates } from './diagram'
import { edgeHandles, laneRect, nodePosition } from './layout'

/**
 * Turns a scenario and the current step into React Flow's nodes and edges. Kept apart from the React components so the
 * mapping (which node is active, which edge points which way, which branch is lit) can be tested directly.
 */
function marker(state, direction, look) {
  const markers = edgeMarkers({ state, direction })
  const arrow = { type: MarkerType.ArrowClosed, color: look.arrow, width: 18, height: 18 }
  return { end: markers.end ? arrow : undefined, start: markers.start ? arrow : undefined }
}

/** Builds React Flow's nodes (lanes first, so they sit behind) for one step. Exported for tests. */
export function buildNodes({ scenario, index, compact }) {
  const { layout } = scenario
  const states = nodeStates(scenario, index)
  // A scenario may add per-step chips to nodes (for example "DOWN" on a service whose call failed). They come from captured evidence.
  const nodeOverlay = scenario.nodeOverlay?.(index) ?? {}
  const lanes = (layout.lanes ?? []).map((lane) => {
    const rect = laneRect(layout, compact, lane)
    return {
      id: `lane:${lane.id}`,
      type: 'lane',
      position: { x: rect.x, y: rect.y },
      style: { width: rect.width, height: rect.height },
      data: { label: lane.label, caption: lane.caption, kind: lane.kind },
      draggable: false,
      selectable: false,
      focusable: false,
      zIndex: 0,
    }
  })
  const nodes = scenario.nodes.map((n) => {
    const meta = layout.nodeMeta?.[n.id] ?? {}
    const extra = nodeOverlay[n.id]?.chips ?? []
    return {
      id: n.id,
      type: 'lab',
      position: nodePosition(layout, compact, n.id),
      data: {
        id: n.id, label: n.label, sublabel: n.sublabel, description: n.description, kind: meta.kind, stage: meta.stage,
        chips: [...(meta.chips ?? []), ...extra], state: states.get(n.id), glyph: meta.glyph, shape: meta.shape, simulated: meta.simulated,
      },
      draggable: false,
      selectable: false,
      zIndex: 2,
    }
  })
  return [...lanes, ...nodes]
}

/** Builds React Flow's edges for one step: the scenario's edges plus the layout's extra branches. Exported for tests. */
export function buildEdges({ scenario, index, compact, reducedMotion }) {
  const { layout } = scenario
  const direction = scenario.steps[index].direction
  const states = edgeStates(scenario, index)
  // A scenario may describe, per step, which edges are in use and by WHICH mechanism (sync call, event, compensation, ...).
  const overlay = scenario.edgeOverlay?.(index) ?? {}
  const main = scenario.edges.map((e) => {
    const o = overlay[e.id]
    if (o?.state === 'hidden') return null
    const state = o?.state ?? states.get(e.id)
    const meta = layout.edgeMeta?.[e.id] ?? {}
    const dir = state === 'active' ? direction : 'REQUEST'
    const look = edgeAppearance({ state, tone: meta.tone, direction: dir, mechanism: o?.mechanism, failed: o?.failed })
    const [sourceHandle, targetHandle] = edgeHandles(layout, compact, e.id)
    const m = marker(state, dir, look)
    return {
      id: e.id,
      source: e.source,
      target: e.target,
      sourceHandle,
      targetHandle,
      type: 'lab',
      zIndex: state === 'active' ? 4 : 1,
      data: {
        state, tone: meta.tone, direction: dir, label: o?.label ?? meta.label, offset: compact ? (meta.compactOffset ?? meta.offset) : meta.offset, reducedMotion,
        mechanism: o?.mechanism, failed: o?.failed,
      },
      markerEnd: m.end,
      markerStart: m.start,
    }
  }).filter(Boolean)
  const bStates = branchStates(layout, scenario, index)
  const branches = (layout.branches ?? []).map((b) => {
    const state = bStates.get(b.id)
    // A branch is the response leaving the application (for example the 401/403 return): it points from source to target.
    const look = edgeAppearance({ state, tone: b.tone, direction: 'REQUEST' })
    const handles = compact ? (b.compactHandles ?? b.handles) : b.handles
    return {
      id: b.id,
      source: b.source,
      target: b.target,
      sourceHandle: handles[0],
      targetHandle: handles[1],
      type: 'lab',
      zIndex: state === 'active' ? 4 : 1,
      data: { state, tone: b.tone, direction: 'REQUEST', label: b.label, offset: b.offset, reducedMotion },
      markerEnd: marker(state, 'REQUEST', look).end,
    }
  })
  return [...main, ...branches]
}
