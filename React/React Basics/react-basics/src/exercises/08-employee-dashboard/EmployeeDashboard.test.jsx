import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import EmployeeDashboardApp from './EmployeeDashboardApp.jsx'
import { employees } from './employees.js'
import { averageSalary, selectEmployees } from './dashboardData.js'

const stat = (label) => screen.getByLabelText(label).textContent
const names = () => screen.getAllByRole('row').slice(1).map((row) => row.cells[0].textContent)

describe('dashboard data', () => {
  it('averages salaries, and is 0 for an empty list (boundary)', () => {
    expect(averageSalary([{ salary: 100 }, { salary: 200 }])).toBe(150)
    expect(averageSalary([])).toBe(0)
  })

  it('does not reorder the original array when sorting', () => {
    const before = employees.map((e) => e.id)
    selectEmployees(employees, 'All', 'Any', 'Salary (high–low)')
    expect(employees.map((e) => e.id)).toEqual(before)
  })
})

describe('Employee Dashboard', () => {
  it('shows all employees and their average salary at first', () => {
    render(<EmployeeDashboardApp />)
    expect(stat('Total employees')).toBe(String(employees.length))
    expect(stat('Matching employees')).toBe(String(employees.length))
    expect(stat('Average salary')).toBe('₹93,556')            // 842000 / 9
  })

  it('filters by department and recomputes the statistics', async () => {
    const user = userEvent.setup()
    render(<EmployeeDashboardApp />)
    await user.selectOptions(screen.getByLabelText('Department'), 'Engineering')

    expect(stat('Total employees')).toBe(String(employees.length))
    expect(stat('Matching employees')).toBe('3')
    expect(stat('Average salary')).toBe('₹1,15,667')          // (95000 + 92000 + 160000) / 3
    expect(screen.getByText('Showing 3 of 9 employees in Engineering.')).toBeInTheDocument()
  })

  it('sorts by salary and filters by salary range', async () => {
    const user = userEvent.setup()
    render(<EmployeeDashboardApp />)
    await user.selectOptions(screen.getByLabelText('Sort by'), 'Salary (high–low)')
    expect(names()[0]).toBe('Manoj Pillai')

    await user.selectOptions(screen.getByLabelText('Salary range'), 'Below ₹50,000')
    expect(names()).toEqual(['Meena Das', 'Priya Menon'])
  })

  it('shows a message and ₹0 average when nothing matches (boundary)', async () => {
    const user = userEvent.setup()
    render(<EmployeeDashboardApp />)
    await user.selectOptions(screen.getByLabelText('Department'), 'Finance')
    await user.selectOptions(screen.getByLabelText('Salary range'), 'Above ₹1,00,000')

    expect(screen.getByText('No employees match these filters.')).toBeInTheDocument()
    expect(stat('Matching employees')).toBe('0')
    expect(stat('Average salary')).toBe('₹0')
  })
})
