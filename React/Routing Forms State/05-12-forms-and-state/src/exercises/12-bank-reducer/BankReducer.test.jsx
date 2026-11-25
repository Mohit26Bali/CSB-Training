import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import BankReducerApp from './BankReducerApp.jsx'
import { bankReducer, initialBankState } from './bankReducer.js'

const T = '2026-10-26T10:00:00.000Z'

describe('bankReducer', () => {
  it('DEPOSIT adds to the balance and records the transaction', () => {
    const next = bankReducer(initialBankState, { type: 'DEPOSIT', account: 'savings', amount: 2500, timestamp: T })
    expect(next.balances.savings).toBe(12500)
    expect(next.transactions[0]).toEqual({ id: 1, type: 'DEPOSIT', amount: 2500, account: 'savings', timestamp: T })
    expect(initialBankState.balances.savings).toBe(10000)                    // the old state was not mutated
  })

  it('WITHDRAW of exactly the balance works; one rupee more is rejected (boundary)', () => {
    const exact = bankReducer(initialBankState, { type: 'WITHDRAW', account: 'current', amount: 5000, timestamp: T })
    expect(exact.balances.current).toBe(0)

    const over = bankReducer(initialBankState, { type: 'WITHDRAW', account: 'current', amount: 5001, timestamp: T })
    expect(over.balances).toEqual(initialBankState.balances)                 // unchanged
    expect(over.transactions).toHaveLength(0)                                  // nothing recorded
    expect(over.error).toBe('Insufficient funds: Current account has ₹5,000')
  })

  it('rejects zero, negative and non-numeric amounts (invalid input)', () => {
    for (const amount of [0, -100, NaN]) {
      const next = bankReducer(initialBankState, { type: 'DEPOSIT', account: 'savings', amount, timestamp: T })
      expect(next.error).toBe('Amount must be greater than 0')
      expect(next.balances).toEqual(initialBankState.balances)
    }
  })

  it('TRANSFER moves money between the two accounts (challenge)', () => {
    const next = bankReducer(initialBankState, { type: 'TRANSFER', from: 'savings', to: 'current', amount: 3000, timestamp: T })
    expect(next.balances).toEqual({ savings: 7000, current: 8000 })
    expect(next.transactions[0]).toMatchObject({ type: 'TRANSFER', from: 'savings', to: 'current', amount: 3000 })

    expect(bankReducer(initialBankState, { type: 'TRANSFER', from: 'savings', to: 'savings', amount: 1, timestamp: T }).error)
      .toBe('Choose two different accounts')
    expect(bankReducer(initialBankState, { type: 'TRANSFER', from: 'current', to: 'savings', amount: 9000, timestamp: T }).error)
      .toMatch(/Insufficient funds/)
  })

  it('RESET returns to the initial state, and a successful action clears the last error', () => {
    let state = bankReducer(initialBankState, { type: 'WITHDRAW', account: 'current', amount: 99999, timestamp: T })
    state = bankReducer(state, { type: 'DEPOSIT', account: 'current', amount: 1, timestamp: T })
    expect(state.error).toBe('')
    expect(bankReducer(state, { type: 'RESET' })).toBe(initialBankState)
  })
})

describe('Bank Transaction Manager', () => {
  it('shows balances and history, and the rejection message in the UI', async () => {
    const user = userEvent.setup()
    render(<BankReducerApp />)
    expect(screen.getByText('No transactions yet.')).toBeInTheDocument()

    await user.type(screen.getByLabelText('Amount (₹)'), '1500')
    await user.click(screen.getByRole('button', { name: 'Submit' }))
    expect(screen.getByLabelText('Savings account balance')).toHaveTextContent('₹11,500')
    expect(screen.getByRole('heading', { name: 'Transaction history (1)' })).toBeInTheDocument()

    await user.selectOptions(screen.getByLabelText('Transaction'), 'WITHDRAW')
    await user.type(screen.getByLabelText('Amount (₹)'), '20000')
    await user.click(screen.getByRole('button', { name: 'Submit' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Insufficient funds: Savings account has ₹11,500')
    expect(screen.getByRole('heading', { name: 'Transaction history (1)' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Reset' }))
    expect(screen.getByLabelText('Savings account balance')).toHaveTextContent('₹10,000')
  })
})
