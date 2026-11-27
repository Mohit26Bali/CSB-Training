import { EditorView } from '@codemirror/view'
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../components/AppHeader', () => ({ default: () => <header /> }))
vi.mock('./codingApi', () => ({
  getCodingProblem: vi.fn(),
  getCodingProgress: vi.fn(),
  listSubmissions: vi.fn(),
  runCode: vi.fn(),
  submitCode: vi.fn(),
}))

import { getCodingProblem, getCodingProgress, listSubmissions, runCode, submitCode } from './codingApi'
import CodingProblemPage, { MAX_SOURCE_BYTES } from './CodingProblemPage'
import { starterClassName } from './starterClass'
import { installLayoutStubs } from './testSupport'

beforeAll(installLayoutStubs)
afterEach(() => {
  cleanup()
  vi.restoreAllMocks() // a spy on window.confirm must never leak into the next test
})

const SLUG = 'retry-with-bounded-backoff'
const STARTER = 'import java.util.concurrent.Callable;\n\npublic final class RetryExecutor {\n    // TODO: implement\n}\n'

const PROBLEM = {
  slug: SLUG,
  title: 'Retry with bounded backoff',
  difficulty: 'MEDIUM',
  topicSlug: 'microservices',
  topicName: 'Microservices Architecture',
  language: 'java17',
  version: 1,
  summary: 'Implement a retry executor.',
  statement: '## Retry with bounded backoff\n\nA call can fail for a moment and succeed a moment later.\n\n1. **Attempts.** `maxAttempts` counts the first call.\n2. **Delay.** Wait `min(base * 2^(k-1), max)`.',
  objectives: ['Count attempts correctly.', 'Avoid arithmetic overflow.'],
  constraints: '* `maxAttempts` can be zero or negative.\n* Delays are non-negative `long` values.',
  examples: [{ title: 'Succeeds on the first call', input: 'maxAttempts = 3; the operation returns "ok"', output: 'returns "ok"; sleeper never called', explanation: 'Nothing failed.' }],
  starterCode: STARTER,
  sampleTests: [{ name: 'Returns the first successful result', input: 'maxAttempts = 3; returns "ok"', expected: 'returns "ok"; ran 1 time(s)' }],
  hiddenTestCount: 9,
}

const RUN_OK = { verdict: 'ACCEPTED', passed: 3, total: 3, tests: [{ name: 'Returns the first successful result', status: 'PASS', message: null }], compilerOutput: '', feedback: 'All sample tests passed. Submit to run the hidden tests too.' }
const SUBMIT_OK = {
  id: 42, problemSlug: SLUG, problemVersion: 1, verdict: 'ACCEPTED', passed: 12, total: 12,
  visibleTests: [{ name: 'Returns the first successful result', status: 'PASS', message: null }], hiddenTotal: 9, hiddenPassed: 9, compilerOutput: '',
  feedback: 'All tests passed, including the hidden ones.', submittedAt: '2026-11-04T10:15:30Z',
}
const HISTORY = [{ id: 42, problemVersion: 1, verdict: 'ACCEPTED', passed: 12, total: 12, submittedAt: '2026-11-04T10:15:30Z' }]

const http = (status, data = {}) => ({ response: { status, data } })

function renderWorkspace(initial = `/learn/coding/${SLUG}`) {
  return render(
    <MemoryRouter initialEntries={[initial]}>
      <Routes>
        <Route path="/learn/coding/:slug" element={<CodingProblemPage />} />
        <Route path="/learn/coding" element={<p>catalogue page</p>} />
        <Route path="/learn/coding/submissions/:id" element={<p>submission page</p>} />
        <Route path="/learn/coding/progress" element={<p>progress page</p>} />
      </Routes>
    </MemoryRouter>,
  )
}

const view = () => EditorView.findFromDOM(document.querySelector('.cm-editor'))
const editorText = () => view().state.doc.toString()
const typeText = (text) =>
  act(() => {
    const v = view()
    v.dispatch({ changes: { from: v.state.doc.length, insert: text }, userEvent: 'input.type' })
  })
