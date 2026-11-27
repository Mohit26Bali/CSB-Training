import { cleanup, render, screen } from '@testing-library/react'
import axios from 'axios'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import { describeLoadError, describeRequestError } from './codingErrors'
import { OutcomePanel } from './components'
import { parseBlocks } from './markdown'
import SimpleMarkdown from './SimpleMarkdown'
import { VERDICTS, verdictInfo } from './verdicts'

afterEach(cleanup)

const http = (status, data = {}) => ({ response: { status, data } })

describe('describeRequestError', () => {
  it('maps each documented status to a clear message and never to a verdict', () => {
    const cases = {
      400: /refused this submission/,
      401: /session has expired/,
      404: /no longer available/,
      409: /already have a run or submission in progress/,
      429: /runner is busy/,
      503: /not available right now/,
    }
    for (const [status, pattern] of Object.entries(cases)) {
      const d = describeRequestError(http(Number(status), { detail: 'The source is empty' }), 'submit')
      expect(d.message, status).toMatch(pattern)
      expect(d.status).toBe(Number(status))
      expect(d.outcomeUnknown).toBe(false)
    }
  })

  it('shows the server reason for a 400, including validation messages', () => {
    expect(describeRequestError(http(400, { detail: 'The source is larger than 65536 bytes' }), 'run').message).toContain('larger than 65536 bytes')
    expect(describeRequestError(http(400, { detail: 'Validation failed', errors: { sourceCode: 'size must be between 1 and 70000' } }), 'submit').message).toContain(
      'size must be between 1 and 70000',
    )
  })

  it('says a sample run is a sample run and a submission a submission', () => {
    expect(describeRequestError(http(400, { detail: 'x' }), 'run').message).toContain('sample run')
    expect(describeRequestError(http(400, { detail: 'x' }), 'submit').message).toContain('submission')
  })

  it('treats a network error and a timeout as an unknown outcome and points to the history after a submit', () => {
    const network = describeRequestError({ code: 'ERR_NETWORK' }, 'submit')
    expect(network.kind).toBe('network')
    expect(network.outcomeUnknown).toBe(true)
    expect(network.message).toMatch(/No result was received/)
    expect(network.message).toMatch(/submission history/)
    const timeout = describeRequestError({ code: 'ECONNABORTED' }, 'submit')
    expect(timeout.kind).toBe('timeout')
    expect(timeout.outcomeUnknown).toBe(true)
    expect(describeRequestError({ code: 'ECONNABORTED' }, 'run').message).not.toMatch(/history/)
  })

  it('an unexpected status is an error with no result', () => {
    const d = describeRequestError(http(500), 'run')
    expect(d.kind).toBe('error')
    expect(d.message).toMatch(/No result was received/)
  })

  it('a deliberate cancellation (leaving the page) is not reported', () => {
    const cancelled = new axios.CanceledError('canceled')
    expect(describeRequestError(cancelled, 'run')).toBeNull()
  })

  it('describeLoadError covers unreachable, 404, 503 and server detail', () => {
    expect(describeLoadError({}).message).toMatch(/Cannot reach the server/)
    expect(describeLoadError(http(404)).status).toBe(404)
    expect(describeLoadError(http(503)).message).toMatch(/not available/)
    expect(describeLoadError(http(500, { detail: 'boom' })).message).toBe('boom')
  })
})

describe('verdict presentation', () => {
  const all = ['ACCEPTED', 'WRONG_ANSWER', 'COMPILE_ERROR', 'RUNTIME_ERROR', 'TIME_LIMIT', 'MEMORY_LIMIT', 'OUTPUT_LIMIT', 'INVALID_SUBMISSION', 'INTERNAL_ERROR', 'CANCELLED']

  it('has a label, a tone and an explanation for every verdict the API can return', () => {
    for (const v of all) {
      const info = verdictInfo(v)
      expect(info.label, v).toBeTruthy()
      expect(info.meaning.length, v).toBeGreaterThan(20)
      expect(info.tone, v).toBeTruthy()
    }
    expect(Object.keys(VERDICTS).sort()).toEqual([...all].sort())
  })

  it('only ACCEPTED looks like a success', () => {
    for (const v of all.filter((x) => x !== 'ACCEPTED')) expect(verdictInfo(v).tone).not.toBe('success')
  })

  it('an unknown verdict is shown as itself and is never a success', () => {
    const info = verdictInfo('SOMETHING_NEW')
    expect(info.label).toBe('SOMETHING_NEW')
    expect(info.tone).toBe('neutral')
  })

  it('claims no runtime or memory figure', () => {
    for (const v of all) expect(verdictInfo(v).meaning).not.toMatch(/\b\d+\s?(ms|MB|KB|seconds)\b/i)
  })
})

