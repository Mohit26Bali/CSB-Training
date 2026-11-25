import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import BankAccountFormApp from './BankAccountFormApp.jsx'
import { initialForm, toSubmission, validateBankForm } from './bankForm.js'

const summary = () => JSON.parse(within(screen.getByLabelText('Live summary')).getByText(/customerName/).textContent)

describe('bank form logic', () => {
  it('requires the minimum deposit for the account type (boundary)', () => {
    const form = { ...initialForm(), customerName: 'A', accountType: 'Savings', minimumBalance: 'Zero balance' }
    expect(validateBankForm({ ...form, initialDeposit: '999' }).initialDeposit).toBe('A Savings account needs at least ₹1,000')
    expect(validateBankForm({ ...form, initialDeposit: '1000' }).initialDeposit).toBeUndefined()
    expect(validateBankForm({ ...form, accountType: 'Current', businessName: 'X', initialDeposit: '4999' }).initialDeposit)
      .toBe('A Current account needs at least ₹5,000')
  })

  it('submits only the fields of the chosen account type', () => {
    const form = { ...initialForm(), accountType: 'Current', businessName: 'Shah Traders', minimumBalance: 'Zero balance' }
    expect(toSubmission(form)).not.toHaveProperty('minimumBalance')
    expect(toSubmission(form).businessName).toBe('Shah Traders')
  })
})

describe('Dynamic bank account form', () => {
  it('shows Savings or Current fields depending on the account type', async () => {
    const user = userEvent.setup()
    render(<BankAccountFormApp />)
    expect(screen.queryByLabelText('Business name')).not.toBeInTheDocument()

    await user.selectOptions(screen.getByLabelText('Account type'), 'Savings')
    expect(screen.getByLabelText('Minimum balance preference')).toBeInTheDocument()
    expect(screen.queryByLabelText('Business name')).not.toBeInTheDocument()

    await user.selectOptions(screen.getByLabelText('Account type'), 'Current')
    expect(screen.getByLabelText('Business name')).toBeInTheDocument()
    expect(screen.queryByLabelText('Minimum balance preference')).not.toBeInTheDocument()
  })

  it('adds and removes nominees, and the live summary follows', async () => {
    const user = userEvent.setup()
    render(<BankAccountFormApp />)
    await user.type(screen.getByLabelText('Nominee 1 name'), 'Ravi')
    await user.click(screen.getByRole('button', { name: '+ Add nominee' }))
    await user.click(screen.getByRole('button', { name: '+ Add nominee' }))
    await user.type(screen.getByLabelText('Nominee 3 name'), 'Meena')
    expect(summary().nominees.map((n) => n.name)).toEqual(['Ravi', '', 'Meena'])

    await user.click(screen.getByRole('button', { name: 'Remove nominee 2' }))
    expect(summary().nominees.map((n) => n.name)).toEqual(['Ravi', 'Meena'])
    expect(screen.getByLabelText('Nominee 2 name')).toHaveValue('Meena')      // renumbered, value kept
  })

  it('shows errors on an invalid submit, then registers a valid customer', async () => {
    const user = userEvent.setup()
    render(<BankAccountFormApp />)
    await user.click(screen.getByRole('button', { name: 'Register customer' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Please correct the highlighted fields.')
    expect(screen.getByText('Customer name is required')).toBeInTheDocument()
    expect(screen.getByText('Nominee 1 name is required')).toBeInTheDocument()

    await user.type(screen.getByLabelText('Customer name'), 'Asha Verma')
    await user.selectOptions(screen.getByLabelText('Account type'), 'Savings')
    await user.type(screen.getByLabelText('Initial deposit (₹)'), '2500')
    await user.selectOptions(screen.getByLabelText('Minimum balance preference'), 'Zero balance')
    await user.type(screen.getByLabelText('Nominee 1 name'), 'Ravi Verma')
    await user.selectOptions(screen.getByLabelText('Nominee 1 relationship'), 'Spouse')
    await user.click(screen.getByRole('button', { name: 'Register customer' }))

    expect(screen.getByRole('status')).toHaveTextContent('Savings account opened for Asha Verma with 1 nominee(s).')
    expect(screen.getByLabelText('Customer name')).toHaveValue('')
  })

  it('removing every nominee is reported (empty case)', async () => {
    const user = userEvent.setup()
    render(<BankAccountFormApp />)
    await user.click(screen.getByRole('button', { name: 'Remove nominee 1' }))
    await user.click(screen.getByRole('button', { name: 'Register customer' }))
    expect(screen.getByText('Add at least one nominee')).toBeInTheDocument()
  })
})
