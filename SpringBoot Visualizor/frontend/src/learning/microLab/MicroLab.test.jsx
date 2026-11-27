import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../../auth/useAuth', () => ({ useAuth: () => ({ user: { id: 7, username: 'sam', role: 'STUDENT' } }) }))
vi.mock('../../components/AppHeader', () => ({ default: () => null }))
vi.mock('../../api/arenaApi', () => ({ listTopics: vi.fn() }))
vi.mock('../api/labApi', () => ({ getMeta: vi.fn(), getCodeReferences: vi.fn() }))
vi.mock('../api/labMicroApi', () => ({
  getMicroMeta: vi.fn(), getMicroExperiments: vi.fn(), createMicroRun: vi.fn(), executeMicroRun: vi.fn(), resetMicroRun: vi.fn(),
}))

import { listTopics } from '../../api/arenaApi'
import { getCodeReferences, getMeta } from '../api/labApi'
import { createMicroRun, executeMicroRun, getMicroExperiments, getMicroMeta, resetMicroRun } from '../api/labMicroApi'
import { TOPICS } from '../scenarios/microLab.sim'
import MicroLabPage from './MicroLabPage'
import MicroRunView from './MicroRunView'
import { BreakerStrip, BulkheadStrip, CallSequence, EnvironmentBanner, MechanismChip, MechanismLegend, RealityPanel, SagaLogTable, SpanTable } from './MicroParts'
import WhatIfForm from './WhatIfForm'