describe('outcome panel', () => {
  const render_ = (props) => render(<MemoryRouter><OutcomePanel {...props} /></MemoryRouter>)

  it('a passing SAMPLE RUN is never labelled Accepted and says it is not official', () => {
    render_({ kind: 'run', data: { verdict: 'ACCEPTED', passed: 3, total: 3, tests: [{ name: 'a', status: 'PASS', message: null }], compilerOutput: '', feedback: 'All sample tests passed.' } })
    expect(screen.queryByText('Accepted')).toBeNull()
    expect(screen.getByText('All sample tests passed')).toBeTruthy()
    expect(screen.getByText((_, el) => el.tagName === 'P' && /not an official result/i.test(el.textContent))).toBeTruthy()
    expect(screen.getByText(/Sample run, not an official submission/)).toBeTruthy()
    expect(screen.getByTestId('counts').textContent).toBe('3 of 3 sample tests passed')
  })

  it('an official ACCEPTED submission is labelled Accepted with the aggregate hidden counts only', () => {
    render_({
      kind: 'submit',
      data: { id: 7, problemVersion: 1, verdict: 'ACCEPTED', passed: 12, total: 12, visibleTests: [{ name: 'v', status: 'PASS', message: null }], hiddenTotal: 9, hiddenPassed: 9, compilerOutput: '', feedback: 'All tests passed.', submittedAt: '2026-11-04T10:00:00Z' },
    })
    expect(screen.getByText('Accepted')).toBeTruthy()
    expect(screen.getByText('Official submission #7')).toBeTruthy()
    expect(screen.getByTestId('counts').textContent).toBe('12 of 12 tests passed (hidden tests: 9 of 9 passed)')
    expect(screen.getByText(/never shown/)).toBeTruthy()
  })

  it('renders only the fields the API defines: extra properties never reach the page', () => {
    render_({
      kind: 'submit',
      data: {
        id: 1, problemVersion: 1, verdict: 'WRONG_ANSWER', passed: 1, total: 2, visibleTests: [{ name: 'shown', status: 'FAIL', message: 'a message' }], hiddenTotal: 1, hiddenPassed: 0, compilerOutput: '', feedback: 'f',
        hiddenTests: [{ name: 'SECRET-HIDDEN-NAME', input: 'SECRET-INPUT', expected: 'SECRET-EXPECTED' }], referenceSolution: 'SECRET-SOLUTION', expected: 'SECRET-EXPECTED',
      },
    })
    expect(document.body.textContent).not.toMatch(/SECRET/)
    expect(screen.getByText('a message')).toBeTruthy()
  })

  it('shows the compiler output for a compile error and no test list when there is none', () => {
    render_({ kind: 'submit', data: { id: 2, problemVersion: 1, verdict: 'COMPILE_ERROR', passed: 0, total: 12, visibleTests: [], hiddenTotal: 9, hiddenPassed: 0, compilerOutput: "RetryExecutor.java:7: error: ';' expected", feedback: 'Your code did not compile.' } })
    expect(screen.getByText('Compile error')).toBeTruthy()
    expect(screen.getByText(/RetryExecutor.java:7/)).toBeTruthy()
  })
})

describe('markdown for statements', () => {
  it('splits headings, paragraphs, lists and code fences', () => {
    const blocks = parseBlocks('## Title\n\nSome *text* with `code`.\n\n1. one\n2. two\n\n* a\n* b\n\n```java\nint x;\n```\n')
    expect(blocks.map((b) => b.type)).toEqual(['h3', 'p', 'ol', 'ul', 'code'])
    expect(blocks[2].items).toEqual(['one', 'two'])
    expect(blocks[4].text).toBe('int x;')
  })

  it('renders inline code, bold and italics as elements, and HTML as plain text', () => {
    const { container } = render(<SimpleMarkdown text={'Use **bold**, *italic* and `min(a, b)` <script>alert(1)</script> <img src=x onerror=alert(1)>'} />)
    expect(container.querySelector('strong').textContent).toBe('bold')
    expect(container.querySelector('em').textContent).toBe('italic')
    expect(container.querySelector('code').textContent).toBe('min(a, b)')
    expect(container.querySelector('script')).toBeNull()
    expect(container.querySelector('img')).toBeNull()
    expect(container.textContent).toContain('<script>alert(1)</script>')
  })

  it('does not treat a multiplication sign as emphasis', () => {
    const { container } = render(<SimpleMarkdown text={'a * b * c'} />)
    expect(container.querySelector('em')).toBeNull()
  })
})
