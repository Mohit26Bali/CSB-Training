/**
 * Diagram layout, owned by each scenario so FlowCanvas can serve any module.
 *
 *   layout.wide.positions  {nodeId: {x, y}}                      desktop positions (use cell() below)
 *   layout.wide.handles    {edgeId: [sourceHandle, targetHandle]} desktop connection points
 *   layout.compact.order   [nodeId...]                            phone: a single vertical column
 *   layout.compact.handles optional per-edge overrides for the column
 *   layout.heights.wide    canvas height in pixels (compact height is computed from the column)
 *   layout.nodeMeta        {nodeId: {kind, stage, chips?}}        presentation: colour family, stage number(s), parts
 *   layout.lanes           [{id, label, caption?, kind, nodes}]   layer containers drawn around their nodes
 *   layout.edgeMeta        {edgeId: {label?, tone?}}              presentation: edge caption; tone 'error' | 'lookup'
 *   layout.branches        [{id, source, target, label, tone, handles, stepIds}]
 *                          extra edges that are NOT part of the data model (for example the 401/403 return path);
 *                          `stepIds` lists the steps during which the branch is the active path
 *   layout.fullWidth / layout.minWidth  give the diagram the whole row and the width below which the column is used
 *
 * Handle ids are "<side>-<s|t>". side: l, r, t, b (centre of that side) or tl, tr, bl, br (left/right third of the
 * top/bottom side, so two edges can leave the same side without sharing a line); s = source, t = target.
 */
export const NODE_W = 180
export const NODE_H = 84
export const COL_STEP = 240
export const ROW_STEP = 140
export const COMPACT_STEP = 140
export const LANE_PAD = { top: 30, side: 14, bottom: 14 }

/** Position of grid cell (col, row); `top` leaves headroom above row 0 for a return arc. */
export function cell(col, row, top = 0) {
  return { x: col * COL_STEP, y: top + row * ROW_STEP }
}

const COMPACT_DEFAULT_HANDLES = ['b-s', 't-t']

export function nodePosition(layout, compact, nodeId) {
  if (compact) return { x: 0, y: LANE_PAD.top + layout.compact.order.indexOf(nodeId) * COMPACT_STEP }
  return layout.wide.positions[nodeId]
}

export function edgeHandles(layout, compact, edgeId) {
  if (compact) return layout.compact.handles?.[edgeId] ?? COMPACT_DEFAULT_HANDLES
  return layout.wide.handles[edgeId]
}

export function canvasHeight(layout, compact) {
  if (compact) {
    const rows = layout.compact?.order?.length ?? 0
    return LANE_PAD.top + Math.max(0, rows - 1) * COMPACT_STEP + NODE_H + LANE_PAD.bottom + 24
  }
  return layout.heights.wide
}

/** The rectangle of a lane: the bounding box of its nodes plus padding (a header strip on top). */
export function laneRect(layout, compact, lane) {
  const points = lane.nodes.map((id) => nodePosition(layout, compact, id)).filter(Boolean)
  const minX = Math.min(...points.map((p) => p.x))
  const maxX = Math.max(...points.map((p) => p.x)) + NODE_W
  const minY = Math.min(...points.map((p) => p.y))
  const maxY = Math.max(...points.map((p) => p.y)) + NODE_H
  return {
    x: minX - LANE_PAD.side,
    y: minY - LANE_PAD.top,
    width: maxX - minX + 2 * LANE_PAD.side,
    height: maxY - minY + LANE_PAD.top + LANE_PAD.bottom,
  }
}

const HANDLE = /^(l|r|t|b|tl|tr|bl|br)-[st]$/
const KINDS = ['client', 'security', 'mvc', 'business', 'persistence', 'database', 'error']

const overlaps = (a, b) => a.x < b.x + b.width && b.x < a.x + a.width && a.y < b.y + b.height && b.y < a.y + a.height

