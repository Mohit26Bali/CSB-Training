import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import TodoApp from './TodoApp.jsx'

async function addTasks(user, ...texts) {
  for (const text of texts) {
    await user.type(screen.getByLabelText('New task'), text)
    await user.click(screen.getByRole('button', { name: 'Add' }))
  }
}

const taskTexts = () => screen.queryAllByRole('listitem').map((item) => item.querySelector('.todo__text')?.textContent)

describe('To-Do List', () => {
  it('starts empty', () => {
    render(<TodoApp />)
    expect(screen.getByText('No tasks yet. Add one above.')).toBeInTheDocument()
    expect(screen.getByText('0 pending tasks')).toBeInTheDocument()
  })

  it('adds tasks and clears the input', async () => {
    const user = userEvent.setup()
    render(<TodoApp />)
    await addTasks(user, 'Write API docs', 'Review PR')

    expect(taskTexts()).toEqual(['Write API docs', 'Review PR'])
    expect(screen.getByLabelText('New task')).toHaveValue('')
    expect(screen.getByText('2 pending tasks')).toBeInTheDocument()
  })

  it('ignores an empty or blank task (invalid input)', async () => {
    const user = userEvent.setup()
    render(<TodoApp />)
    await user.click(screen.getByRole('button', { name: 'Add' }))
    await addTasks(user, '    ')
    expect(screen.queryAllByRole('listitem')).toHaveLength(0)
  })

  it('completes a task, updates the pending count, and filters', async () => {
    const user = userEvent.setup()
    render(<TodoApp />)
    await addTasks(user, 'Write API docs', 'Review PR')

    await user.click(screen.getByLabelText('Complete Write API docs'))
    expect(screen.getByText('1 pending task')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Active' }))
    expect(taskTexts()).toEqual(['Review PR'])
    await user.click(screen.getByRole('button', { name: 'Completed' }))
    expect(taskTexts()).toEqual(['Write API docs'])
    await user.click(screen.getByRole('button', { name: 'All' }))
    expect(taskTexts()).toHaveLength(2)
  })

  it('edits and deletes a task', async () => {
    const user = userEvent.setup()
    render(<TodoApp />)
    await addTasks(user, 'Write API docs', 'Review PR')

    const first = screen.getAllByRole('listitem')[0]
    await user.click(within(first).getByRole('button', { name: 'Edit' }))
    await user.clear(screen.getByLabelText('Edit Write API docs'))
    await user.type(screen.getByLabelText('Edit Write API docs'), 'Write user guide')
    await user.click(screen.getByRole('button', { name: 'Save' }))
    expect(taskTexts()).toEqual(['Write user guide', 'Review PR'])

    await user.click(within(screen.getAllByRole('listitem')[1]).getByRole('button', { name: 'Delete' }))
    expect(taskTexts()).toEqual(['Write user guide'])
  })
})
