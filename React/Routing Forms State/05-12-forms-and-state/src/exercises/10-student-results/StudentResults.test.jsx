import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import StudentList from './StudentList.jsx'
import StudentResultsApp from './StudentResultsApp.jsx'
import { validateStudent } from './StudentForm.jsx'

async function addStudent(user, name, marks) {
  await user.type(screen.getByLabelText('Student name'), name)
  await user.type(screen.getByLabelText('Marks (0–100)'), marks)
  await user.click(screen.getByRole('button', { name: 'Add student' }))
}
const stat = (label) => screen.getByLabelText(label).textContent
const names = () => screen.queryAllByRole('row').slice(1).map((row) => row.cells[0].textContent)

describe('validateStudent()', () => {
  it('accepts 0 and 100 but not -1, 101 or 55.5 (boundaries)', () => {
    expect(validateStudent({ name: 'A', marks: '0' })).toEqual({})
    expect(validateStudent({ name: 'A', marks: '100' })).toEqual({})
    for (const marks of ['-1', '101', '55.5']) {
      expect(validateStudent({ name: 'A', marks }).marks).toBe('Marks must be a whole number from 0 to 100')
    }
    expect(validateStudent({ name: ' ', marks: '' })).toEqual({ name: 'Student name is required', marks: 'Marks is required' })
  })
})

describe('Student results (lifted state)', () => {
  it('StudentList shows exactly the students it receives as props', () => {
    render(<StudentList students={[{ id: 1, name: 'Zara', marks: 39 }]} passMark={40} emptyText="none" />)
    expect(screen.getByText('Zara')).toBeInTheDocument()
    expect(screen.getByText('Failed')).toBeInTheDocument()
  })

  it('the form sends students up, and the list and statistics update', async () => {
    const user = userEvent.setup()
    render(<StudentResultsApp />)
    expect(screen.getByText('No students yet. Add one with the form.')).toBeInTheDocument()
    expect(stat('Average marks')).toBe('0.0')

    await addStudent(user, 'Asha', '82')
    await addStudent(user, 'Ravi', '40')          // exactly the pass mark: passed
    await addStudent(user, 'Karan', '39')
    expect(names()).toEqual(['Asha', 'Ravi', 'Karan'])
    expect(stat('Students')).toBe('3')
    expect(stat('Average marks')).toBe('53.7')    // 161 / 3
    expect(stat('Passed')).toBe('2')
    expect(screen.getByLabelText('Student name')).toHaveValue('')
  })

  it('rejects invalid marks without adding anything', async () => {
    const user = userEvent.setup()
    render(<StudentResultsApp />)
    await addStudent(user, 'Asha', '120')
    expect(screen.getByText('Marks must be a whole number from 0 to 100')).toBeInTheDocument()
    expect(stat('Students')).toBe('0')
  })

  it('filters All / Passed / Failed', async () => {
    const user = userEvent.setup()
    render(<StudentResultsApp />)
    await addStudent(user, 'Asha', '82')
    await addStudent(user, 'Karan', '39')

    await user.click(screen.getByRole('button', { name: 'Passed' }))
    expect(names()).toEqual(['Asha'])
    await user.click(screen.getByRole('button', { name: 'Failed' }))
    expect(names()).toEqual(['Karan'])
    await user.click(screen.getByRole('button', { name: 'All' }))
    expect(names()).toEqual(['Asha', 'Karan'])
    expect(stat('Students')).toBe('2')            // statistics always use all students
  })
})