// React Flow measures its container; jsdom has no layout engine, so provide the browser APIs it expects.
beforeAll(() => {
  globalThis.ResizeObserver ??= class {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
  globalThis.DOMMatrixReadOnly ??= class {
    constructor() {
      this.m22 = 1
    }
  }
})
const setWide = (wide) => {
  globalThis.matchMedia = (query) => ({ matches: wide, media: query, addEventListener() {}, removeEventListener() {} })
}
beforeEach(() => {
  setWide(true)
  window.history.replaceState(null, '', '/')
  Element.prototype.scrollIntoView = vi.fn()
})
afterEach(cleanup)

const call = (seq, from, to, operation, outcome = 'SUCCESS', kind = 'SYNC', extra = {}) => ({
  seq, from, to, operation, outcome, kind, code: null, simulatedMillis: 18, measuredMicros: 40, handlerInvoked: true, spanId: `span-${seq}`, parentSpanId: null,
  correlationId: 'corr-1', instance: null, ...extra,
})
const step = (id, extra = {}) => ({
  id, title: `Step ${id}`, nodes: [], edgeFrom: null, edgeTo: null, direction: 'REQUEST', provenance: 'CAPTURED',
  explanation: `Explanation of ${id} that is long enough to be valid.`, evidence: [], sql: [], codeRefId: null, ...extra,
})
const ev = (label, value) => ({ label, value })

const calls = [
  call(1, 'client', 'inventory', 'reserve'),
  call(2, 'order', 'inventory', 'release', 'SUCCESS', 'SYNC', { spanId: 'span-2' }),
  call(3, 'broker', 'notification', 'onOrderPlaced', 'UNAVAILABLE', 'EVENT', { spanId: 'span-3' }),
]
const result = {
  environment: 'Simulated in-process services: one Java process, no network, no message broker, no separate deployments.',
  errorCode: null, durationMillis: 12, sqlCount: 1, cleanupRowsRemoved: 4, limitations: ['A limitation.'],
  sql: [], calls,
  spans: [
    { spanId: 'span-1', parentSpanId: null, correlationId: 'corr-1', service: 'order', operation: 'placeOrder', status: 'SUCCESS', startMillis: 0, simulatedMillis: 47, measuredMicros: 90, correlationPropagated: true, kind: 'SYNC' },
    { spanId: 'span-2', parentSpanId: 'span-1', correlationId: 'corr-1', service: 'inventory', operation: 'reserve', status: 'SUCCESS', startMillis: 5, simulatedMillis: 18, measuredMicros: 40, correlationPropagated: true, kind: 'SYNC' },
  ],
  steps: [
    step('reserve', {
      nodes: ['client', 'inventory', 'inventory-db'], edgeFrom: 'client', edgeTo: 'inventory',
      evidence: [ev('Service call 1', 'x'), ev('Caller sees', 'SUCCESS'), ev('Breaker state', 'CLOSED'), ev('Permits available to Payment', '0'),
        ev('Other database calls observed', 'inventory: setAutoCommit(false) → inventory: commit()')],
      sql: [{ index: 1, sql: 'update micro_lab_stock set available = available - ? where run_key = ?', phase: 'SERVICE DB', trigger: 'inventory: executeUpdate affected 1 row(s)' }],
    }),
    step('compensate', {
      nodes: ['order', 'inventory'], edgeFrom: 'order', edgeTo: 'inventory',
      evidence: [ev('Service call 2', 'x'), ev('Breaker state', 'OPEN'), ev('Saga log 4 COMPENSATE_RESERVE_STOCK', 'COMPENSATED (reversed by a new business operation)')],
    }),
    step('deliver', { nodes: ['broker', 'notification'], edgeFrom: 'broker', edgeTo: 'notification', evidence: [ev('Service call 3', 'x')] }),
    step('conclusion', { provenance: 'DERIVED', nodes: ['inventory'], evidence: [ev('Why', 'derived from the above')] }),
    step('limits', { provenance: 'CONCEPTUAL', nodes: ['client'] }),
  ],
}
const info = { id: 'saga-orchestrated', title: 'Saga by orchestration', topic: 'saga', diagram: 'services', whatIfs: [] }

describe('MicroParts', () => {
  it('names the six mechanisms in the legend, each with a line sample and its own wording', () => {
    render(<MechanismLegend />)
    const legend = screen.getByTestId('mechanism-legend')
    for (const text of ['Synchronous call', 'Event on the simulated channel', 'Local database transaction', 'Outbox relay', 'Compensation: a new business operation', 'Refused locally']) {
      expect(legend.textContent).toContain(text)
    }
    expect(legend.querySelectorAll('svg')).toHaveLength(6)
    expect(legend.textContent).toContain('not a database rollback')
  })

  it('writes every mechanism as text with an icon on its chip, so colour is never the only cue', () => {
    for (const [m, text] of [['SYNC', 'sync call'], ['EVENT', 'event'], ['LOCAL_TX', 'local tx'], ['OUTBOX', 'outbox relay'], ['COMPENSATION', 'compensation'], ['REFUSED', 'refused locally']]) {
      const { unmount } = render(<MechanismChip mechanism={m} />)
      expect(screen.getByTestId('mechanism-chip').textContent).toContain(text)
      expect(screen.getByTestId('mechanism-chip').getAttribute('data-mechanism')).toBe(m)
      unmount()
    }
  })

  it('states what is real and what is simulated', () => {
    render(<RealityPanel defaultOpen />)
    const panel = screen.getByTestId('reality-panel')
    expect(panel.textContent).toContain('Real (observed)')
    expect(panel.textContent).toContain('Simulated (a model)')
    expect(panel.textContent).toContain('not Kafka or RabbitMQ')
    expect(panel.textContent).toContain('one JVM')
  })

  it('shows the server\'s environment statement as simulated infrastructure', () => {
    render(<EnvironmentBanner text="no network" />)
    expect(screen.getByTestId('environment-banner').textContent).toContain('Simulated infrastructure')
  })

  it('lists the calls of a step with mechanism, outcome and whether the handler ran, and the local transactions', () => {
    const steps = result.steps
    const calls2 = [calls[0], calls[1]]
    render(<CallSequence step={{ ...steps[0], calls: calls2.slice(0, 1) }} />)
    const rows = screen.getAllByTestId('call-row')
    expect(rows).toHaveLength(1)
    expect(rows[0].textContent).toContain('client → inventory.reserve')
    expect(rows[0].textContent).toContain('sync call')
    expect(rows[0].textContent).toContain('SUCCESS')
    expect(rows[0].textContent).toContain('handler ran')
    expect(screen.getByTestId('local-transactions').textContent).toContain('inventory: committed')
  })

  it('marks a failed event call and a compensation distinctly', () => {
    render(<CallSequence step={{ ...step('s'), calls: [calls[1], calls[2]] }} />)
    const rows = screen.getAllByTestId('call-row')
    expect(rows[0].textContent).toContain('compensation')
    expect(rows[1].textContent).toContain('event')
    expect(rows[1].textContent).toContain('UNAVAILABLE')
  })

  it('shows the lines of a conceptual step as simulated text, with no call', () => {
    render(<CallSequence step={{ ...step('c', { provenance: 'CONCEPTUAL' }), drawn: [{ id: 'order->broker', mechanism: 'EVENT', labels: ['EVENT'] }] }} />)
    expect(screen.getByTestId('concept-lines').textContent).toContain('SIMULATED')
    expect(screen.getByTestId('concept-lines').textContent).toContain('order → broker')
    expect(screen.getByTestId('concept-lines').textContent).toContain('nothing was called or observed')
  })

  it('marks the current breaker state with text and exposes it to assistive technology', () => {
    render(<BreakerStrip steps={result.steps} index={1} />)
    const strip = screen.getByTestId('breaker-strip')
    expect(strip.getAttribute('aria-label')).toBe('Circuit breaker state: OPEN')
    expect(strip.querySelector('[aria-current="step"]').textContent).toContain('OPEN')
    expect(strip.querySelectorAll('[aria-current="step"]')).toHaveLength(1)
    expect(within(strip).getByText('CLOSED').getAttribute('aria-current')).toBeNull()
  })

  it('shows free permits, flags an empty pool, and shows nothing when the run has none', () => {
    const { rerender } = render(<BulkheadStrip steps={result.steps} index={0} />)
    expect(screen.getByTestId('bulkhead-strip').textContent).toContain('payment permits free')
    expect(screen.getByTestId('bulkhead-strip').textContent).toContain('Semaphore')
    rerender(<BulkheadStrip steps={[step('x')]} index={0} />)
    expect(screen.queryByTestId('bulkhead-strip')).toBeNull()
  })

  it('tabulates the saga log and says a compensation is not a rollback', () => {
    render(<SagaLogTable step={result.steps[1]} />)
    const table = screen.getByTestId('saga-log')
    expect(table.textContent).toContain('COMPENSATE_RESERVE_STOCK')
    expect(table.textContent).toContain('COMPENSATED')
    expect(table.textContent).toContain('not a database rollback')
    expect(table.querySelector('caption')).toBeTruthy()
  })

  it('keeps simulated and measured times in separately labelled columns, and flags a correlation id that was not passed on', () => {
    const spans = [...result.spans, { spanId: 'span-3', parentSpanId: null, correlationId: 'uncorrelated-1', service: 'user', operation: 'getUser', status: 'SUCCESS', startMillis: 1, simulatedMillis: 12, measuredMicros: 30, correlationPropagated: false, kind: 'SYNC' }]
    render(<SpanTable spans={spans} />)
    const table = screen.getByTestId('span-table')
    expect(within(table).getByText('Simulated ms')).toBeTruthy()
    expect(within(table).getByText('Measured µs')).toBeTruthy()
    expect(table.textContent).toContain('(not passed on)')
    expect(table.textContent).toContain('not over a network')
    const rows = within(table).getAllByTestId('span-row')
    expect(rows).toHaveLength(3)
    expect(rows[1].textContent).toContain('└ inventory.reserve')
  })
})

describe('WhatIfForm', () => {
  const options = [
    { key: 'callTimeoutMillis', label: 'Caller timeout', kind: 'INT', defaultValue: '100', min: 50, max: 500, choices: [] },
    { key: 'permits', label: 'Permits', kind: 'CHOICE', defaultValue: 'per-service', min: null, max: null, choices: ['per-service', 'shared'] },
    { key: 'useKey', label: 'Use a key', kind: 'BOOLEAN', defaultValue: 'true', min: null, max: null, choices: [] },
  ]
  const values = { callTimeoutMillis: '100', permits: 'per-service', useKey: 'true' }

  it('shows one labelled control per server option and the request field each one fills', () => {
    render(<WhatIfForm experimentId="x" options={options} values={values} onChange={() => {}} />)
    expect(screen.getByLabelText('Caller timeout').getAttribute('min')).toBe('50')
    expect(screen.getByLabelText('Caller timeout').getAttribute('max')).toBe('500')
    expect(screen.getByLabelText('Permits').querySelectorAll('option')).toHaveLength(2)
    expect(screen.getByLabelText('Use a key').value).toBe('true')
    expect(screen.getByTestId('whatif-x').textContent).toContain('Sent as callTimeoutMillis')
  })

  it('reports changes with the whole values object, as strings', () => {
    const onChange = vi.fn()
    render(<WhatIfForm experimentId="x" options={options} values={values} onChange={onChange} />)
    fireEvent.change(screen.getByLabelText('Permits'), { target: { value: 'shared' } })
    expect(onChange).toHaveBeenCalledWith({ ...values, permits: 'shared' })
  })

  it('says why a value is not allowed, to assistive technology too', () => {
    render(<WhatIfForm experimentId="x" options={options} values={{ ...values, callTimeoutMillis: '5' }} onChange={() => {}} />)
    expect(screen.getByRole('alert').textContent).toBe('Enter a number from 50 to 500.')
    expect(screen.getByLabelText('Caller timeout').getAttribute('aria-invalid')).toBe('true')
  })

  it('says so when an experiment has no controls, and can be disabled while a run is in progress', () => {
    const { rerender } = render(<WhatIfForm experimentId="x" options={[]} values={{}} onChange={() => {}} />)
    expect(screen.getByText(/no What-If controls/)).toBeTruthy()
    rerender(<WhatIfForm experimentId="x" options={options} values={values} onChange={() => {}} disabled />)
    expect(screen.getByTestId('whatif-x').disabled).toBe(true)
  })
})

describe('MicroRunView', () => {
  it('renders the diagram with the call list, the banner, a provenance badge per step and a mechanism for each call', () => {
    render(<MicroRunView info={info} result={result} runKey="k1" codeRefs={{}} />)
    expect(screen.getByTestId('environment-banner').textContent).toContain('no message broker')
    expect(screen.getByTestId('node-inventory')).toBeTruthy()
    expect(screen.getByTestId('node-broker').textContent).toContain('simulated')
    expect(screen.getByTestId('headline').textContent).toContain('SUCCESS')
    expect(screen.getAllByTestId('call-row')[0].textContent).toContain('sync call')
    expect(screen.getByTestId('provenance-summary').textContent).toContain('3 live, 1 inferred, 1 simulated')
    expect(screen.getByTestId('mechanism-legend')).toBeTruthy()
  })

  it('follows the step: breaker state, permits, the compensation call and the saga log change as the learner steps', () => {
    render(<MicroRunView info={info} result={result} runKey="k1" codeRefs={{}} />)
    expect(screen.getByTestId('breaker-strip').getAttribute('aria-label')).toContain('CLOSED')
    expect(screen.getByTestId('bulkhead-strip')).toBeTruthy()
    expect(screen.queryByTestId('saga-log')).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'Next step' }))
    expect(screen.getByTestId('breaker-strip').getAttribute('aria-label')).toContain('OPEN')
    expect(screen.getByTestId('saga-log').textContent).toContain('COMPENSATED')
    expect(screen.getAllByTestId('call-row')[0].textContent).toContain('compensation')
    fireEvent.click(screen.getByRole('button', { name: 'Next step' }))
    expect(screen.getAllByTestId('call-row')[0].textContent).toContain('UNAVAILABLE')
    expect(screen.getByTestId('node-notification').textContent).toContain('DOWN')
  })

  it('marks the badge of each kind of step: LIVE, INFERRED and SIMULATED', () => {
    render(<MicroRunView info={info} result={result} runKey="k1" codeRefs={{}} />)
    const label = () => screen.getAllByTestId('mode-badge').map((b) => b.textContent)
    expect(label().some((t) => t.includes('LIVE'))).toBe(true)
    fireEvent.click(screen.getByRole('button', { name: 'Next step' }))
    fireEvent.click(screen.getByRole('button', { name: 'Next step' }))
    fireEvent.click(screen.getByRole('button', { name: 'Next step' }))
    expect(label().some((t) => t.includes('INFERRED'))).toBe(true)
    fireEvent.click(screen.getByRole('button', { name: 'Next step' }))
    expect(label().some((t) => t.includes('SIMULATED'))).toBe(true)
  })

  it('shows the SQL of a step as run by a service and the trace of the run', () => {
    render(<MicroRunView info={info} result={result} runKey="k1" codeRefs={{}} />)
    expect(screen.getByTestId('sql-block').textContent).toContain('update micro_lab_stock')
    expect(screen.getByTestId('sql-block').textContent).toContain('run by a service on its own table')
    expect(screen.getByTestId('span-table')).toBeTruthy()
  })

  it('replaces the diagram with a vertical list of calls on a phone', () => {
    setWide(false)
    render(<MicroRunView info={info} result={result} runKey="k2" codeRefs={{}} />)
    expect(screen.getByTestId('call-sequence')).toBeTruthy()
    expect(document.querySelector('.react-flow')).toBeNull()
    expect(screen.getAllByTestId('call-row')).toHaveLength(1)
  })

  it('has keyboard control of the walkthrough: arrow keys move through the steps', () => {
    render(<MicroRunView info={info} result={result} runKey="k1" codeRefs={{}} />)
    const region = screen.getByRole('region', { name: /Live walkthrough/ })
    fireEvent.keyDown(region, { key: 'ArrowRight' })
    expect(screen.getByTestId('breaker-strip').getAttribute('aria-label')).toContain('OPEN')
    fireEvent.keyDown(region, { key: 'ArrowLeft' })
    expect(screen.getByTestId('breaker-strip').getAttribute('aria-label')).toContain('CLOSED')
  })
})

