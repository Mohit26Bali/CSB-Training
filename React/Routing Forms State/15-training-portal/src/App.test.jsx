import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import App from './App.jsx'
import { sampleData } from './state/sampleData.js'
import { STORAGE_KEY, loadData } from './state/TrainingContext.jsx'
import { enrollmentProblem, trainingReducer } from './state/trainingReducer.js'
import { validateCourse, validateParticipant } from './state/validation.js'

beforeEach(() => localStorage.clear())

const renderAt = (path) => render(<MemoryRouter initialEntries={[path]}><App /></MemoryRouter>)
const heading = () => screen.getByRole('heading', { level: 1 }).textContent

describe('trainingReducer', () => {
  it('ENROLL adds an enrollment; duplicates and full courses are refused', () => {
    const next = trainingReducer(sampleData, { type: 'ENROLL', participantId: 'P-104', courseId: 'C-201', date: '2026-10-26' })
    expect(next.enrollments.at(-1)).toEqual({ id: 'E-306', participantId: 'P-104', courseId: 'C-201', date: '2026-10-26', status: 'Enrolled' })
    expect(sampleData.enrollments).toHaveLength(5)                                          // not mutated

    expect(enrollmentProblem(sampleData, 'P-101', 'C-201')).toBe('This participant is already enrolled in this course')
    const full = trainingReducer(sampleData, { type: 'ENROLL', participantId: 'P-104', courseId: 'C-202', date: 'x' })
    expect(enrollmentProblem(full, 'P-101', 'C-202')).toBe('This course is full')            // capacity 3 reached
    expect(trainingReducer(full, { type: 'ENROLL', participantId: 'P-101', courseId: 'C-202', date: 'x' })).toBe(full)
  })

  it('COMPLETE and CANCEL change one enrollment; RESET restores the sample data', () => {
    const done = trainingReducer(sampleData, { type: 'COMPLETE_ENROLLMENT', id: 'E-301' })
    expect(done.enrollments.find((e) => e.id === 'E-301').status).toBe('Completed')
    const cancelled = trainingReducer(done, { type: 'CANCEL_ENROLLMENT', id: 'E-302' })
    expect(cancelled.enrollments.some((e) => e.id === 'E-302')).toBe(false)
    expect(trainingReducer(cancelled, { type: 'RESET_DATA' })).toBe(sampleData)
  })
})

describe('validation', () => {
  const participant = { name: 'Priya Menon', email: 'priya@company.com', phone: '9876501234', organization: 'Initech', level: 'Beginner' }
  const course = { title: 'Docker Basics', category: 'Cloud', trainerId: 'T-3', startDate: '2026-12-01', durationDays: '3', capacity: '10' }

  it('accepts valid data', () => {
    expect(validateParticipant(participant, sampleData.participants)).toEqual({})
    expect(validateCourse(course, sampleData.courses, sampleData.trainers)).toEqual({})
  })

  it('rejects invalid participants (duplicate email, bad phone, missing fields)', () => {
    expect(validateParticipant({ ...participant, email: 'ASHA.VERMA@company.com' }, sampleData.participants).email)
      .toBe('A participant with this email is already registered')
    expect(validateParticipant({ ...participant, phone: '12345' }, []).phone).toMatch(/10 digits/)
    expect(Object.keys(validateParticipant({ name: '', email: '', phone: '', organization: '', level: '' }, [])))
      .toEqual(['name', 'email', 'phone', 'organization', 'level'])
  })

  it('checks course boundaries: duration 1–90, capacity 1–100, unique title', () => {
    const v = (c) => validateCourse({ ...course, ...c }, sampleData.courses, sampleData.trainers)
    expect(v({ durationDays: '0' }).durationDays).toBeDefined()
    expect(v({ durationDays: '90' }).durationDays).toBeUndefined()
    expect(v({ capacity: '101' }).capacity).toBeDefined()
    expect(v({ capacity: '1' }).capacity).toBeUndefined()
    expect(v({ title: 'react for beginners' }).title).toBe('A course with this title already exists')
  })
})

describe('persistence (localStorage)', () => {
  it('falls back to the sample data for missing or broken storage', () => {
    expect(loadData()).toBe(sampleData)
    localStorage.setItem(STORAGE_KEY, '{oops')
    expect(loadData()).toBe(sampleData)
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ participants: 'not a list' }))
    expect(loadData()).toBe(sampleData)
  })

  it('saves changes and restores them on the next start', async () => {
    const user = userEvent.setup()
    const { unmount } = renderAt('/enrollments')
    await user.click(screen.getByRole('button', { name: 'Mark E-301 completed' }))
    expect(JSON.parse(localStorage.getItem(STORAGE_KEY)).enrollments[0].status).toBe('Completed')

    unmount()
    renderAt('/dashboard')                                                                    // "restart"
    expect(screen.getByLabelText('Completed')).toHaveTextContent('3')
  })
})

