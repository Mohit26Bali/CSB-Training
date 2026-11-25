import { render, screen, within } from '@testing-library/react'
import { ResultTable } from './ResultTable'
import { ResultView } from './ResultView'
import { SqlBlock } from './SqlBlock'

describe('ResultTable', () => {
  it('shows NULL for missing values and right-aligns numbers', () => {
    render(<ResultTable columns={['id', 'name', 'phone']} rows={[[1, 'Sara', null], ['2', 'Dan', '555-0102']]} caption="people" />)
    const table = screen.getByRole('table')
    expect(within(table).getAllByRole('columnheader').map((h) => h.textContent)).toEqual(['id', 'name', 'phone'])
    expect(within(table).getByText('NULL')).toHaveClass('null')
    expect(within(table).getByText('1')).toHaveClass('num')
    expect(within(table).getByText('2')).toHaveClass('num') // numeric text such as a DECIMAL string
    expect(within(table).getByText('Sara')).not.toHaveClass('num')
  })

  it('says so when there are no rows', () => {
    render(<ResultTable columns={['a']} rows={[]} />)
    expect(screen.getByText('No rows.')).toBeInTheDocument()
  })

  it('shows markup in the data as plain text, never as HTML', () => {
    render(<ResultTable columns={['note']} rows={[['<img src=x onerror=alert(1)><b>bold</b>']]} />)
    expect(document.querySelector('img')).toBeNull()
    expect(document.querySelector('b')).toBeNull()
    expect(screen.getByText('<img src=x onerror=alert(1)><b>bold</b>')).toBeInTheDocument()
  })
})

describe('ResultView', () => {
  it('summarises rows, including truncation', () => {
    render(<ResultView result={{ kind: 'rows', columns: ['n'], rows: [[1], [2]], totalRows: 10000, truncated: true, ms: 12 }} />)
    expect(screen.getByRole('status')).toHaveTextContent('10000 rows (showing the first 2) · 12 ms')
  })

  it('uses the singular for one row', () => {
    render(<ResultView result={{ kind: 'rows', columns: ['n'], rows: [[1]], totalRows: 1, truncated: false }} />)
    expect(screen.getByRole('status')).toHaveTextContent('1 row')
    expect(screen.getByRole('status')).not.toHaveTextContent('1 rows')
  })

  it('shows affected rows for a change, and how many actually changed', () => {
    render(<ResultView result={{ kind: 'change', affectedRows: 9, changedRows: 7, insertId: null, info: '' }} />)
    expect(screen.getByRole('status')).toHaveTextContent('9 rows affected (7 actually changed)')
  })

  it('announces errors with their MySQL number', () => {
    render(<ResultView result={{ kind: 'error', error: { code: 1062, message: "Duplicate entry 'x'" } }} />)
    expect(screen.getByRole('alert')).toHaveTextContent("Error 1062 Duplicate entry 'x'")
  })

  it('explains transaction control inside the sandbox', () => {
    render(<ResultView result={{ kind: 'transaction', note: 'COMMIT is not saved in the sandbox' }} />)
    expect(screen.getByText('COMMIT is not saved in the sandbox')).toBeInTheDocument()
  })
})

describe('SqlBlock', () => {
  it('colours keywords, strings, numbers and comments', () => {
    const { container } = render(<SqlBlock sql={"SELECT COUNT(*) FROM t WHERE a = 'x' AND b > 10 -- note"} />)
    expect(container.querySelector('.tok-k')?.textContent).toBe('SELECT')
    expect(container.querySelector('.tok-f')?.textContent).toBe('COUNT')
    expect(container.querySelector('.tok-s')?.textContent).toBe("'x'")
    expect(container.querySelector('.tok-n')?.textContent).toBe('10')
    expect(container.querySelector('.tok-c')?.textContent).toBe('-- note')
  })

  it('keeps the SQL text exactly as written and never creates HTML from it', () => {
    const sql = "SELECT '<script>alert(1)</script>' AS x\nFROM `a b`"
    const { container } = render(<SqlBlock sql={sql} />)
    expect(container.querySelector('script')).toBeNull()
    expect(container.querySelector('pre')?.textContent).toBe(sql)
  })
})
