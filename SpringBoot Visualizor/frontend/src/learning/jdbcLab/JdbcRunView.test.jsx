import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { PoolStrip, StatementCounter, TransactionStateStrip } from './JdbcRunView'

const step = (sql, evidence = []) => ({ sql: sql.map((s, i) => ({ index: i + 1, sql: s })), evidence })

describe('JDBC run view parts', () => {
  it('counts statements so far', () => {
    render(<StatementCounter steps={[step(['a']), step(['b', 'c'])]} index={0} />)
    expect(screen.getByTestId('statement-counter').textContent).toMatch(/1\s*of 3 statements handed to the JDBC driver/)
  })

  it('shows the transaction state only from server evidence', () => {
    const steps = [step([], [{ label: 'Transaction state of connection A', value: 'COMMITTED: the changes are permanent' }])]
    const { rerender } = render(<TransactionStateStrip steps={steps} index={0} />)
    expect(screen.getByTestId('transaction-state').textContent).toContain('COMMITTED')
    rerender(<TransactionStateStrip steps={[step([])]} index={0} />)
    expect(screen.queryByTestId('transaction-state')).toBeNull()
  })

  it('shows pool counters read from the lab pool', () => {
    render(<PoolStrip step={step([], [{ label: 'Lab pool', value: 'active=1 idle=0 total=1 waiting=0 (maximum 1)' }])} />)
    const strip = screen.getByTestId('pool-strip')
    expect(strip.textContent).toContain('maximum')
    expect(strip.textContent).toContain('dedicated lab pool')
  })

  it('renders nothing for a step without pool metrics', () => {
    const { container } = render(<PoolStrip step={step([])} />)
    expect(container.firstChild).toBeNull()
  })
})
