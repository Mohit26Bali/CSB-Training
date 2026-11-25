import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import LoanApplicationApp from './LoanApplicationApp.jsx'
import { STEPS } from './loanSteps.js'

const step = () => screen.getByText(/^Step \d of 4/).textContent
const next = (user) => user.click(screen.getByRole('button', { name: 'Next' }))

async function fillPersonal(user) {
  await user.type(screen.getByLabelText('Full name'), 'Asha Verma')
  await user.type(screen.getByLabelText('Email'), 'asha@company.com')
  await user.type(screen.getByLabelText('Mobile number'), '9876543210')
  await user.type(screen.getByLabelText('Age'), '29')
}
async function fillEmployment(user) {
  await user.selectOptions(screen.getByLabelText('Employment type'), 'Salaried')
  await user.type(screen.getByLabelText('Employer / business name'), 'Acme Ltd')
  await user.type(screen.getByLabelText('Monthly income (₹)'), '85000')
  await user.type(screen.getByLabelText('Years in current job'), '4')
}
async function fillLoan(user) {
  await user.selectOptions(screen.getByLabelText('Loan type'), 'Car')
  await user.type(screen.getByLabelText('Loan amount (₹)'), '600000')
  await user.type(screen.getByLabelText('Tenure (months)'), '60')
  await user.type(screen.getByLabelText('Purpose'), 'New car')
}

describe('step validators', () => {
  it('check the boundaries of age, amount and tenure', () => {
    const personal = { fullName: 'A', email: 'a@b.co', mobile: '9876543210' }
    expect(STEPS[0].validate({ ...personal, age: '20' }).age).toBeDefined()
    expect(STEPS[0].validate({ ...personal, age: '21' }).age).toBeUndefined()
    const loan = { loanType: 'Car', tenureMonths: '60', purpose: 'x' }
    expect(STEPS[2].validate({ ...loan, amount: '9999' }).amount).toBeDefined()
    expect(STEPS[2].validate({ ...loan, amount: '10000' }).amount).toBeUndefined()
    expect(STEPS[2].validate({ ...loan, amount: '600000', tenureMonths: '361' }).tenureMonths).toBeDefined()
  })
})

describe('Multi-step loan application', () => {
  it('does not move on while the current step is invalid', async () => {
    const user = userEvent.setup()
    render(<LoanApplicationApp />)
    expect(step()).toBe('Step 1 of 4: Personal information')
    expect(screen.getByRole('button', { name: 'Back' })).toBeDisabled()

    await next(user)
    expect(step()).toBe('Step 1 of 4: Personal information')
    expect(screen.getByText('Full name is required')).toBeInTheDocument()
  })

  it('keeps the data when going Back and Next', async () => {
    const user = userEvent.setup()
    render(<LoanApplicationApp />)
    await fillPersonal(user)
    await next(user)
    expect(step()).toBe('Step 2 of 4: Employment information')

    await user.click(screen.getByRole('button', { name: 'Back' }))
    expect(screen.getByLabelText('Full name')).toHaveValue('Asha Verma')
    expect(screen.getByLabelText('Age')).toHaveValue(29)
  })

  it('goes through all steps, edits one section from Review, and submits', async () => {
    const user = userEvent.setup()
    render(<LoanApplicationApp />)
    await fillPersonal(user); await next(user)
    await fillEmployment(user); await next(user)
    await fillLoan(user); await next(user)
    expect(step()).toBe('Step 4 of 4: Review and submit')
    expect(within(screen.getByRole('region', { name: 'Loan information' })).getByText('600000')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Edit Employment information' }))
    expect(step()).toBe('Step 2 of 4: Employment information')
    await user.clear(screen.getByLabelText('Employer / business name'))
    await user.type(screen.getByLabelText('Employer / business name'), 'Globex Pvt Ltd')
    await user.click(screen.getByRole('button', { name: 'Save and return to review' }))

    expect(step()).toBe('Step 4 of 4: Review and submit')                         // straight back to Review
    expect(screen.getByText('Globex Pvt Ltd')).toBeInTheDocument()
    expect(screen.getByText('Asha Verma')).toBeInTheDocument()                      // other data kept
    expect(screen.getByText('New car')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Submit application' }))
    expect(screen.getByRole('status')).toHaveTextContent('Application submitted: Car loan of ₹6,00,000 for Asha Verma.')
  })
})
