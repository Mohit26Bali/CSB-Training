import { useMemo } from 'react'
import { BaseEdge, EdgeLabelRenderer, getSmoothStepPath, Handle, MarkerType, Position, ReactFlow } from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import Disclosure from '../../components/ui/Disclosure'
import { buildErGraph, CARD_W, cardHeight, HEADER_H, isCardActive, ROW_H, visibleAttributes, visibleColumns } from '../schema/er'

const HANDLES = [
  ['l', Position.Left, undefined],
  ['r', Position.Right, undefined],
  ['t', Position.Top, undefined],
  ['b', Position.Bottom, undefined],
  ['tl', Position.Top, '25%'],
  ['tr', Position.Top, '75%'],
  ['bl', Position.Bottom, '25%'],
  ['br', Position.Bottom, '75%'],
]

function Handles() {
  return HANDLES.map(([id, position, offset]) => (
    <span key={id}>
      <Handle type="source" id={`${id}-s`} position={position} isConnectable={false} style={{ opacity: 0, pointerEvents: 'none', ...(offset ? { left: offset } : {}) }} />
      <Handle type="target" id={`${id}-t`} position={position} isConnectable={false} style={{ opacity: 0, pointerEvents: 'none', ...(offset ? { left: offset } : {}) }} />
    </span>
  ))
}

const SHORT = { OneToOne: '@OneToOne', OneToMany: '@OneToMany', ManyToOne: '@ManyToOne', ManyToMany: '@ManyToMany' }

const pill = 'rounded px-1 text-[10px] font-bold'
const ROW_ACTIVE = 'bg-accent-soft font-semibold text-fg shadow-[inset_3px_0_0_var(--color-accent-primary)]'

/** A Java entity: its fields, with the association annotations and which side owns the foreign key. */
function EntityNode({ data }) {
  const { entity, active } = data
  const rows = visibleAttributes(entity)
  const cardActive = isCardActive(entity.id, active)
  return (
    <div
      data-testid={`er-entity-${entity.id}`}
      data-active={cardActive}
      className={`lab-motion overflow-hidden rounded-xl border-2 border-t-4 bg-surface ${cardActive ? 'border-accent ring-4 ring-accent/25' : 'border-line border-t-layer-business'}`}
      style={{ width: CARD_W, height: cardHeight(rows.length) }}
      role="group"
      aria-label={`Java entity ${entity.id}`}
    >
      <div className="flex flex-col justify-center bg-elevated px-3" style={{ height: HEADER_H - 2 }}>
        <span className="text-[10px] font-semibold uppercase tracking-wide text-layer-business">Java entity</span>
        <span className="font-mono text-sm font-semibold text-fg">class {entity.id}</span>
      </div>
      <ul className="py-1">
        {rows.map((a) => {
          const rowActive = active.has(`${entity.id}.${a.name}`)
          return (
            <li
              key={a.name}
              data-testid={`er-attr-${entity.id}.${a.name}`}
              data-active={rowActive}
              title={a.annotation ?? (a.id ? '@Id' : undefined)}
              className={`flex items-center justify-between gap-2 px-3 font-mono text-[11px] ${rowActive ? ROW_ACTIVE : 'text-fg-2'}`}
              style={{ height: ROW_H }}
            >
              <span className="truncate">{a.name}: {a.javaType}</span>
              {a.id && <span className={`${pill} bg-warning-soft text-warning`}>@Id</span>}
              {a.association && (
                <span className="flex shrink-0 items-center gap-1">
                  <span className={`${pill} bg-subtle text-fg-2`}>{SHORT[a.association]}</span>
                  <span className={`${pill} ${a.owning ? 'bg-success-soft text-success' : 'bg-subtle text-muted'}`}>
                    {a.owning ? 'OWNING' : 'INVERSE'}
                  </span>
                </span>
              )}
            </li>
          )
        })}
      </ul>
      <Handles />
    </div>
  )
}

/** A database table (or join table): its real columns with primary key, foreign key and unique markers. */
function TableNode({ data }) {
  const { table, active } = data
  const rows = visibleColumns(table)
  const cardActive = isCardActive(table.id, active)
  const fkColumns = new Set(table.foreignKeys.map((f) => f.column))
  const palette = table.joinTable
    ? { top: 'border-t-layer-persistence', label: 'text-layer-persistence' }
    : { top: 'border-t-layer-database', label: 'text-layer-database' }
  return (
    <div
      data-testid={`er-table-${table.id}`}
      data-active={cardActive}
      className={`lab-motion overflow-hidden rounded-xl border-2 border-t-4 bg-surface ${cardActive ? 'border-accent ring-4 ring-accent/25' : `border-line ${palette.top}`}`}
      style={{ width: CARD_W, height: cardHeight(rows.length) }}
      role="group"
      aria-label={`${table.joinTable ? 'Join table' : 'Database table'} ${table.id}`}
    >
      <div className="flex flex-col justify-center bg-elevated px-3" style={{ height: HEADER_H - 2 }}>
        <span className={`text-[10px] font-semibold uppercase tracking-wide ${palette.label}`}>{table.joinTable ? 'Join table' : 'Database table'}</span>
        <span className="font-mono text-sm font-semibold text-fg">{table.id}</span>
      </div>
      <ul className="py-1">
        {rows.map((c) => {
          const rowActive = active.has(`${table.id}.${c.name}`)
          return (
            <li
              key={c.name}
              data-testid={`er-column-${table.id}.${c.name}`}
              data-active={rowActive}
              className={`flex items-center justify-between gap-2 px-3 font-mono text-[11px] ${rowActive ? ROW_ACTIVE : 'text-fg-2'}`}
              style={{ height: ROW_H }}
            >
              <span className="truncate">{c.name} <span className="text-muted">{c.type}</span></span>
              <span className="flex shrink-0 gap-1">
                {c.primaryKey && <span className={`${pill} bg-warning-soft text-warning`}>PK</span>}
                {fkColumns.has(c.name) && <span className={`${pill} bg-info-soft text-info`}>FK</span>}
                {c.unique && <span className={`${pill} bg-subtle text-fg-2`}>UNIQUE</span>}
                {!c.nullable && !c.primaryKey && <span className={`${pill} bg-subtle text-muted`}>NOT NULL</span>}
              </span>
            </li>
          )
        })}
      </ul>
      <Handles />
    </div>
  )
}

