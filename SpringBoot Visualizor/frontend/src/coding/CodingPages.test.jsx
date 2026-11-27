import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { Link, MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../components/AppHeader', () => ({ default: () => <header /> }))
vi.mock('./codingApi', () => ({
  getCodingProgress: vi.fn(),
  getSubmission: vi.fn(),
  listCodingProblems: vi.fn(),
}))

import { getCodingProgress, getSubmission, listCodingProblems } from './codingApi'
import CodingCataloguePage from './CodingCataloguePage'
import CodingProgressPage from './CodingProgressPage'
import CodingSubmissionPage from './CodingSubmissionPage'
import { installLayoutStubs } from './testSupport'

beforeAll(installLayoutStubs)
afterEach(cleanup)

const http = (status, data = {}) => ({ response: { status, data } })
const PROBLEM = {
  slug: 'retry-with-bounded-backoff', title: 'Retry with bounded backoff', difficulty: 'MEDIUM', topicSlug: 'microservices', topicName: 'Microservices Architecture',
  language: 'java17', summary: 'Implement a retry executor.', version: 1, accepted: false, attempts: 0,
}
const PROGRESS = {
  problemsAttempted: 1, problemsAccepted: 1, totalFullSubmissions: 3,
  problems: [{ slug: PROBLEM.slug, title: PROBLEM.title, attempts: 3, accepted: true, acceptedAt: '2026-11-04T10:00:00Z', latestVerdict: 'WRONG_ANSWER', latestSubmissionId: 9, latestAt: '2026-11-04T11:00:00Z' }],
}

beforeEach(() => {
  vi.clearAllMocks()
  listCodingProblems.mockResolvedValue([PROBLEM])
  getCodingProgress.mockResolvedValue({ problemsAttempted: 0, problemsAccepted: 0, totalFullSubmissions: 0, problems: [] })
})

describe('coding catalogue', () => {
  const open = () =>
    render(
      <MemoryRouter initialEntries={['/learn/coding']}>
        <Routes>
          <Route path="/learn/coding" element={<CodingCataloguePage />} />
          <Route path="/learn/coding/:slug" element={<p>workspace</p>} />
        </Routes>
      </MemoryRouter>,
    )

  it('shows loading, then each problem with its title, topic, difficulty, language and status', async () => {
    listCodingProblems.mockReturnValue(new Promise(() => {}))
    open()
    expect(screen.getByRole('status').textContent).toMatch(/Loading the coding problems/)
    cleanup()
    listCodingProblems.mockResolvedValue([PROBLEM])
    open()
    const card = await screen.findByTestId('problem-retry-with-bounded-backoff')
    expect(within(card).getByRole('link', { name: 'Retry with bounded backoff' }).getAttribute('href')).toBe('/learn/coding/retry-with-bounded-backoff')
    expect(within(card).getByText('MEDIUM')).toBeTruthy()
    expect(within(card).getByText('Microservices Architecture')).toBeTruthy()
    expect(within(card).getByText('Java 17')).toBeTruthy()
    expect(within(card).getByText('Not attempted')).toBeTruthy()
    expect(within(card).getByText('Implement a retry executor.')).toBeTruthy()
    expect(within(card).getByRole('link', { name: 'Open problem' })).toBeTruthy()
  })

  it('shows acceptance and attempt status from the API', async () => {
    listCodingProblems.mockResolvedValue([
      { ...PROBLEM, accepted: true, attempts: 2 },
      { ...PROBLEM, slug: 'second', title: 'Second', accepted: false, attempts: 1 },
    ])
    open()
    await screen.findByText('Second')
    expect(screen.getByText('Accepted')).toBeTruthy()
    expect(screen.getByText('Attempted, 1 submission')).toBeTruthy()
    expect(screen.getAllByRole('link', { name: 'Continue' })).toHaveLength(2)
  })

  it('lists only what the API returns, and says so when there is nothing', async () => {
    listCodingProblems.mockResolvedValue([])
    open()
    expect(await screen.findByText('No coding problems yet')).toBeTruthy()
    expect(screen.queryByRole('list', { name: 'Coding problems' })).toBeNull()
  })

  it('reports a failed load, and a 503 in words', async () => {
    listCodingProblems.mockRejectedValue(http(503))
    open()
    expect((await screen.findByRole('alert')).textContent).toMatch(/not available right now/)
    cleanup()
    listCodingProblems.mockRejectedValue({})
    open()
    expect((await screen.findByRole('alert')).textContent).toMatch(/Cannot reach the server/)
  })

  it('shows the progress summary when it can be read, and still works when it cannot', async () => {
    getCodingProgress.mockResolvedValue(PROGRESS)
    open()
    expect((await screen.findByTestId('progress-summary')).textContent).toBe('1 accepted · 1 attempted · 3 submissions')
    cleanup()
    getCodingProgress.mockRejectedValue(http(500))
    open()
    await screen.findByTestId('problem-retry-with-bounded-backoff')
    expect(screen.queryByTestId('progress-summary')).toBeNull()
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('opens the workspace from a card', async () => {
    open()
    ;(await screen.findByRole('link', { name: 'Open problem' })).click()
    expect(await screen.findByText('workspace')).toBeTruthy()
  })
})

describe('submission detail', () => {
  const SUBMISSION = {
    id: 9, problemSlug: 'retry-with-bounded-backoff', problemVersion: 1, verdict: 'WRONG_ANSWER', passed: 9, total: 12,
    visibleTests: [
      { name: 'Returns the first successful result', status: 'PASS', message: null },
      { name: 'Retries transient failures with doubling delays', status: 'FAIL', message: 'expected waits [100, 200] but got [100]' },
    ],
    hiddenTotal: 9, hiddenPassed: 7, compilerOutput: '', feedback: 'Some sample tests fail.', submittedAt: '2026-11-04T11:00:00Z',
    sourceCode: 'public final class RetryExecutor {\n}\n',
  }
  const open = (id = '9') =>
    render(
      <MemoryRouter initialEntries={[`/learn/coding/submissions/${id}`]}>
        <Routes>
          <Route path="/learn/coding/submissions/:id" element={<CodingSubmissionPage />} />
          <Route path="/learn/coding/:slug" element={<p>workspace</p>} />
        </Routes>
      </MemoryRouter>,
    )

  it('shows the verdict, version, time, counts, visible tests, hidden aggregates, the source, and a link back to the problem', async () => {
    getSubmission.mockResolvedValue(SUBMISSION)
    open()
    await screen.findByText('Wrong answer')
    expect(getSubmission).toHaveBeenCalledWith('9')
    expect(screen.getByRole('heading', { name: 'Submission #9' })).toBeTruthy()
    expect(screen.getByText('problem version 1')).toBeTruthy()
    expect(screen.getByTestId('counts').textContent).toBe('9 of 12 tests passed (hidden tests: 7 of 9 passed)')
    expect(screen.getByText('expected waits [100, 200] but got [100]')).toBeTruthy()
    expect(screen.getByText('Some sample tests fail.')).toBeTruthy()
    await waitFor(() => expect(screen.getByTestId('submission-source').textContent).toContain('public final class RetryExecutor')) // the editor is built just after the page
    expect(screen.getByRole('link', { name: /Back to the problem/ }).getAttribute('href')).toBe('/learn/coding/retry-with-bounded-backoff')
    expect(screen.getByText(/never shown/)).toBeTruthy()
  })

  it('the source of a past submission is shown read-only', async () => {
    getSubmission.mockResolvedValue(SUBMISSION)
    open()
    await screen.findByText('Wrong answer')
    expect(await screen.findByRole('textbox', { name: /Submitted source code of submission 9/ })).toBeTruthy()
    const content = document.querySelector('.cm-content')
    expect(content.getAttribute('contenteditable')).not.toBe('false') // focusable and selectable, so it can be copied
  })

  it('shows compiler output for a compile error', async () => {
    getSubmission.mockResolvedValue({ ...SUBMISSION, verdict: 'COMPILE_ERROR', passed: 0, visibleTests: [], compilerOutput: "RetryExecutor.java:7: error: ';' expected" })
    open()
    expect(await screen.findByText('Compile error')).toBeTruthy()
    expect(screen.getByText(/RetryExecutor.java:7: error/)).toBeTruthy()
  })

  it('another user\'s or an unknown submission is simply "not found", with no source and no hint that it exists', async () => {
    getSubmission.mockRejectedValue(http(404, { detail: 'Submission not found' }))
    open('12345')
    expect((await screen.findByRole('alert')).textContent).toBe('This submission was not found.')
    expect(document.querySelector('.cm-editor')).toBeNull()
    expect(screen.getByRole('link', { name: /Coding practice/ }).getAttribute('href')).toBe('/learn/coding')
  })

  it('shows loading, and other failures in words', async () => {
    getSubmission.mockReturnValue(new Promise(() => {}))
    open()
    expect(screen.getByRole('status').textContent).toMatch(/Loading the submission/)
    cleanup()
    getSubmission.mockRejectedValue({})
    open()
    expect((await screen.findByRole('alert')).textContent).toMatch(/Cannot reach the server/)
  })

  it('does not show a stale submission while the next one loads', async () => {
    getSubmission.mockResolvedValue(SUBMISSION)
    render(
      <MemoryRouter initialEntries={['/learn/coding/submissions/9']}>
        <Link to="/learn/coding/submissions/10">next</Link>
        <Routes>
          <Route path="/learn/coding/submissions/:id" element={<CodingSubmissionPage />} />
        </Routes>
      </MemoryRouter>,
    )
    await screen.findByText('Wrong answer')
    getSubmission.mockReturnValue(new Promise(() => {}))
    fireEvent.click(screen.getByRole('link', { name: 'next' }))
    await waitFor(() => expect(getSubmission).toHaveBeenCalledWith('10'))
    expect(screen.getByRole('heading', { name: 'Submission #10' })).toBeTruthy()
    expect(screen.queryByText('Wrong answer')).toBeNull() // submission 9 is gone while 10 loads
    expect(screen.getByRole('status').textContent).toMatch(/Loading the submission/)
  })
})

describe('coding progress', () => {
  const open = () =>
    render(
      <MemoryRouter initialEntries={['/learn/coding/progress']}>
        <Routes>
          <Route path="/learn/coding/progress" element={<CodingProgressPage />} />
        </Routes>
      </MemoryRouter>,
    )

  it('shows the three totals and each problem with its attempts, acceptance, latest verdict and a link to the latest submission', async () => {
    getCodingProgress.mockResolvedValue(PROGRESS)
    open()
    const totals = await screen.findByTestId('progress-totals')
    expect(totals.textContent).toContain('Problems accepted1')
    expect(totals.textContent).toContain('Problems attempted1')
    expect(totals.textContent).toContain('Total submissions3')
    const row = screen.getByRole('list', { name: 'Problems you have attempted' })
    expect(within(row).getByRole('link', { name: 'Retry with bounded backoff' }).getAttribute('href')).toBe('/learn/coding/retry-with-bounded-backoff')
    expect(within(row).getByText(/3 submissions · accepted/)).toBeTruthy()
    expect(within(row).getByText('Accepted')).toBeTruthy()
    expect(within(row).getByText('Wrong answer')).toBeTruthy()
    expect(within(row).getByRole('link', { name: 'view submission #9' }).getAttribute('href')).toBe('/learn/coding/submissions/9')
    expect(screen.getByText(/Sample runs are not counted/)).toBeTruthy()
  })

  it('an attempted but unsolved problem is not shown as accepted', async () => {
    getCodingProgress.mockResolvedValue({ problemsAttempted: 1, problemsAccepted: 0, totalFullSubmissions: 1, problems: [{ ...PROGRESS.problems[0], accepted: false, acceptedAt: null, attempts: 1, latestVerdict: 'TIME_LIMIT' }] })
    open()
    expect(await screen.findByText('Not solved yet')).toBeTruthy()
    expect(screen.queryByText('Accepted')).toBeNull()
    expect(screen.getByText('Time limit exceeded')).toBeTruthy()
  })

  it('has an empty state, a loading state and an error state', async () => {
    getCodingProgress.mockReturnValue(new Promise(() => {}))
    open()
    expect(screen.getByRole('status').textContent).toMatch(/Loading your progress/)
    cleanup()
    getCodingProgress.mockResolvedValue({ problemsAttempted: 0, problemsAccepted: 0, totalFullSubmissions: 0, problems: [] })
    open()
    expect(await screen.findByText('Nothing here yet')).toBeTruthy()
    cleanup()
    getCodingProgress.mockRejectedValue(http(500, { detail: 'progress unavailable' }))
    open()
    expect((await screen.findByRole('alert')).textContent).toBe('progress unavailable')
  })
})

describe('coding catalogue filters', () => {
  const make = (slug, title, difficulty, topicSlug, topicName) => ({ ...PROBLEM, slug, title, difficulty, topicSlug, topicName })
  const MANY = [
    make('circuit-breaker-state-machine', 'Circuit breaker state machine', 'MEDIUM', 'microservices', 'Microservices Architecture'),
    make('saga-compensation-coordinator', 'Saga compensation coordinator', 'HARD', 'microservices', 'Microservices Architecture'),
    make('password-validator', 'Password validator', 'EASY', 'java-core', 'Java Core'),
    make('request-validator', 'Request validation', 'EASY', 'spring-boot-basics', 'Spring Boot Basics'),
    make('bean-registry-primary', 'Bean registry', 'MEDIUM', 'spring-boot-basics', 'Spring Boot Basics'),
  ]
  const open = () =>
    render(
      <MemoryRouter initialEntries={['/learn/coding']}>
        <Routes>
          <Route path="/learn/coding" element={<CodingCataloguePage />} />
        </Routes>
      </MemoryRouter>,
    )
  const titles = () => screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent)

  it('lists easier problems first and says how many are shown', async () => {
    listCodingProblems.mockResolvedValue(MANY)
    open()
    await screen.findByTestId('problem-password-validator')
    expect(titles()).toEqual(['Password validator', 'Request validation', 'Circuit breaker state machine', 'Bean registry', 'Saga compensation coordinator'])
    expect(screen.getByTestId('filter-count').textContent).toBe('Showing 5 of 5 problems')
  })

  it('filters by topic and by difficulty, and the two combine', async () => {
    listCodingProblems.mockResolvedValue(MANY)
    open()
    await screen.findByTestId('problem-password-validator')
    fireEvent.change(screen.getByLabelText('Topic'), { target: { value: 'spring-boot-basics' } })
    expect(titles()).toEqual(['Request validation', 'Bean registry'])
    expect(screen.getByTestId('filter-count').textContent).toBe('Showing 2 of 5 problems')
    fireEvent.change(screen.getByLabelText('Difficulty'), { target: { value: 'MEDIUM' } })
    expect(titles()).toEqual(['Bean registry'])
    fireEvent.change(screen.getByLabelText('Topic'), { target: { value: '' } })
    expect(titles()).toEqual(['Circuit breaker state machine', 'Bean registry']) // equal difficulty keeps the server order
  })

  it('offers only the topics and difficulties that exist', async () => {
    listCodingProblems.mockResolvedValue(MANY.slice(2, 4))
    open()
    await screen.findByTestId('problem-password-validator')
    const topics = within(screen.getByLabelText('Topic')).getAllByRole('option').map((o) => o.textContent)
    expect(topics).toEqual(['All topics', 'Java Core', 'Spring Boot Basics'])
    const levels = within(screen.getByLabelText('Difficulty')).getAllByRole('option').map((o) => o.textContent)
    expect(levels).toEqual(['All difficulties', 'Easy'])
  })

  it('explains an empty result and can reset the filters', async () => {
    listCodingProblems.mockResolvedValue(MANY)
    open()
    await screen.findByTestId('problem-password-validator')
    fireEvent.change(screen.getByLabelText('Topic'), { target: { value: 'java-core' } })
    fireEvent.change(screen.getByLabelText('Difficulty'), { target: { value: 'HARD' } })
    expect(screen.getByText('No problems match these filters')).toBeTruthy()
    expect(screen.queryByRole('list', { name: 'Coding problems' })).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'Show all problems' }))
    expect(titles()).toHaveLength(5)
  })

  it('shows no filters when there is only one problem', async () => {
    listCodingProblems.mockResolvedValue([PROBLEM])
    open()
    await screen.findByTestId('problem-retry-with-bounded-backoff')
    expect(screen.queryByLabelText('Topic')).toBeNull()
  })
})