/** Returns a list of problems; empty means every node is placed and every edge can be drawn in both layouts. */
export function validateLayout(scenario) {
  const problems = []
  const layout = scenario?.layout
  if (!layout) return ['scenario has no layout']
  const { nodes = [], edges = [] } = scenario
  const order = layout.compact?.order ?? []

  for (const n of nodes) {
    const p = layout.wide?.positions?.[n.id]
    if (!p || !Number.isFinite(p.x) || !Number.isFinite(p.y)) problems.push(`wide layout is missing a position for ${n.id}`)
    if (!order.includes(n.id)) problems.push(`compact layout is missing ${n.id}`)
  }
  for (const id of order) if (!nodes.some((n) => n.id === id)) problems.push(`compact layout lists unknown node ${id}`)
  if (new Set(order).size !== order.length) problems.push('compact layout lists a node twice')

  const positions = Object.values(layout.wide?.positions ?? {}).map((p) => `${p.x},${p.y}`)
  if (new Set(positions).size !== positions.length) problems.push('two nodes share a wide position')

  for (const e of edges) {
    const wide = layout.wide?.handles?.[e.id]
    if (!wide || wide.length !== 2 || !wide.every((h) => HANDLE.test(h))) problems.push(`wide layout needs two valid handles for ${e.id}`)
    const override = layout.compact?.handles?.[e.id]
    if (override) {
      if (override.length !== 2 || !override.every((h) => HANDLE.test(h))) problems.push(`compact handles for ${e.id} are invalid`)
    } else if (order.indexOf(e.target) - order.indexOf(e.source) !== 1) {
      // The default top-to-bottom handles only make sense between neighbours in the column.
      problems.push(`edge ${e.id} joins non-adjacent nodes in the compact layout and needs explicit handles`)
    }
  }
  if (!(layout.heights?.wide > 0)) problems.push('layout heights are missing')

  problems.push(...validatePresentation(scenario))
  return problems
}

/** Checks the optional presentation layer (node colours/stages, lanes, edge captions, branches) when it is present. */
function validatePresentation(scenario) {
  const problems = []
  const { layout, nodes = [], edges = [] } = scenario
  const nodeIds = new Set(nodes.map((n) => n.id))
  const edgeIds = new Set(edges.map((e) => e.id))

  if (layout.nodeMeta) {
    for (const n of nodes) {
      const meta = layout.nodeMeta[n.id]
      if (!meta) problems.push(`node ${n.id} has no presentation metadata`)
      else if (!KINDS.includes(meta.kind)) problems.push(`node ${n.id} has an unknown kind ${meta.kind}`)
    }
  }
  for (const lane of layout.lanes ?? []) {
    if (!lane.label || !KINDS.includes(lane.kind)) problems.push(`lane ${lane.id} needs a label and a known kind`)
    for (const id of lane.nodes ?? []) if (!nodeIds.has(id)) problems.push(`lane ${lane.id} lists unknown node ${id}`)
  }
  const lanes = layout.lanes ?? []
  const owners = lanes.flatMap((l) => l.nodes ?? [])
  if (new Set(owners).size !== owners.length) problems.push('a node belongs to more than one lane')
  for (const [mode, compact] of [['wide', false], ['compact', true]]) {
    const rects = lanes.map((l) => [l.id, laneRect(layout, compact, l)])
    for (let i = 0; i < rects.length; i += 1) {
      for (let j = i + 1; j < rects.length; j += 1) {
        if (overlaps(rects[i][1], rects[j][1])) problems.push(`lanes ${rects[i][0]} and ${rects[j][0]} overlap in the ${mode} layout`)
      }
    }
    // A node that is not in a lane must not sit inside another lane's rectangle.
    for (const n of nodes) {
      if (owners.includes(n.id)) continue
      const p = nodePosition(layout, compact, n.id)
      const box = { x: p.x, y: p.y, width: NODE_W, height: NODE_H }
      for (const [laneId, rect] of rects) if (overlaps(box, rect)) problems.push(`node ${n.id} sits inside lane ${laneId} in the ${mode} layout`)
    }
  }
  for (const id of Object.keys(layout.edgeMeta ?? {})) if (!edgeIds.has(id)) problems.push(`edge caption for unknown edge ${id}`)
  for (const b of layout.branches ?? []) {
    if (!nodeIds.has(b.source) || !nodeIds.has(b.target)) problems.push(`branch ${b.id} references an unknown node`)
    if (b.id !== `${b.source}->${b.target}` || edgeIds.has(b.id)) problems.push(`branch ${b.id} must be a new "source->target" id`)
    if (!b.handles || !b.handles.every((h) => HANDLE.test(h))) problems.push(`branch ${b.id} needs valid handles`)
    if (!Array.isArray(b.stepIds) || b.stepIds.length === 0) problems.push(`branch ${b.id} must name the steps that use it`)
  }
  return problems
}