// ------------------------------------------------------------------------------------------------------------------------------- the page

const experiments = [
  { id: 'comm-sync-order-calls', topic: 'communication', title: 'Synchronous calls', question: 'Q1?', claim: 'C1.', diagram: 'services', whatIfs: [] },
  { id: 'res-bulkhead', topic: 'resilience', title: 'Bulkheads', question: 'Q2?', claim: 'C2.', diagram: 'services',
    whatIfs: [{ key: 'permits', label: 'Permits', kind: 'CHOICE', defaultValue: 'per-service', min: null, max: null, choices: ['per-service', 'shared'] }] },
  { id: 'res-retry-backoff', topic: 'resilience', title: 'Retry', question: 'Q3?', claim: 'C3.', diagram: 'services',
    whatIfs: [{ key: 'maxAttempts', label: 'Attempts', kind: 'INT', defaultValue: '3', min: 1, max: 5, choices: [] }] },
]
const meta = {
  environment: 'x', limitations: ['Server limitation.'], limits: {},
  services: [{ id: 'order', title: 'Order Service', responsibility: 'Orders.', ownedTables: ['micro_lab_orders'], databaseNode: 'order-db' }, { id: 'gateway', title: 'API Gateway', responsibility: 'Edge.', ownedTables: [], databaseNode: null }],
}
const view = (state, withResult = true) => ({ runId: 'a'.repeat(32), experimentId: 'res-bulkhead', state, whatIf: { permits: 'shared' }, createdAt: 't', updatedAt: `u-${state}-${Math.random()}`, ...(withResult ? { result } : {}) })

