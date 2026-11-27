/**
 * Pure logic for the entity/table diagram of the JPA Relationship Lab. The input is the backend's mapping description
 * (GET /api/lab/jpa/mapping), which is read from the real annotations and the real MySQL schema, so this file never
 * contains a table, column or annotation of its own.
 *
 * Ids used for highlighting (the same ids the backend puts in a step's `nodes`):
 *   Employee                  a Java entity            jpa_lab_employee            a table
 *   Employee.department       a field of an entity     jpa_lab_employee.department_id   a column of a table
 *   Employee->Department      an association edge      jpa_lab_employee->jpa_lab_department   a foreign key edge
 */
export const CARD_W = 280
export const HEADER_H = 44
export const ROW_H = 26
export const ROW_GAP = 72
export const COLUMN_GAP = 150

const HIDDEN = new Set(['runKey', 'run_key'])

export const visibleAttributes = (entity) => entity.attributes.filter((a) => !HIDDEN.has(a.name))
export const visibleColumns = (table) => table.columns.filter((c) => !HIDDEN.has(c.name))
export const cardHeight = (rows) => HEADER_H + rows * ROW_H + 12

const entityOf = (mapping, id) => mapping.entities.find((e) => e.id === id)
const tableOf = (mapping, id) => mapping.tables.find((t) => t.id === id)

/** The join table (if any) that links two entities, found from the owning side's @JoinTable. */
function joinTableBetween(mapping, a, b) {
  for (const [from, to] of [[a, b], [b, a]]) {
    const hit = entityOf(mapping, from)?.attributes.find((x) => x.joinTable && x.target === to)
    if (hit) return hit.joinTable.name
  }
  return null
}

/**
 * Lays the focused entities out as rows: an entity card on the left and its table on the right. A join table gets its own
 * row between the two entities it links. Returns React Flow nodes and edges (without highlighting).
 */
export function buildErGraph(mapping, focus, compact = false) {
  const rows = []
  focus.forEach((id, i) => {
    const entity = entityOf(mapping, id)
    if (!entity) return
    rows.push({ entity, table: tableOf(mapping, entity.table) })
    const next = focus[i + 1]
    const joinName = next ? joinTableBetween(mapping, id, next) : null
    if (joinName) rows.push({ entity: null, table: tableOf(mapping, joinName) })
  })

  const nodes = []
  let y = 0
  const tableX = CARD_W + COLUMN_GAP
  const rowIndex = new Map()
  rows.forEach((row, i) => {
    const entityRows = row.entity ? visibleAttributes(row.entity).length : 0
    const tableRows = row.table ? visibleColumns(row.table).length : 0
    const entityH = row.entity ? cardHeight(entityRows) : 0
    const tableH = row.table ? cardHeight(tableRows) : 0
    if (compact) {
      if (row.entity) {
        nodes.push({ id: row.entity.id, type: 'entity', position: { x: 0, y }, data: { entity: row.entity }, height: entityH })
        rowIndex.set(row.entity.id, nodes.length - 1)
        y += entityH + ROW_GAP
      }
      if (row.table) {
        nodes.push({ id: row.table.id, type: 'table', position: { x: 0, y }, data: { table: row.table }, height: tableH })
        rowIndex.set(row.table.id, nodes.length - 1)
        y += tableH + ROW_GAP
      }
    } else {
      if (row.entity) {
        nodes.push({ id: row.entity.id, type: 'entity', position: { x: 0, y }, data: { entity: row.entity }, height: entityH })
        rowIndex.set(row.entity.id, i)
      }
      if (row.table) {
        nodes.push({ id: row.table.id, type: 'table', position: { x: tableX, y }, data: { table: row.table }, height: tableH })
        rowIndex.set(row.table.id, i)
      }
      y += Math.max(entityH, tableH) + ROW_GAP
    }
  })

  const present = new Set(nodes.map((n) => n.id))
  const order = new Map(nodes.map((n, i) => [n.id, i]))
  const edges = []
  for (const e of mapping.edges) {
    if (!present.has(e.source) || !present.has(e.target)) continue
    const distance = Math.abs(order.get(e.source) - order.get(e.target))
    const down = order.get(e.source) < order.get(e.target)
    let handles
    if (e.kind === 'MAPS_TO') {
      handles = compact ? ['b-s', 't-t'] : ['r-s', 'l-t']
    } else if (compact) {
      handles = e.kind === 'ASSOCIATION' ? ['r-s', 'r-t'] : ['l-s', 'l-t']
    } else if (e.kind === 'FOREIGN_KEY') {
      handles = down ? ['b-s', 't-t'] : ['t-s', 'b-t']
    } else if (distance <= 2 && rowIndex.get(e.source) !== undefined && Math.abs(rowIndex.get(e.source) - rowIndex.get(e.target)) === 1) {
      handles = down ? ['bl-s', 'tl-t'] : ['tr-s', 'br-t']
    } else {
      handles = down ? ['r-s', 'r-t'] : ['l-s', 'l-t']
    }
    edges.push({ ...e, sourceHandle: handles[0], targetHandle: handles[1] })
  }
  return { nodes, edges, height: y - ROW_GAP }
}

/** All the ids a card owns: itself plus its fields or columns, so a highlighted field also lights its card. */
export function isCardActive(id, active) {
  if (active.has(id)) return true
  for (const a of active) if (a.startsWith(`${id}.`)) return true
  return false
}

