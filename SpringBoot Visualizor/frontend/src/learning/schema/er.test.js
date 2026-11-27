import { describe, expect, it } from 'vitest'
import { mapping } from './mappingFixture'
import { buildErGraph, CARD_W, describeRelationship, isCardActive, relationshipList, visibleAttributes, visibleColumns } from './er'

describe('buildErGraph', () => {
  it('puts each entity on the left and its table on the right, one row per entity', () => {
    const { nodes } = buildErGraph(mapping, ['Department', 'Employee'])
    const at = (id) => nodes.find((n) => n.id === id).position
    expect(at('Department').x).toBe(0)
    expect(at('jpa_lab_department').x).toBeGreaterThan(CARD_W)
    expect(at('Department').y).toBe(at('jpa_lab_department').y)
    expect(at('Employee').y).toBeGreaterThan(at('Department').y)
    expect(nodes.filter((n) => n.type === 'entity')).toHaveLength(2)
    expect(nodes.filter((n) => n.type === 'table')).toHaveLength(2)
  })

  it('draws the entity-to-table mapping, both Java references and the foreign key, and nothing else', () => {
    const { edges } = buildErGraph(mapping, ['Department', 'Employee'])
    expect(edges.map((e) => e.id).sort()).toEqual([
      'Department->Employee', 'Department->jpa_lab_department', 'Employee->Department', 'Employee->jpa_lab_employee', 'jpa_lab_employee->jpa_lab_department',
    ])
    expect(edges.find((e) => e.id === 'jpa_lab_employee->jpa_lab_department').kind).toBe('FOREIGN_KEY')
  })

  it('inserts the join table between the two tables it links', () => {
    const { nodes, edges } = buildErGraph(mapping, ['Student', 'Course'])
    const y = (id) => nodes.find((n) => n.id === id).position.y
    expect(y('jpa_lab_student')).toBeLessThan(y('jpa_lab_enrollment'))
    expect(y('jpa_lab_enrollment')).toBeLessThan(y('jpa_lab_course'))
    expect(nodes.find((n) => n.id === 'jpa_lab_enrollment').data.table.joinTable).toBe(true)
    expect(edges.filter((e) => e.kind === 'FOREIGN_KEY').map((e) => e.id).sort()).toEqual(['jpa_lab_enrollment->jpa_lab_course', 'jpa_lab_enrollment->jpa_lab_student'])
  })

  it('uses a single column in the phone layout and valid connection handles in both layouts', () => {
    const compact = buildErGraph(mapping, ['Department', 'Employee'], true)
    expect(new Set(compact.nodes.map((n) => n.position.x))).toEqual(new Set([0]))
    const ys = compact.nodes.map((n) => n.position.y)
    expect(new Set(ys).size).toBe(ys.length)
    const valid = /^(l|r|t|b|tl|tr|bl|br)-[st]$/
    for (const graph of [compact, buildErGraph(mapping, ['Student', 'Course'])]) {
      for (const e of graph.edges) {
        expect(e.sourceHandle).toMatch(valid)
        expect(e.targetHandle).toMatch(valid)
      }
    }
  })

  it('hides the lab-only run_key plumbing', () => {
    expect(visibleAttributes(mapping.entities[0]).map((a) => a.name)).toEqual(['id', 'name', 'employees'])
    expect(visibleColumns(mapping.tables[1]).map((c) => c.name)).toEqual(['id', 'name', 'department_id'])
  })
})

describe('highlighting', () => {
  it('a highlighted field or column also lights its card', () => {
    expect(isCardActive('Employee', new Set(['Employee.department']))).toBe(true)
    expect(isCardActive('jpa_lab_employee', new Set(['jpa_lab_employee.department_id']))).toBe(true)
    expect(isCardActive('Employee', new Set(['Department.employees']))).toBe(false)
    expect(isCardActive('Employee', new Set(['EmployeeExtra']))).toBe(false)
  })
})

describe('describeRelationship', () => {
  it('explains the owning side: who holds the foreign key and what lights up', () => {
    const d = describeRelationship(mapping, 'Employee', 'department')
    expect(d.text).toContain('OWNING side')
    expect(d.text).toContain('department_id')
    expect(d.text).toContain('jpa_lab_employee')
    expect(d.nodes).toEqual(expect.arrayContaining(['Employee', 'Employee.department', 'jpa_lab_employee', 'jpa_lab_employee.department_id', 'jpa_lab_department']))
    expect(d.edges).toEqual(expect.arrayContaining(['Employee->Department', 'jpa_lab_employee->jpa_lab_department']))
    expect(d.annotation).toBe('@ManyToOne(fetch = LAZY)')
  })

  it('explains the inverse side: mappedBy, no column of its own, and that changing it writes nothing', () => {
    const d = describeRelationship(mapping, 'Department', 'employees')
    expect(d.text).toContain('INVERSE side')
    expect(d.text).toContain('mappedBy = "department"')
    expect(d.text).toContain('never writes a foreign key')
    expect(d.nodes).toEqual(expect.arrayContaining(['Employee.department', 'jpa_lab_employee.department_id']))
  })

  it('explains a many-to-many through its join table on the owning side and says the inverse side writes nothing', () => {
    const owning = describeRelationship(mapping, 'Student', 'courses')
    expect(owning.text).toContain('jpa_lab_enrollment')
    expect(owning.nodes).toEqual(expect.arrayContaining(['jpa_lab_enrollment', 'jpa_lab_enrollment.student_id', 'jpa_lab_enrollment.course_id']))
    const inverse = describeRelationship(mapping, 'Course', 'students')
    expect(inverse.text).toContain('writes nothing to the join table')
  })

  it('returns null for a plain column and lists only associations', () => {
    expect(describeRelationship(mapping, 'Department', 'name')).toBeNull()
    expect(relationshipList(mapping).map((r) => r.key).sort()).toEqual(['Course.students', 'Department.employees', 'Employee.department', 'Student.courses'])
    expect(relationshipList(mapping).find((r) => r.key === 'Employee.department').owning).toBe(true)
  })
})