const FK_LABEL = /^(\w+) references \w+\.(\w+)$/

function ErEdge({ id, sourceX, sourceY, targetX, targetY, sourcePosition, targetPosition, data, markerEnd }) {
  const [path, labelX, labelY] = getSmoothStepPath({ sourceX, sourceY, targetX, targetY, sourcePosition, targetPosition, borderRadius: 10, offset: 28 })
  const { kind, label, active } = data
  const inverse = kind === 'ASSOCIATION' && /mappedBy/.test(label ?? '')
  const color = kind === 'FOREIGN_KEY' ? 'var(--diagram-mvc)' : kind === 'ASSOCIATION' ? 'var(--color-accent-secondary)' : 'var(--color-border-strong)'
  const text = kind === 'FOREIGN_KEY' ? (FK_LABEL.exec(label ?? '') ? label.replace(FK_LABEL, '$1 → $2') : label) : kind === 'ASSOCIATION' ? label : null
  return (
    <>
      <BaseEdge
        id={id}
        path={path}
        markerEnd={kind === 'MAPS_TO' ? undefined : markerEnd}
        style={{ stroke: active ? 'var(--color-accent-primary)' : color, strokeWidth: active ? 4 : 2, strokeDasharray: kind === 'MAPS_TO' ? '2 5' : inverse ? '8 5' : undefined, opacity: active ? 1 : 0.85 }}
      />
      {text && (
        <EdgeLabelRenderer>
          <div
            className={`nodrag nopan pointer-events-none absolute whitespace-nowrap rounded-full px-2 py-0.5 font-mono text-[10px] font-semibold shadow-sm ring-1 ${
              active ? 'bg-accent text-on-accent ring-accent' : kind === 'FOREIGN_KEY' ? 'bg-surface text-layer-mvc ring-layer-mvc/50' : 'bg-surface text-accent-2 ring-line-strong'
            }`}
            style={{ transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px)` }}
          >
            {text}
          </div>
        </EdgeLabelRenderer>
      )}
    </>
  )
}

const NODE_TYPES = { entity: EntityNode, table: TableNode }
const EDGE_TYPES = { er: ErEdge }

function Legend() {
  return (
    <div className="space-y-1 text-xs leading-5 text-fg-2">
      <div><span className="font-bold text-layer-business">■</span> Java entity · <span className="font-bold text-layer-database">■</span> table · <span className="font-bold text-layer-persistence">■</span> join table</div>
      <div><span className="font-bold text-accent-2">→</span> Java reference · <span className="font-bold text-layer-mvc">→</span> foreign key · <span className="font-bold text-accent">→</span> current step</div>
      <div>Dashed = inverse side (mappedBy)</div>
      <div>run_key is lab plumbing and is not shown</div>
    </div>
  )
}

/**
 * Entities, tables and the foreign keys between them for the focused entities. Highlighting comes from `activeIds`
 * (entity, Entity.field, table, table.column) and `activeEdges` (association or foreign key edge ids).
 */
export default function ErDiagram({ mapping, focus, activeIds, activeEdges, compact }) {
  const graph = useMemo(() => buildErGraph(mapping, focus, compact), [mapping, focus, compact])
  const nodes = useMemo(
    () => graph.nodes.map((n) => ({ ...n, data: { ...n.data, active: activeIds }, draggable: false, selectable: false })),
    [graph, activeIds],
  )
  const edges = useMemo(
    () =>
      graph.edges.map((e) => ({
        id: e.id,
        source: e.source,
        target: e.target,
        sourceHandle: e.sourceHandle,
        targetHandle: e.targetHandle,
        type: 'er',
        zIndex: activeEdges.has(e.id) ? 4 : 1,
        data: { kind: e.kind, label: e.label, active: activeEdges.has(e.id) },
        markerEnd: { type: MarkerType.ArrowClosed, color: activeEdges.has(e.id) ? 'var(--color-accent-primary)' : e.kind === 'FOREIGN_KEY' ? 'var(--diagram-mvc)' : 'var(--color-accent-secondary)', width: 18, height: 18 },
      })),
    [graph, activeEdges],
  )
  return (
    <div className="space-y-2">
    <div
      className="overflow-hidden rounded-xl border border-line bg-code"
      style={{ height: Math.max(260, graph.height + 70) }}
      role="img"
      aria-label="Diagram of the Java entities, the database tables they map to and the foreign keys between them. The explanation below describes the same relationship in text."
    >
      <ReactFlow
        key={compact ? 'compact' : 'wide'}
        nodes={nodes}
        edges={edges}
        nodeTypes={NODE_TYPES}
        edgeTypes={EDGE_TYPES}
        fitView
        fitViewOptions={{ padding: 0.04 }}
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
    <Disclosure title="How to read this diagram" meta="colours, arrows, markers">
      <Legend />
    </Disclosure>
    </div>
  )
}