/** Every association the lab maps, for the relationship selector. */
export function relationshipList(mapping) {
  return mapping.entities.flatMap((e) =>
    e.attributes.filter((a) => a.association).map((a) => ({
      key: `${e.id}.${a.name}`,
      entity: e.id,
      attribute: a.name,
      label: `${e.id}.${a.name}`,
      annotation: a.annotation,
      owning: a.owning,
    })),
  )
}

const plural = (word) => `${word}s`

/**
 * Plain-language explanation of one association plus everything it should light up: the entity, the field, the table,
 * the foreign key column(s) and the connecting edges. Everything comes from the mapping, nothing is hard-coded per entity.
 */
export function describeRelationship(mapping, entityId, attributeName) {
  const entity = entityOf(mapping, entityId)
  const attr = entity?.attributes.find((a) => a.name === attributeName)
  if (!attr || !attr.association) return null
  const target = entityOf(mapping, attr.target)
  const nodes = [entityId, `${entityId}.${attributeName}`, entity.table]
  const edges = [`${entityId}->${attr.target}`]
  const lines = []
  const fetchNote = `Fetch: ${attr.fetch}${attr.association === 'ManyToOne' || attr.association === 'OneToOne' ? ' (the specification default for this annotation is EAGER)' : ' (the specification default for this annotation is LAZY)'}.`

  if (attr.association === 'ManyToOne' || (attr.association === 'OneToOne' && attr.owning)) {
    const many = attr.association === 'ManyToOne'
    nodes.push(`${entity.table}.${attr.joinColumn}`, target.table)
    edges.push(`${entity.table}->${target.table}`)
    lines.push(`${many ? 'Many' : 'One'} ${entityId} ${many ? 'rows point' : 'row points'} to one ${attr.target}.`)
    lines.push(`This is the OWNING side: the foreign key column ${attr.joinColumn} lives in the table of ${entityId} (${entity.table}) and references ${target.table}.`)
    lines.push(`When Hibernate writes a ${entityId}, it puts the id of ${entityId}.${attributeName} into ${attr.joinColumn}.`)
    if (attr.association === 'OneToOne') lines.push('The foreign key is UNIQUE, which is what makes it one-to-one instead of many-to-one.')
  } else if (attr.association === 'OneToMany' || attr.association === 'OneToOne') {
    const owner = target.attributes.find((a) => a.name === attr.mappedBy)
    nodes.push(`${attr.target}.${attr.mappedBy}`, target.table)
    if (owner?.joinColumn) nodes.push(`${target.table}.${owner.joinColumn}`)
    edges.push(`${attr.target}->${entityId}`, `${target.table}->${entity.table}`)
    lines.push(`${entityId}.${attributeName} ${attr.association === 'OneToMany' ? `lists the ${plural(attr.target)} of one ${entityId}` : `points to the ${attr.target} of one ${entityId}`}.`)
    lines.push(`This is the INVERSE side: ${entity.table} has no column for it. mappedBy = "${attr.mappedBy}" says the foreign key is owned by ${attr.target}.${attr.mappedBy}${owner?.joinColumn ? ` (column ${owner.joinColumn} in ${target.table})` : ''}.`)
    lines.push('Changing this side alone never writes a foreign key.')
  } else if (attr.association === 'ManyToMany' && attr.owning) {
    const jt = attr.joinTable
    nodes.push(jt.name, `${jt.name}.${jt.joinColumn}`, `${jt.name}.${jt.inverseJoinColumn}`, target.table)
    edges.push(`${attr.target}->${entityId}`, `${jt.name}->${entity.table}`, `${jt.name}->${target.table}`)
    lines.push(`Many ${plural(entityId)} relate to many ${plural(attr.target)}, stored as rows of a third table.`)
    lines.push(`This is the OWNING side: @JoinTable declares ${jt.name}, with ${jt.joinColumn} referencing ${entityId} and ${jt.inverseJoinColumn} referencing ${attr.target}.`)
    lines.push('Adding or removing an element of this collection inserts or deletes join table rows.')
  } else if (attr.association === 'ManyToMany') {
    const owner = target.attributes.find((a) => a.name === attr.mappedBy)
    const jt = owner?.joinTable
    nodes.push(`${attr.target}.${attr.mappedBy}`)
    if (jt) nodes.push(jt.name, `${jt.name}.${jt.joinColumn}`, `${jt.name}.${jt.inverseJoinColumn}`)
    edges.push(`${attr.target}->${entityId}`)
    lines.push(`This is the INVERSE side (mappedBy = "${attr.mappedBy}"): it mirrors ${attr.target}.${attr.mappedBy}, which owns the join table${jt ? ` ${jt.name}` : ''}.`)
    lines.push('Changing this collection alone writes nothing to the join table.')
  }
  lines.push(fetchNote)
  if (attr.cascade?.length) lines.push(`Cascade: ${attr.cascade.join(', ')}. ${attr.orphanRemoval ? 'orphanRemoval is on.' : 'orphanRemoval is off.'}`)
  return { title: `${entityId}.${attributeName}`, annotation: attr.annotation, owning: attr.owning, text: lines.join(' '), lines, nodes, edges }
}
