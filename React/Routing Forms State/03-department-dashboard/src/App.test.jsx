import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import App from './App.jsx'

const renderAt = (path) => render(<MemoryRouter initialEntries={[path]}><App /></MemoryRouter>)
const tab = (name) => screen.getByRole('heading', { level: 2, name })
const activeTab = () => document.querySelector('.dashboard__tab--active')

describe('Department dashboard with nested routes', () => {
  it('/departments/:departmentId opens the Overview by default', () => {
    renderAt('/departments/engineering')
    expect(screen.getByRole('heading', { level: 1, name: 'Engineering' })).toBeInTheDocument()
    expect(tab('Overview')).toBeInTheDocument()
    expect(activeTab()).toHaveTextContent('Overview')
  })

  it('the parent dashboard stays while the child route changes', async () => {
    const user = userEvent.setup()
    renderAt('/departments/engineering/overview')
    const header = screen.getByRole('heading', { level: 1, name: 'Engineering' })

    for (const section of ['Employees', 'Projects', 'Reports']) {
      await user.click(screen.getByRole('link', { name: section }))
      expect(tab(section)).toBeInTheDocument()
      expect(activeTab()).toHaveTextContent(section)
      expect(screen.getByRole('heading', { level: 1, name: 'Engineering' })).toBe(header)   // same element: not re-created
    }
    expect(screen.getByText('Q3 delivery report')).toBeInTheDocument()
  })

  it('the department parameter selects the data shown', () => {
    renderAt('/departments/finance/employees')
    expect(screen.getByText('Arjun Nair')).toBeInTheDocument()
    expect(screen.queryByText('Asha Verma')).not.toBeInTheDocument()
  })

  it('a department without projects says so (empty case)', () => {
    renderAt('/departments/human-resources/projects')
    expect(screen.getByText('No projects in this department.')).toBeInTheDocument()
  })

  it('an unknown department or section is handled', () => {
    renderAt('/departments/marketing/overview')
    expect(screen.getByRole('heading', { name: 'Department not found' })).toBeInTheDocument()
  })

  it('an unknown section keeps the dashboard and explains', () => {
    renderAt('/departments/engineering/budget')
    expect(screen.getByRole('heading', { level: 1, name: 'Engineering' })).toBeInTheDocument()
    expect(screen.getByText('There is no such section in this dashboard.')).toBeInTheDocument()
  })

  it('the list links into each dashboard', async () => {
    const user = userEvent.setup()
    renderAt('/')
    await user.click(screen.getByRole('link', { name: /Finance/ }))
    expect(screen.getByRole('heading', { level: 1, name: 'Finance' })).toBeInTheDocument()
  })
})