const replaceAll = (text) =>
  act(() => {
    const v = view()
    v.dispatch({ changes: { from: 0, to: v.state.doc.length, insert: text }, userEvent: 'input.type' })
  })
const runButton = () => screen.getByRole('button', { name: /sample tests/i })
const submitButton = () => screen.getByRole('button', { name: /submit|submitting/i })

async function loaded() {
  renderWorkspace()
  await screen.findByRole('heading', { level: 1, name: 'Retry with bounded backoff' })
  // the editor is created in an effect right after the page renders: wait for it, so no test races it
  await waitFor(() => expect(document.querySelector('.cm-editor')).not.toBeNull())
}

function deferred() {
  let resolve
  let reject
  const promise = new Promise((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

beforeEach(() => {
  vi.clearAllMocks()
  getCodingProblem.mockResolvedValue(PROBLEM)
  getCodingProgress.mockResolvedValue({ problemsAttempted: 0, problemsAccepted: 0, totalFullSubmissions: 0, problems: [] })
  listSubmissions.mockResolvedValue([])
  runCode.mockResolvedValue(RUN_OK)
  submitCode.mockResolvedValue(SUBMIT_OK)
})

describe('problem workspace', () => {
  it('shows loading, then the statement, objectives, constraints, examples, samples and the hidden-test count', async () => {
    getCodingProblem.mockReturnValue(new Promise(() => {}))
    renderWorkspace()
    expect(screen.getByRole('status').textContent).toMatch(/Loading the problem/)
    cleanup()

    getCodingProblem.mockResolvedValue(PROBLEM)
    await loaded()
    expect(screen.getByText('Medium', { exact: false }) || screen.getByText('MEDIUM')).toBeTruthy()
    expect(screen.getAllByText('Java 17').length).toBeGreaterThan(0)
    expect(screen.getByText('Microservices Architecture')).toBeTruthy()
    expect(screen.getByRole('heading', { name: 'Retry with bounded backoff', level: 3 })).toBeTruthy() // the statement's own heading
    expect(screen.getByText(/counts the first call/)).toBeTruthy()
    expect(screen.getByRole('heading', { name: 'What you will practise' })).toBeTruthy()
    expect(screen.getByText('Avoid arithmetic overflow.')).toBeTruthy()
    expect(screen.getByRole('heading', { name: 'Constraints' })).toBeTruthy()
    expect(screen.getByRole('heading', { name: 'Examples' })).toBeTruthy()
    expect(screen.getByText('Succeeds on the first call')).toBeTruthy()
    expect(screen.getByText('Nothing failed.')).toBeTruthy()
    expect(screen.getByRole('heading', { name: 'Sample tests' })).toBeTruthy()
    expect(screen.getByText('Returns the first successful result')).toBeTruthy()
    expect(screen.getByText(/also executes 9 hidden tests, whose details are never shown/)).toBeTruthy()
    expect(screen.getByText(/problem version 1/)).toBeTruthy()
  })

  it('initialises the editor with the starter code from the API, in Java, with a language indicator', async () => {
    await loaded()
    expect(editorText()).toBe(STARTER)
    expect(screen.getAllByText('Java 17').length).toBeGreaterThanOrEqual(2) // header and editor panel
    expect(document.querySelector('.cm-lineNumbers')).not.toBeNull()
    expect(screen.getByRole('textbox', { name: /Java source code/ })).toBeTruthy()
    expect(screen.queryByText('Not submitted')).toBeNull()
  })

  it('a problem that does not exist is a clear message with a way back, not a blank page', async () => {
    getCodingProblem.mockRejectedValue(http(404))
    renderWorkspace('/learn/coding/nope')
    expect(await screen.findByRole('alert')).toBeTruthy()
    expect(screen.getByRole('alert').textContent).toMatch(/does not exist or is no longer available/)
    expect(screen.getByRole('link', { name: /Back to coding practice/ }).getAttribute('href')).toBe('/learn/coding')
  })

  it('a server that cannot be reached is reported', async () => {
    getCodingProblem.mockRejectedValue({})
    renderWorkspace()
    expect((await screen.findByRole('alert')).textContent).toMatch(/Cannot reach the server/)
  })

  it('shows the learner\'s status for the problem from their progress', async () => {
    getCodingProgress.mockResolvedValue({ problemsAttempted: 1, problemsAccepted: 1, totalFullSubmissions: 2, problems: [{ slug: SLUG, attempts: 2, accepted: true }] })
    await loaded()
    expect(await screen.findByText('Solved')).toBeTruthy()
    cleanup()
    getCodingProgress.mockResolvedValue({ problemsAttempted: 1, problemsAccepted: 0, totalFullSubmissions: 3, problems: [{ slug: SLUG, attempts: 3, accepted: false }] })
    await loaded()
    expect(await screen.findByText(/3 attempts, not solved yet/)).toBeTruthy()
  })
})

describe('Run versus Submit', () => {
  it('Run calls the run endpoint with the current editor text, and is labelled a sample run', async () => {
    await loaded()
    typeText('// my edit\n')
    fireEvent.click(runButton())
    await screen.findByTestId('outcome')
    expect(runCode).toHaveBeenCalledTimes(1)
    expect(runCode.mock.calls[0][0]).toBe(SLUG)
    expect(runCode.mock.calls[0][1]).toBe(STARTER + '// my edit\n')
    expect(runCode.mock.calls[0][2]).toBeInstanceOf(AbortSignal)
    expect(submitCode).not.toHaveBeenCalled()
    expect(screen.getByText('Sample run, not an official submission')).toBeTruthy()
    expect(screen.getByTestId('counts').textContent).toBe('3 of 3 sample tests passed')
    expect(screen.queryByText('Accepted')).toBeNull()
    expect(screen.getByText('All sample tests passed')).toBeTruthy()
  })

  it('Run does not touch the history or progress; Submit refreshes both exactly once, after the response', async () => {
    await loaded()
    await waitFor(() => expect(listSubmissions).toHaveBeenCalledTimes(1))
    expect(getCodingProgress).toHaveBeenCalledTimes(1)
    fireEvent.click(runButton())
    await screen.findByTestId('outcome')
    expect(listSubmissions).toHaveBeenCalledTimes(1)
    expect(getCodingProgress).toHaveBeenCalledTimes(1)

    listSubmissions.mockResolvedValue(HISTORY)
    getCodingProgress.mockResolvedValue({ problemsAttempted: 1, problemsAccepted: 1, totalFullSubmissions: 1, problems: [{ slug: SLUG, attempts: 1, accepted: true }] })
    fireEvent.click(submitButton())
    await screen.findByText('Official submission #42')
    expect(submitCode).toHaveBeenCalledTimes(1)
    expect(submitCode.mock.calls[0][1]).toBe(STARTER)
    await waitFor(() => expect(listSubmissions).toHaveBeenCalledTimes(2))
    expect(getCodingProgress).toHaveBeenCalledTimes(2)
    expect(await screen.findByText('Solved')).toBeTruthy()
    const list = screen.getByRole('list', { name: 'Your submissions for this problem' })
    expect(within(list).getByRole('link').getAttribute('href')).toBe('/learn/coding/submissions/42')
  })

  it('Submit shows the official verdict with the aggregate hidden counts only', async () => {
    await loaded()
    fireEvent.click(submitButton())
    await screen.findByText('Official submission #42')
    expect(screen.getByText('Accepted')).toBeTruthy()
    expect(screen.getByTestId('counts').textContent).toBe('12 of 12 tests passed (hidden tests: 9 of 9 passed)')
    expect(within(screen.getByTestId('outcome')).getByText(/problem version 1/)).toBeTruthy()
  })

  it('never infers Accepted from passing sample tests: only the API\'s official verdict shows Accepted', async () => {
    submitCode.mockResolvedValue({ ...SUBMIT_OK, verdict: 'WRONG_ANSWER', passed: 11, hiddenPassed: 8, feedback: 'All sample tests pass, but 1 hidden test(s) fail.' })
    await loaded()
    fireEvent.click(runButton())
    await screen.findByText('All sample tests passed')
    expect(screen.queryByText('Accepted')).toBeNull()
    fireEvent.click(submitButton())
    await screen.findByText('Official submission #42')
    expect(screen.getByText('Wrong answer')).toBeTruthy()
    expect(screen.queryByText('Accepted')).toBeNull()
    expect(screen.getByTestId('counts').textContent).toBe('11 of 12 tests passed (hidden tests: 8 of 9 passed)')
    expect(screen.getByText(/1 hidden test\(s\) fail/)).toBeTruthy()
  })

  it('keeps the editor contents between a run and a submit', async () => {
    await loaded()
    replaceAll('// work in progress\n')
    fireEvent.click(runButton())
    await screen.findByTestId('outcome')
    expect(editorText()).toBe('// work in progress\n')
    fireEvent.click(submitButton())
    await screen.findByText('Official submission #42')
    expect(editorText()).toBe('// work in progress\n')
    expect(submitCode.mock.calls[0][1]).toBe('// work in progress\n')
  })

  it('marks unsaved work until an official submission saves it', async () => {
    await loaded()
    expect(screen.queryByText('Not submitted')).toBeNull()
    typeText('// edit\n')
    expect(screen.getByText('Not submitted')).toBeTruthy()
    fireEvent.click(runButton())
    await screen.findByTestId('outcome')
    expect(screen.getByText('Not submitted')).toBeTruthy() // a run does not save anything
    fireEvent.click(submitButton())
    await screen.findByText('Official submission #42')
    expect(screen.queryByText('Not submitted')).toBeNull()
  })

  it('renders every verdict the API can return with its own label and explanation', async () => {
    const labels = {
      ACCEPTED: 'Accepted', WRONG_ANSWER: 'Wrong answer', COMPILE_ERROR: 'Compile error', RUNTIME_ERROR: 'Runtime error', TIME_LIMIT: 'Time limit exceeded',
      MEMORY_LIMIT: 'Memory limit exceeded', OUTPUT_LIMIT: 'Output limit exceeded', INVALID_SUBMISSION: 'Source refused', INTERNAL_ERROR: 'Runner problem', CANCELLED: 'Cancelled',
    }
    for (const [verdict, label] of Object.entries(labels)) {
      submitCode.mockResolvedValue({ ...SUBMIT_OK, verdict, passed: 0, visibleTests: [], hiddenPassed: 0, feedback: '' })
      await loaded()
      fireEvent.click(submitButton())
      await screen.findByText('Official submission #42')
      expect(within(screen.getByTestId('outcome')).getByText(label), verdict).toBeTruthy()
      cleanup()
    }
  })

  it('shows compiler output and failing sample messages', async () => {
    runCode.mockResolvedValue({
      verdict: 'WRONG_ANSWER', passed: 1, total: 3, compilerOutput: '',
      tests: [{ name: 'Retries transient failures with doubling delays', status: 'FAIL', message: 'expected waits [100, 200] but got [100]' }],
      feedback: 'Some sample tests fail.',
    })
    await loaded()
    fireEvent.click(runButton())
    expect(await screen.findByText('expected waits [100, 200] but got [100]')).toBeTruthy()
    expect(screen.getByText('Failed')).toBeTruthy()
    runCode.mockResolvedValue({ verdict: 'COMPILE_ERROR', passed: 0, total: 3, tests: [], compilerOutput: "RetryExecutor.java:7: error: ';' expected", feedback: 'Your code did not compile.' })
    fireEvent.click(runButton())
    expect(await screen.findByText(/RetryExecutor.java:7: error/)).toBeTruthy()
    expect(screen.getByText('Compile error')).toBeTruthy()
  })

  it('never shows hidden test details even if the response carried extra properties', async () => {
    submitCode.mockResolvedValue({ ...SUBMIT_OK, verdict: 'WRONG_ANSWER', passed: 10, hiddenPassed: 7, hiddenTests: [{ name: 'SECRET-HIDDEN-NAME', input: 'SECRET-INPUT' }], expected: 'SECRET-EXPECTED' })
    await loaded()
    fireEvent.click(submitButton())
    await screen.findByText('Official submission #42')
    expect(document.body.textContent).not.toMatch(/SECRET/)
  })
})

describe('request lifecycle', () => {
  it('shows a pending state and disables Run, Submit and Reset while a request is in flight; a second click does nothing', async () => {
    const d = deferred()
    runCode.mockReturnValue(d.promise)
    await loaded()
    typeText('// x\n')
    fireEvent.click(runButton())
    fireEvent.click(runButton())
    fireEvent.click(submitButton())
    expect(runCode).toHaveBeenCalledTimes(1)
    expect(submitCode).not.toHaveBeenCalled()
    expect(runButton().disabled).toBe(true)
    expect(submitButton().disabled).toBe(true)
    expect(screen.getByRole('button', { name: 'Reset to starter code' }).disabled).toBe(true)
    expect(screen.getByRole('button', { name: 'Running sample tests…' })).toBeTruthy()
    expect(screen.getAllByRole('status').some((s) => /Running the sample tests/.test(s.textContent))).toBe(true)
    await act(async () => d.resolve(RUN_OK))
    expect(await screen.findByTestId('outcome')).toBeTruthy()
    expect(runButton().disabled).toBe(false)
    expect(submitButton().disabled).toBe(false)
  })

  it('a Submit in flight blocks a Run as well', async () => {
    const d = deferred()
    submitCode.mockReturnValue(d.promise)
    await loaded()
    fireEvent.click(submitButton())
    fireEvent.click(runButton())
    expect(submitCode).toHaveBeenCalledTimes(1)
    expect(runCode).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Submitting…' }).disabled).toBe(true)
    await act(async () => d.resolve(SUBMIT_OK))
    await screen.findByText('Official submission #42')
  })

  it('cancels the request when the learner leaves the page, without updating the unmounted page', async () => {
    const d = deferred()
    runCode.mockReturnValue(d.promise)
    await loaded()
    fireEvent.click(runButton())
    const signal = runCode.mock.calls[0][2]
    expect(signal.aborted).toBe(false)
    cleanup()
    expect(signal.aborted).toBe(true)
    await act(async () => d.reject(Object.assign(new Error('canceled'), { code: 'ERR_CANCELED', name: 'CanceledError', __CANCEL__: true })))
  })

  const failures = [
    [400, { detail: 'The source is larger than 65536 bytes' }, /refused this sample run: The source is larger than 65536 bytes/],
    [401, {}, /session has expired/],
    [404, {}, /no longer available/],
    [409, {}, /already have a run or submission in progress/],
    [429, {}, /runner is busy/],
    [503, {}, /Code execution is not available right now/],
  ]
  it.each(failures)('a %s answer shows a clear message, no verdict, keeps the code, and re-enables the buttons', async (status, data, pattern) => {
    runCode.mockRejectedValue(http(status, data))
    await loaded()
    typeText('// keep me\n')
    fireEvent.click(runButton())
    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toMatch(pattern)
    expect(screen.queryByTestId('outcome')).toBeNull()
    expect(editorText()).toBe(STARTER + '// keep me\n')
    expect(runButton().disabled).toBe(false)
    expect(submitButton().disabled).toBe(false)
  })

  it('a failed Submit does not refresh the history or progress and keeps the code unsaved', async () => {
    submitCode.mockRejectedValue(http(503))
    await loaded()
    await waitFor(() => expect(listSubmissions).toHaveBeenCalledTimes(1))
    typeText('// x\n')
    fireEvent.click(submitButton())
    expect((await screen.findByRole('alert')).textContent).toMatch(/Nothing was judged and nothing was recorded/)
    expect(listSubmissions).toHaveBeenCalledTimes(1)
    expect(getCodingProgress).toHaveBeenCalledTimes(1)
    expect(screen.getByText('Not submitted')).toBeTruthy()
  })

  it.each([
    ['a network error', { code: 'ERR_NETWORK' }],
    ['a timeout', { code: 'ECONNABORTED' }],
  ])('%s shows no verdict, says no result was received, and points to the history', async (_name, error) => {
    submitCode.mockRejectedValue(error)
    await loaded()
    fireEvent.click(submitButton())
    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toMatch(/No result was received, so none is shown/)
    expect(alert.textContent).toMatch(/submission history/)
    expect(screen.queryByTestId('outcome')).toBeNull()
    expect(screen.queryByText('Accepted')).toBeNull()
    expect(listSubmissions).toHaveBeenCalledTimes(1)
  })

  it('a 400 validation answer lists the field problems', async () => {
    submitCode.mockRejectedValue(http(400, { detail: 'Validation failed', errors: { sourceCode: 'size must be between 1 and 70000' } }))
    await loaded()
    fireEvent.click(submitButton())
    expect((await screen.findByRole('alert')).textContent).toMatch(/size must be between 1 and 70000/)
  })
})

describe('editor controls', () => {
  it('Reset restores the starter code after confirmation, and leaves it alone when declined', async () => {
    const confirm = vi.spyOn(window, 'confirm')
    await loaded()
    const reset = screen.getByRole('button', { name: 'Reset to starter code' })
    expect(reset.disabled).toBe(true) // nothing to reset yet
    typeText('// edits\n')
    expect(reset.disabled).toBe(false)

    confirm.mockReturnValueOnce(false)
    fireEvent.click(reset)
    expect(confirm).toHaveBeenCalledTimes(1)
    expect(editorText()).toBe(STARTER + '// edits\n')

    confirm.mockReturnValueOnce(true)
    fireEvent.click(reset)
    await waitFor(() => expect(editorText()).toBe(STARTER))
    expect(screen.queryByText('Not submitted')).toBeNull()
    confirm.mockRestore()
  })

  it('refuses to send code over the 64 KiB limit and says so; the buttons stay disabled', async () => {
    await loaded()
    replaceAll('x'.repeat(MAX_SOURCE_BYTES + 1))
    expect(screen.getByRole('alert').textContent).toMatch(/limit is 64 KiB/)
    expect(runButton().disabled).toBe(true)
    expect(submitButton().disabled).toBe(true)
    fireEvent.click(submitButton())
    expect(submitCode).not.toHaveBeenCalled()
    replaceAll('x'.repeat(MAX_SOURCE_BYTES))
    expect(runButton().disabled).toBe(false)
  })

  it('counts bytes, not characters, when checking the limit', async () => {
    await loaded()
    replaceAll('€'.repeat(Math.floor(MAX_SOURCE_BYTES / 3) + 1)) // 3 bytes each
    expect(runButton().disabled).toBe(true)
  })

  it('an empty editor cannot be run or submitted', async () => {
    await loaded()
    replaceAll('   \n')
    expect(runButton().disabled).toBe(true)
    expect(submitButton().disabled).toBe(true)
    expect(screen.getByText(/The editor is empty/)).toBeTruthy()
  })
})

describe('unsaved work warning', () => {
  it('asks the browser to confirm closing or reloading only while there is unsaved work', async () => {
    await loaded()
    const clean = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(clean)
    expect(clean.defaultPrevented).toBe(false)
    typeText('// x\n')
    const dirty = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(dirty)
    expect(dirty.defaultPrevented).toBe(true)
  })

  it('asks before an in-app link discards unsaved work, and honours a refusal', async () => {
    const confirm = vi.spyOn(window, 'confirm')
    await loaded()
    const back = screen.getByRole('link', { name: '← Coding practice' })
    fireEvent.click(back) // clean: no question
    expect(confirm).not.toHaveBeenCalled()
    await screen.findByText('catalogue page')
    cleanup()

    await loaded()
    typeText('// unsaved\n')
    confirm.mockReturnValueOnce(false)
    fireEvent.click(screen.getByRole('link', { name: '← Coding practice' }))
    expect(confirm).toHaveBeenCalledTimes(1)
    expect(screen.queryByText('catalogue page')).toBeNull()
    expect(editorText()).toBe(STARTER + '// unsaved\n')
    confirm.mockReturnValueOnce(true)
    fireEvent.click(screen.getByRole('link', { name: '← Coding practice' }))
    await screen.findByText('catalogue page')
    confirm.mockRestore()
  })

  it('does not ask after the work has been submitted', async () => {
    const confirm = vi.spyOn(window, 'confirm')
    await loaded()
    typeText('// x\n')
    fireEvent.click(submitButton())
    await screen.findByText('Official submission #42')
    fireEvent.click(screen.getByRole('link', { name: '← Coding practice' }))
    expect(confirm).not.toHaveBeenCalled()
    await screen.findByText('catalogue page')
    confirm.mockRestore()
  })
})

describe('layout and keyboard', () => {
  it('offers Problem, Code and Results tabs for narrow screens, and the panels follow the selection', async () => {
    await loaded()
    const tabs = screen.getByRole('tablist', { name: 'Workspace sections' })
    expect(within(tabs).getAllByRole('tab').map((t) => t.textContent)).toEqual(['Problem', 'Code', 'Results'])
    expect(tabs.className).toContain('lg:hidden') // on wide screens everything is visible at once
    const panel = (id) => document.getElementById(`panel-${id}`)
    expect(screen.getByRole('tab', { name: 'Problem' }).getAttribute('aria-selected')).toBe('true')
    expect(panel('problem').className).not.toMatch(/(^|\s)hidden(\s|$)/)
    expect(panel('code').className).toMatch(/(^|\s)hidden(\s|$)/)
    expect(panel('code').className).toContain('lg:block')
    fireEvent.click(screen.getByRole('tab', { name: 'Code' }))
    expect(panel('code').className).not.toMatch(/(^|\s)hidden(\s|$)/)
    expect(panel('problem').className).toMatch(/(^|\s)hidden(\s|$)/)
  })

  it('moves to the Results tab when a result arrives, and supports arrow keys, Home and End', async () => {
    await loaded()
    fireEvent.click(screen.getByRole('tab', { name: 'Code' }))
    fireEvent.click(runButton())
    await screen.findByTestId('outcome')
    expect(screen.getByRole('tab', { name: 'Results' }).getAttribute('aria-selected')).toBe('true')
    const tabs = screen.getByRole('tablist')
    fireEvent.keyDown(tabs, { key: 'ArrowRight' })
    expect(screen.getByRole('tab', { name: 'Problem' }).getAttribute('aria-selected')).toBe('true')
    fireEvent.keyDown(tabs, { key: 'End' })
    expect(screen.getByRole('tab', { name: 'Results' }).getAttribute('aria-selected')).toBe('true')
    fireEvent.keyDown(tabs, { key: 'Home' })
    expect(screen.getByRole('tab', { name: 'Problem' }).getAttribute('aria-selected')).toBe('true')
    fireEvent.keyDown(tabs, { key: 'ArrowLeft' })
    expect(screen.getByRole('tab', { name: 'Results' }).getAttribute('aria-selected')).toBe('true')
    // only the selected tab is in the tab order
    expect(screen.getByRole('tab', { name: 'Code' }).getAttribute('tabindex')).toBe('-1')
  })

  it('states what Run and Submit do next to their buttons, so the difference is not left to guesswork', async () => {
    await loaded()
    expect(runButton().getAttribute('aria-describedby')).toBe('run-help')
    expect(document.getElementById('run-help').textContent).toMatch(/Not an official attempt; your progress does not change/)
    expect(submitButton().getAttribute('aria-describedby')).toBe('submit-help')
    expect(document.getElementById('submit-help').textContent).toMatch(/every test, hidden ones included/)
    expect(screen.getByText(/Press Escape, then Tab/)).toBeTruthy()
  })

  it('shows history states: empty, then the list; and an error that does not break the page', async () => {
    await loaded()
    expect(await screen.findByText(/No official submissions yet/)).toBeTruthy()
    cleanup()
    listSubmissions.mockResolvedValue(HISTORY)
    await loaded()
    expect(await screen.findByText('#42')).toBeTruthy()
    expect(screen.getByText('12 of 12 passed')).toBeTruthy()
    expect(within(screen.getByRole('list', { name: 'Your submissions for this problem' })).getByText(/version 1/)).toBeTruthy()
    cleanup()
    listSubmissions.mockRejectedValue(http(500, { detail: 'history unavailable' }))
    await loaded()
    expect(await screen.findByText('history unavailable')).toBeTruthy()
    expect(editorText()).toBe(STARTER)
  })
})

describe('a scripted problem of the bank', () => {
  const CB_SLUG = 'circuit-breaker-state-machine'
  const CB_STARTER = 'public final class CircuitBreaker {\n    // TODO\n}\n'
  const CB = {
    ...PROBLEM,
    slug: CB_SLUG,
    title: 'Circuit breaker state machine',
    starterCode: CB_STARTER,
    examples: [{ title: 'Trips', input: 'new 2 100\nfail\nfail\nstate', output: '-\n-\n-\nOPEN', explanation: 'Two failures open it.' }],
    sampleTests: [{ name: 'Trips after the threshold', input: '1. new 2 100\n2. fail\n3. fail\n4. state', expected: '1. -\n2. -\n3. -\n4. OPEN' }],
    hiddenTestCount: 10,
  }

  it('names the class of ITS starter code, not the retry exercise', async () => {
    getCodingProblem.mockResolvedValue(CB)
    renderWorkspace(`/learn/coding/${CB_SLUG}`)
    await screen.findByRole('heading', { level: 1, name: 'Circuit breaker state machine' })
    await waitFor(() => expect(document.querySelector('.cm-editor')).not.toBeNull())
    const hint = screen.getByText(/Write the whole file, declaring only/i)
    expect(within(hint).getByText('CircuitBreaker')).toBeTruthy()
    expect(hint.textContent).not.toContain('RetryExecutor')
    expect(editorText()).toBe(CB_STARTER)
  })

  it('shows multi-line sample tests and examples with their line breaks preserved', async () => {
    getCodingProblem.mockResolvedValue(CB)
    renderWorkspace(`/learn/coding/${CB_SLUG}`)
    await screen.findByRole('heading', { level: 1, name: 'Circuit breaker state machine' })
    const input = screen.getByText(/1\. new 2 100/)
    expect(input.className).toContain('whitespace-pre-wrap')
    expect(input.textContent).toBe('1. new 2 100\n2. fail\n3. fail\n4. state')
    expect(screen.getByText(/4\. OPEN/).className).toContain('whitespace-pre-wrap')
    const example = screen.getByText((_, el) => el.tagName === 'DD' && el.textContent === 'new 2 100\nfail\nfail\nstate')
    expect(example.className).toContain('whitespace-pre-wrap')
  })

  it('still tells the learner how many hidden tests exist without showing them', async () => {
    getCodingProblem.mockResolvedValue(CB)
    renderWorkspace(`/learn/coding/${CB_SLUG}`)
    await screen.findByRole('heading', { level: 1, name: 'Circuit breaker state machine' })
    expect(screen.getByText(/10 hidden tests/)).toBeTruthy()
  })
})

describe('starterClassName', () => {
  it('reads the declared class, whatever the modifiers or the leading code', () => {
    expect(starterClassName('public final class OutboxRelay {\n}')).toBe('OutboxRelay')
    expect(starterClassName('import java.util.List;\n\npublic class Bulkhead<T> {\n}')).toBe('Bulkhead')
    expect(starterClassName('final class SagaCoordinator {}')).toBe('SagaCoordinator')
    expect(starterClassName('// public class Fake\npublic final class IdempotencyHandler {}')).toBe('IdempotencyHandler')
    expect(starterClassName('')).toBe('the class from the starter code')
    expect(starterClassName(undefined)).toBe('the class from the starter code')
  })
})
