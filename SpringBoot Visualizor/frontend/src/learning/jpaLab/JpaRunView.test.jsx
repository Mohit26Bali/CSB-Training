import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeAll, describe, expect, it } from 'vitest'
import ErDiagram from '../components/ErDiagram'
import { mapping } from '../schema/mappingFixture'
import JpaRunView, { EntityStateStrip, QueryCounter } from './JpaRunView'

// React Flow measures its container; jsdom has no layout engine, so provide the browser APIs it expects.
beforeAll(() => {
  globalThis.ResizeObserver ??= class {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
  globalThis.DOMMatrixReadOnly ??= class {
    constructor() {
      this.m22 = 1
    }
  }
  globalThis.matchMedia ??= (query) => ({ matches: true, media: query, addEventListener() {}, removeEventListener() {} })
})

afterEach(cleanup)

const sql = (index, text) => ({ index, sql: text, phase: 'EXPERIMENT', trigger: null })
const step = (id, extra = {}) => ({
  id, title: `Step ${id}`, nodes: [], edgeFrom: null, edgeTo: null, direction: 'REQUEST', provenance: 'CAPTURED',
  explanation: `Explanation of ${id} that is long enough.`, evidence: [], sql: [], codeRefId: null, ...extra,
})

const run = {
  experimentId: 'owning-inverse-only', mode: 'LIVE', ranAt: '2026-11-01T10:00:00Z', durationMillis: 12, sqlCount: 3, limitations: ['x'],
  sql: [],
  steps: [
    step('load', { nodes: ['Department', 'Department.employees', 'jpa_lab_department'], edgeFrom: 'Department', edgeTo: 'jpa_lab_department', sql: [sql(1, 'select d from jpa_lab_department'), sql(2, 'select e from jpa_lab_employee')] }),
    step('persist', { nodes: ['Employee', 'jpa_lab_employee.department_id'], edgeFrom: 'jpa_lab_employee', edgeTo: 'jpa_lab_department', sql: [sql(3, 'insert into jpa_lab_employee')] }),
    step('conclusion', { provenance: 'DERIVED', nodes: ['Employee.department'] }),
  ],
}
const info = { id: 'owning-inverse-only', title: 'Change only the inverse collection', diagram: 'er', focus: ['Department', 'Employee'] }

describe('ErDiagram (rendered)', () => {
  it('shows entities, tables and columns, and highlights exactly the active ids', () => {
    render(<ErDiagram mapping={mapping} focus={['Department', 'Employee']} activeIds={new Set(['Employee.department', 'jpa_lab_employee.department_id'])} activeEdges={new Set()} compact={false} />)
    expect(screen.getByTestId('er-attr-Employee.department').getAttribute('data-active')).toBe('true')
    expect(screen.getByTestId('er-attr-Department.employees').getAttribute('data-active')).toBe('false')
    expect(screen.getByTestId('er-column-jpa_lab_employee.department_id').getAttribute('data-active')).toBe('true')
    expect(screen.getByTestId('er-entity-Employee').getAttribute('data-active')).toBe('true')
    expect(screen.getByTestId('er-entity-Department').getAttribute('data-active')).toBe('false')
    expect(screen.getByTestId('er-table-jpa_lab_employee').textContent).toContain('FK')
    expect(screen.getByTestId('er-attr-Employee.department').textContent).toContain('OWNING')
    expect(screen.getByTestId('er-attr-Department.employees').textContent).toContain('INVERSE')
    expect(screen.queryByTestId('er-column-jpa_lab_employee.run_key')).toBeNull()
  })

  it('renders the join table for a many-to-many with both of its foreign keys', () => {
    render(<ErDiagram mapping={mapping} focus={['Student', 'Course']} activeIds={new Set()} activeEdges={new Set()} compact />)
    const join = screen.getByTestId('er-table-jpa_lab_enrollment')
    expect(join.textContent).toContain('Join table')
    expect(join.textContent.match(/FK/g)).toHaveLength(2)
  })
})

describe('JpaRunView (rendered)', () => {
  it('counts the statements prepared so far from the captured steps and follows the selected step', () => {
    render(<JpaRunView info={info} run={run} mapping={mapping} codeRefs={{}} />)
    expect(screen.getByTestId('query-counter').textContent).toContain('2 of 3 statements')
    fireEvent.click(screen.getByRole('button', { name: 'Next step' }))
    expect(screen.getByTestId('query-counter').textContent).toContain('3 of 3 statements')
    expect(screen.getByTestId('er-attr-Employee.department')).toBeTruthy()
    expect(screen.getAllByTestId('mode-badge').some((b) => b.textContent.includes('LIVE'))).toBe(true)
  })

  it('shows the captured SQL with the Hibernate caveat and the provenance of each step', () => {
    render(<JpaRunView info={info} run={run} mapping={mapping} codeRefs={{}} />)
    expect(screen.getByTestId('sql-block').textContent).toContain('select d from jpa_lab_department')
    expect(screen.getByTestId('sql-block').textContent).toContain('does not show the values bound')
    expect(screen.getByTestId('sql-block').textContent).toContain('prepared during this step')
  })
})

describe('small run-view parts', () => {
  it('the counter never shows more than was captured', () => {
    render(<QueryCounter steps={run.steps} index={2} />)
    expect(screen.getByTestId('query-counter').textContent).toContain('3 of 3')
  })

  it('names the entity state for a step and says it is inferred', () => {
    render(<EntityStateStrip stepId="detach" />)
    expect(screen.getByTestId('entity-state-strip').textContent).toContain('INFERRED')
    expect(screen.getByText('DETACHED').getAttribute('aria-current')).toBe('step')
    expect(screen.getByText('MANAGED').getAttribute('aria-current')).toBeNull()
  })
})