function renderPage() {
  return render(<MemoryRouter><MicroLabPage /></MemoryRouter>)
}
const stageBar = () => screen.getByRole('navigation', { name: 'Lesson stages' })
const goTo = (name) => fireEvent.click(within(stageBar()).getByRole('button', { name }))

beforeEach(() => {
  vi.clearAllMocks()
  getMeta.mockResolvedValue({ versions: { java: '17' }, settings: {} })
  getCodeReferences.mockResolvedValue({})
  getMicroMeta.mockResolvedValue(meta)
  getMicroExperiments.mockResolvedValue(experiments)
  listTopics.mockResolvedValue([{ slug: 'microservices', activeQuestionCount: 0 }])
})

describe('MicroLabPage', () => {
  it('opens on Understand with the first topic, its 5W1H, three levels, mistakes and what to look for', async () => {
    renderPage()
    expect(screen.getByRole('tab', { name: TOPICS[0].title }).getAttribute('aria-selected')).toBe('true')
    for (const key of ['what', 'why', 'who', 'where', 'when', 'how']) expect(screen.getByTestId(`5w1h-${key}`)).toBeTruthy()
    expect(screen.getByTestId('level-text').textContent).toBe(TOPICS[0].levels.beginner)
    fireEvent.click(screen.getByRole('radio', { name: 'Advanced' }))
    expect(screen.getByTestId('level-text').textContent).toBe(TOPICS[0].levels.advanced)
    expect(screen.getByTestId('mistakes').children).toHaveLength(TOPICS[0].mistakes.length)
    expect(screen.getByTestId('observations').children.length).toBeGreaterThanOrEqual(3)
    expect(screen.getByTestId('reality-panel')).toBeTruthy()
    await act(async () => {})
  })

  it('moves between the eight topics with the arrow keys, Home and End', async () => {
    renderPage()
    const first = screen.getByRole('tab', { name: TOPICS[0].title })
    expect(screen.getAllByRole('tab')).toHaveLength(8)
    fireEvent.keyDown(first, { key: 'ArrowRight' })
    expect(screen.getByRole('tab', { name: TOPICS[1].title }).getAttribute('aria-selected')).toBe('true')
    expect(document.activeElement).toBe(screen.getByRole('tab', { name: TOPICS[1].title }))
    fireEvent.keyDown(document.activeElement, { key: 'End' })
    expect(screen.getByRole('tab', { name: TOPICS[7].title }).getAttribute('aria-selected')).toBe('true')
    fireEvent.keyDown(document.activeElement, { key: 'ArrowRight' })
    expect(screen.getByRole('tab', { name: TOPICS[0].title }).getAttribute('aria-selected')).toBe('true')
    fireEvent.keyDown(document.activeElement, { key: 'Home' })
    expect(screen.getByRole('tab', { name: TOPICS[0].title }).getAttribute('aria-selected')).toBe('true')
    await act(async () => {})
  })

  it('shows a SIMULATED walkthrough on the Visualize stage', async () => {
    renderPage()
    await act(async () => {})
    goTo(/Visualize/)
    expect(screen.getByRole('region', { name: /Simulated walkthrough/ })).toBeTruthy()
    expect(screen.getAllByTestId('mode-badge').every((b) => b.textContent.includes('SIMULATED'))).toBe(true)
  })

  it('lists the experiments of the chosen topic, each with What-If controls that match the server options', async () => {
    renderPage()
    await act(async () => {})
    goTo(/Experiment/)
    fireEvent.click(screen.getByRole('tab', { name: TOPICS.find((t) => t.id === 'resilience').title }))
    const list = screen.getByTestId('experiment-list')
    expect(within(list).getAllByRole('listitem').filter((li) => li.dataset.testid?.startsWith('experiment-'))).toHaveLength(2)
    expect(screen.getByTestId('experiment-res-bulkhead').textContent).toContain('LIVE')
    expect(screen.getByTestId('whatif-res-bulkhead').textContent).toContain('Sent as permits')
    expect(screen.queryByTestId('experiment-comm-sync-order-calls')).toBeNull()
  })

  it('does not let an out-of-range What-If value be run', async () => {
    renderPage()
    await act(async () => {})
    goTo(/Experiment/)
    fireEvent.click(screen.getByRole('tab', { name: TOPICS.find((t) => t.id === 'resilience').title }))
    const card = screen.getByTestId('experiment-res-retry-backoff')
    fireEvent.change(within(card).getByLabelText('Attempts'), { target: { value: '9' } })
    expect(within(card).getByRole('button', { name: /Run LIVE experiment/ }).disabled).toBe(true)
    expect(within(card).getByRole('alert').textContent).toContain('1 to 5')
    fireEvent.change(within(card).getByLabelText('Attempts'), { target: { value: '4' } })
    expect(within(card).getByRole('button', { name: /Run LIVE experiment/ }).disabled).toBe(false)
  })

  it('creates a run with the chosen What-If values, executes it, and shows it on the Observe stage', async () => {
    createMicroRun.mockResolvedValue(view('CREATED', false))
    executeMicroRun.mockResolvedValue(view('COMPLETED'))
    renderPage()
    await act(async () => {})
    goTo(/Experiment/)
    fireEvent.click(screen.getByRole('tab', { name: TOPICS.find((t) => t.id === 'resilience').title }))
    const card = screen.getByTestId('experiment-res-bulkhead')
    fireEvent.change(within(card).getByLabelText('Permits'), { target: { value: 'shared' } })
    fireEvent.click(within(card).getByRole('button', { name: /Run LIVE experiment/ }))
    await waitFor(() => expect(screen.getByTestId('run-status')).toBeTruthy())
    expect(createMicroRun).toHaveBeenCalledWith('res-bulkhead', { permits: 'shared' })
    expect(executeMicroRun).toHaveBeenCalledWith('a'.repeat(32))
    expect(screen.getByTestId('run-status').textContent).toContain('COMPLETED')
    expect(screen.getByTestId('run-status').textContent).toContain('cleanup removed 4 lab rows')
    expect(screen.getByTestId('run-whatif').textContent).toContain('permits = shared')
    expect(screen.getByTestId('environment-banner')).toBeTruthy()
  })

  it('runs the same run again by resetting it first, and can reset without running', async () => {
    createMicroRun.mockResolvedValue(view('CREATED', false))
    executeMicroRun.mockResolvedValue(view('COMPLETED'))
    resetMicroRun.mockResolvedValue(view('CREATED', false))
    renderPage()
    await act(async () => {})
    goTo(/Experiment/)
    fireEvent.click(screen.getByRole('tab', { name: TOPICS.find((t) => t.id === 'resilience').title }))
    fireEvent.click(within(screen.getByTestId('experiment-res-bulkhead')).getByRole('button', { name: /Run LIVE experiment/ }))
    await waitFor(() => expect(screen.getByTestId('run-controls')).toBeTruthy())

    fireEvent.click(within(screen.getByTestId('run-controls')).getByRole('button', { name: 'Reset only' }))
    await waitFor(() => expect(screen.getByTestId('run-status').textContent).toContain('CREATED'))
    expect(resetMicroRun).toHaveBeenCalledWith('a'.repeat(32))
    expect(screen.getByText(/result was discarded/)).toBeTruthy()

    fireEvent.click(within(screen.getByTestId('run-controls')).getByRole('button', { name: 'Execute this run' }))
    await waitFor(() => expect(screen.getByTestId('run-status').textContent).toContain('COMPLETED'))

    fireEvent.click(within(screen.getByTestId('run-controls')).getByRole('button', { name: 'Reset and run again' }))
    await waitFor(() => expect(resetMicroRun).toHaveBeenCalledTimes(2))
    expect(executeMicroRun).toHaveBeenCalledTimes(3)
  })

  it('shows the server\'s refusal when a run cannot be created or executed', async () => {
    createMicroRun.mockRejectedValue({ response: { status: 409, data: { detail: 'You already have 20 runs that are not finished.' } } })
    renderPage()
    await act(async () => {})
    goTo(/Experiment/)
    fireEvent.click(screen.getByRole('tab', { name: TOPICS.find((t) => t.id === 'resilience').title }))
    fireEvent.click(within(screen.getByTestId('experiment-res-bulkhead')).getByRole('button', { name: /Run LIVE experiment/ }))
    await waitFor(() => expect(screen.getAllByRole('alert').some((a) => a.textContent.includes('20 runs'))).toBe(true))
    expect(executeMicroRun).not.toHaveBeenCalled()
  })

  it('tells the learner when a run ended with an error but still shows what was recorded', async () => {
    createMicroRun.mockResolvedValue(view('CREATED', false))
    executeMicroRun.mockResolvedValue({ ...view('FAILED'), result: { ...result, errorCode: 'IllegalStateException' } })
    renderPage()
    await act(async () => {})
    goTo(/Experiment/)
    fireEvent.click(screen.getByRole('tab', { name: TOPICS.find((t) => t.id === 'resilience').title }))
    fireEvent.click(within(screen.getByTestId('experiment-res-bulkhead')).getByRole('button', { name: /Run LIVE experiment/ }))
    await waitFor(() => expect(screen.getByTestId('run-status').textContent).toContain('FAILED'))
    expect(screen.getAllByRole('alert').some((a) => a.textContent.includes('IllegalStateException'))).toBe(true)
    expect(screen.getByTestId('environment-banner')).toBeTruthy()
  })

  it('explains the services from the server, links takeaways to experiments, and offers the Arena', async () => {
    renderPage()
    await act(async () => {})
    goTo(/Explain/)
    expect(screen.getByTestId('services-table').textContent).toContain('micro_lab_orders')
    expect(screen.getByTestId('services-table').textContent).toContain('none')
    expect(screen.getByText('Server limitation.')).toBeTruthy()
    const bulkhead = screen.getAllByRole('link', { name: 'Bulkheads' })[0]
    expect(bulkhead.getAttribute('href')).toBe('#experiment-res-bulkhead')
    goTo(/Test/)
    expect(screen.getByText(/No questions available for this topic yet/)).toBeTruthy()
  })

  it('Test yourself opens the microservices Arena topic with the chosen difficulty and count, and reports the question count', async () => {
    listTopics.mockResolvedValue([{ slug: 'microservices', activeQuestionCount: 34 }, { slug: 'jdbc', activeQuestionCount: 27 }])
    renderPage()
    await act(async () => {})
    goTo(/Test/)
    expect(screen.getByText(/34 active question\(s\) available/)).toBeTruthy()
    fireEvent.change(screen.getByLabelText(/Difficulty/), { target: { value: 'HARD' } })
    fireEvent.change(screen.getByLabelText(/Questions/), { target: { value: '12' } })
    const link = screen.getByRole('link', { name: /Test yourself in the Arena/ })
    expect(link.getAttribute('href')).toBe('/arena?topic=microservices&difficulty=HARD&count=12')
    // no answer or explanation is part of this stage: only the link into the Arena
    expect(screen.queryByText(/correct answer/i)).toBeNull()
  })

  it('Test yourself clamps the question count to the Arena range', async () => {
    listTopics.mockResolvedValue([{ slug: 'microservices', activeQuestionCount: 34 }])
    renderPage()
    await act(async () => {})
    goTo(/Test/)
    fireEvent.change(screen.getByLabelText(/Questions/), { target: { value: '99' } })
    expect(screen.getByRole('link', { name: /Test yourself in the Arena/ }).getAttribute('href')).toBe('/arena?topic=microservices&count=20')
  })

  it('Test yourself shows an error and no link when the Arena topics cannot be loaded', async () => {
    listTopics.mockRejectedValue({ response: { status: 500, data: { detail: 'boom' } } })
    renderPage()
    await act(async () => {})
    goTo(/Test/)
    expect(screen.queryByRole('link', { name: /Test yourself in the Arena/ })).toBeNull()
    expect(screen.getAllByRole('alert').length).toBeGreaterThan(0)
  })

  it('shows an error instead of an empty page when the server cannot be reached', async () => {
    getMicroExperiments.mockRejectedValue({ response: { status: 500, data: { detail: 'boom' } } })
    renderPage()
    await act(async () => {})
    goTo(/Experiment/)
    expect(screen.getAllByRole('alert').length).toBeGreaterThan(0)
  })
})