describe('Training portal', () => {
  it('the dashboard summarises the shared state', () => {
    renderAt('/')
    expect(heading()).toBe('Dashboard')
    expect(screen.getByLabelText('Trainers')).toHaveTextContent('4')
    expect(screen.getByLabelText('Participants')).toHaveTextContent('4')
    expect(screen.getByLabelText('Active enrollments')).toHaveTextContent('3')
  })

  it('registers a participant, lands on the new participant page and enrolls them', async () => {
    const user = userEvent.setup()
    renderAt('/participants/new')
    await user.click(screen.getByRole('button', { name: 'Register' }))
    expect(screen.getByText('Name is required')).toBeInTheDocument()                          // invalid: stays

    await user.type(screen.getByLabelText('Full name'), 'Priya Menon')
    await user.type(screen.getByLabelText('Email'), 'priya@company.com')
    await user.type(screen.getByLabelText('Mobile number'), '9876501234')
    await user.type(screen.getByLabelText('Organization'), 'Initech')
    await user.selectOptions(screen.getByLabelText('Experience level'), 'Beginner')
    await user.click(screen.getByRole('button', { name: 'Register' }))

    expect(heading()).toBe('Priya Menon')
    expect(screen.getByRole('status')).toHaveTextContent('Priya Menon was registered as P-105.')

    await user.selectOptions(screen.getByLabelText('Course'), 'C-203')
    await user.click(screen.getByRole('button', { name: 'Enroll' }))
    expect(screen.getByRole('link', { name: 'AWS Cloud Practitioner' })).toBeInTheDocument()
  })

  it('creates a course and enrolls a participant from the course page; full courses refuse', async () => {
    const user = userEvent.setup()
    renderAt('/courses/new')
    await user.type(screen.getByLabelText('Title'), 'Docker Basics')
    await user.selectOptions(screen.getByLabelText('Category'), 'Cloud')
    await user.selectOptions(screen.getByLabelText('Trainer'), 'T-3')
    await user.type(screen.getByLabelText('Start date'), '2026-12-01')
    await user.type(screen.getByLabelText('Duration (days)'), '3')
    await user.type(screen.getByLabelText('Capacity (seats)'), '1')
    await user.click(screen.getByRole('button', { name: 'Create course' }))

    expect(heading()).toBe('Docker Basics')
    await user.selectOptions(screen.getByLabelText('Participant'), 'P-102')
    await user.click(screen.getByRole('button', { name: 'Enroll' }))
    expect(screen.getByLabelText('Seats')).toHaveTextContent('1 / 1 (full)')

    await user.selectOptions(screen.getByLabelText('Participant'), 'P-103')
    await user.click(screen.getByRole('button', { name: 'Enroll' }))
    expect(screen.getByRole('alert')).toHaveTextContent('This course is full')
  })

  it('search and filters narrow the lists', async () => {
    const user = userEvent.setup()
    renderAt('/participants')
    await user.type(screen.getByLabelText('Search participants'), 'globex')
    expect(screen.getAllByRole('row')).toHaveLength(3)                                        // header + 2
    await user.selectOptions(screen.getByLabelText('Filter by level'), 'Advanced')
    expect(screen.getByRole('link', { name: 'Karan Shah' })).toBeInTheDocument()
    expect(screen.getAllByRole('row')).toHaveLength(2)

    await user.clear(screen.getByLabelText('Search participants'))
    await user.type(screen.getByLabelText('Search participants'), 'nobody')
    expect(screen.getByText('No participants match these filters.')).toBeInTheDocument()
  })

  it('the course list filters by category', async () => {
    const user = userEvent.setup()
    renderAt('/courses')
    await user.selectOptions(screen.getByLabelText('Filter by category'), 'Cloud')
    expect(within(screen.getByRole('table')).getAllByRole('link').map((a) => a.textContent)).toEqual(['AWS Cloud Practitioner'])
  })

  it.each([
    ['/participants/P-999', 'Participant not found'],
    ['/courses/C-999', 'Course not found'],
    ['/anything', '404 — Page Not Found'],
  ])('%s → %s', (path, title) => {
    renderAt(path)
    expect(heading()).toBe(title)
  })

  it('reports derive occupancy and completion rates', () => {
    renderAt('/reports')
    const row = within(screen.getByRole('table', { name: 'Course report' })).getByText('Presentation Skills').closest('tr')
    expect(within(row).getAllByRole('cell').map((c) => c.textContent)).toEqual(['Presentation Skills', '2', '25', '8%', '2', '100%'])
  })
})
