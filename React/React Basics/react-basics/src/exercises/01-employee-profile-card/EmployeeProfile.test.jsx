import { render, screen } from '@testing-library/react'
import EmployeeCard from './EmployeeCard.jsx'
import EmployeeProfileApp from './EmployeeProfileApp.jsx'
import { employees } from './employees.js'

describe('Employee Profile Card', () => {
  it('shows every field of one employee', () => {
    render(<EmployeeCard employee={employees[0]} />)

    expect(screen.getByRole('heading', { name: 'Asha Verma' })).toBeInTheDocument()
    expect(screen.getByText('ID: EMP-101')).toBeInTheDocument()
    expect(screen.getByText('Software Engineer - Engineering')).toBeInTheDocument()
    expect(screen.getByText('Location: Bengaluru')).toBeInTheDocument()
    expect(screen.getByText(/mentors new joiners/)).toBeInTheDocument()
  })

  it('renders one card per employee in the data array', () => {
    render(<EmployeeProfileApp />)

    expect(screen.getAllByRole('article')).toHaveLength(employees.length)
    expect(employees.length).toBeGreaterThanOrEqual(3)
  })
})
