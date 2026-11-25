import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import App from './App.jsx'
import { bankReducer, initialBankState } from './state/bankReducer.js'
import { validateTransfer } from './state/validateTransfer.js'

const LOGGED_IN = { ...initialBankState, user: { username: 'priya', name: 'Priya Menon', email: 'p@x.com', phone: '9', customerId: 'C-1' } }
const renderAt = (path, initialState = initialBankState) =>
  render(<MemoryRouter initialEntries={[path]}><App initialState={initialState} /></MemoryRouter>)
const heading = () => screen.getByRole('heading', { level: 1 }).textContent

describe('validateTransfer()', () => {
  const accounts = initialBankState.accounts
  const ok = { fromAccountId: 'CA-2001', toId: 'PAY-1', amount: '5000', remark: '' }

  it('accepts a valid transfer', () => {
    expect(validateTransfer(ok, accounts)).toEqual({})
  })

  it('rejects missing, same-account, zero and over-balance transfers', () => {
    expect(Object.keys(validateTransfer({ fromAccountId: '', toId: '', amount: '', remark: '' }, accounts)))
      .toEqual(['fromAccountId', 'toId', 'amount'])
    expect(validateTransfer({ ...ok, toId: 'CA-2001' }, accounts).toId).toBe('Choose a different account to pay into')
    expect(validateTransfer({ ...ok, amount: '0' }, accounts).amount).toBe('Amount must be greater than 0')
    expect(validateTransfer({ ...ok, amount: '10.555' }, accounts).amount).toMatch(/2 decimal places/)
  })

  it('allows exactly the balance but not one rupee more; enforces the daily limit (boundaries)', () => {
    expect(validateTransfer({ ...ok, amount: '42000' }, accounts).amount).toBeUndefined()
    expect(validateTransfer({ ...ok, amount: '42001' }, accounts).amount).toBe('Insufficient balance: ₹42,000 available')
    expect(validateTransfer({ ...ok, fromAccountId: 'SB-1002', amount: '100001' }, accounts).amount).toBe('The daily limit is ₹1,00,000')
  })
})

describe('bankReducer', () => {
  it('COMPLETE_TRANSFER between own accounts debits one, credits the other and records both', () => {
    let state = bankReducer(initialBankState, {
      type: 'PREPARE_TRANSFER',
      transfer: { fromAccountId: 'SB-1002', toId: 'SB-1001', toLabel: 'Everyday Savings (SB-1001)', amount: 20000, remark: '' },
    })
    state = bankReducer(state, { type: 'COMPLETE_TRANSFER', timestamp: '2026-10-26T10:00:00.000Z' })
    expect(state.accounts.find((a) => a.id === 'SB-1002').balance).toBe(130000)
    expect(state.accounts.find((a) => a.id === 'SB-1001').balance).toBe(104500)
    expect(state.transactions.slice(0, 2).map((t) => [t.id, t.kind])).toEqual([['TXN-1005', 'CREDIT'], ['TXN-1004', 'DEBIT']])
    expect(state.pendingTransfer).toBeNull()
    expect(initialBankState.accounts[1].balance).toBe(150000)             // not mutated
  })

  it('refuses to complete a transfer that would overdraw (checked again in the reducer)', () => {
    const state = { ...initialBankState, pendingTransfer: { fromAccountId: 'CA-2001', toId: 'PAY-1', toLabel: 'x', amount: 999999 } }
    expect(bankReducer(state, { type: 'COMPLETE_TRANSFER', timestamp: 'now' })).toBe(state)
  })
})

describe('Banking portal', () => {
  it.each(['/dashboard', '/accounts', '/transfer', '/transactions/TXN-1001', '/profile'])(
    '%s is protected: redirects to Login', (path) => {
      renderAt(path)
      expect(heading()).toBe('Customer login')
    })

  it('logging in returns to the requested page; Logout locks the portal again', async () => {
    const user = userEvent.setup()
    renderAt('/accounts')
    await user.type(screen.getByLabelText('Username'), 'priya')
    await user.type(screen.getByLabelText('Password'), 'bank@123')
    await user.click(screen.getByRole('button', { name: 'Log in' }))
    expect(heading()).toBe('Accounts')

    await user.click(screen.getByRole('button', { name: 'Logout' }))
    expect(heading()).toBe('Customer login')
  })

  it('rejects wrong credentials', async () => {
    const user = userEvent.setup()
    renderAt('/login')
    await user.type(screen.getByLabelText('Username'), 'priya')
    await user.type(screen.getByLabelText('Password'), 'nope')
    await user.click(screen.getByRole('button', { name: 'Log in' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Invalid username or password.')
  })

  it('transfer → confirmation page → confirm → transaction details, with balances updated', async () => {
    const user = userEvent.setup()
    renderAt('/transfer', LOGGED_IN)
    await user.click(screen.getByRole('button', { name: 'Continue' }))
    expect(screen.getByText('Select the account to pay from')).toBeInTheDocument()       // invalid: stays

    await user.selectOptions(screen.getByLabelText('From account'), 'CA-2001')
    await user.selectOptions(screen.getByLabelText('To'), 'PAY-2')
    await user.type(screen.getByLabelText('Amount (₹)'), '7500')
    await user.type(screen.getByLabelText('Remark (optional, max 50)'), 'Wiring work')
    await user.click(screen.getByRole('button', { name: 'Continue' }))

    expect(heading()).toBe('Confirm transfer')
    expect(screen.getByText('₹34,500.00')).toBeInTheDocument()                             // balance after

    await user.click(screen.getByRole('button', { name: 'Edit' }))                           // back, values kept
    expect(screen.getByLabelText('Amount (₹)')).toHaveValue(7500)
    await user.click(screen.getByRole('button', { name: 'Continue' }))
    await user.click(screen.getByRole('button', { name: 'Confirm transfer' }))

    expect(heading()).toBe('Transaction TXN-1004')
    expect(screen.getByRole('status')).toHaveTextContent('Transfer completed successfully.')
    expect(screen.getByText(/Transfer to Shah Electricals/)).toBeInTheDocument()

    await user.click(screen.getByRole('link', { name: 'CA-2001' }))
    expect(screen.getByLabelText('Account balance')).toHaveTextContent('₹34,500.00')
  })

  it('cancelling on the confirmation page moves no money', async () => {
    const user = userEvent.setup()
    renderAt('/transfer', LOGGED_IN)
    await user.selectOptions(screen.getByLabelText('From account'), 'SB-1001')
    await user.selectOptions(screen.getByLabelText('To'), 'SB-1002')
    await user.type(screen.getByLabelText('Amount (₹)'), '1000')
    await user.click(screen.getByRole('button', { name: 'Continue' }))
    await user.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(heading()).toBe('Welcome, Priya Menon')
    expect(screen.getByLabelText('Total balance')).toHaveTextContent('₹2,76,500.00')         // unchanged
  })

  it('opening /transfer/confirm with nothing pending goes back to the form', () => {
    renderAt('/transfer/confirm', LOGGED_IN)
    expect(heading()).toBe('Transfer money')
  })

  it('account and transaction details come from the URL; unknown ids are handled', () => {
    renderAt('/accounts/SB-1002', LOGGED_IN)
    expect(heading()).toBe('Emergency Fund')
  })

  it.each([['/accounts/XX-9', 'Account not found'], ['/transactions/TXN-9999', 'Transaction not found']])(
    '%s → %s', (path, title) => {
      renderAt(path, LOGGED_IN)
      expect(heading()).toBe(title)
    })
})
