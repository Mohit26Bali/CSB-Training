import { describe, expect, it } from 'vitest'
import { edgeAppearance, MECHANISMS } from './diagram'
import { buildEdges, buildNodes } from './flowElements'
import {
  breakerState, callsOfStep, drawnOfStep, edgeLabel, edgeOverlay, enrichSteps, headline, isFailure, latestEvidence, localTransactions, mechanismOfCall,
  nodeOverlay, permits, sagaLog, spanRows,
} from './microRun'
import { handlesFor, liveScenario } from '../scenarios/microLab.sim'

const call = (seq, from, to, operation, outcome = 'SUCCESS', kind = 'SYNC', extra = {}) => ({
  seq, from, to, operation, outcome, kind, code: null, simulatedMillis: 10, handlerInvoked: true, instance: null, ...extra,
})
const step = (id, extra = {}) => ({
  id, title: id, nodes: [], edgeFrom: null, edgeTo: null, direction: 'REQUEST', provenance: 'CAPTURED', explanation: 'An explanation that is long enough.',
  evidence: [], sql: [], codeRefId: null, ...extra,
})
const withCalls = (seqs, extra = {}) => step('s', { evidence: seqs.map((n) => ({ label: `Service call ${n}`, value: 'x' })), ...extra })

describe('classifying a call into a mechanism', () => {
  it('tells a synchronous call from an event, a compensation, an outbox relay and a local refusal', () => {
    expect(mechanismOfCall(call(1, 'order', 'inventory', 'reserve'))).toBe('SYNC')
    expect(mechanismOfCall(call(1, 'broker', 'notification', 'onOrderPlaced', 'SUCCESS', 'EVENT'))).toBe('EVENT')
    expect(mechanismOfCall(call(1, 'order', 'inventory', 'release'))).toBe('COMPENSATION')
    expect(mechanismOfCall(call(1, 'order', 'payment', 'refund'))).toBe('COMPENSATION')
    expect(mechanismOfCall(call(1, 'client', 'order', 'relayOutbox'))).toBe('OUTBOX')
    expect(mechanismOfCall(call(1, 'client', 'product', 'getProduct', 'CIRCUIT_OPEN'))).toBe('REFUSED')
    expect(mechanismOfCall(call(1, 'client', 'payment', 'charge', 'BULKHEAD_REJECTED'))).toBe('REFUSED')
  })

  it('treats a compensation delivered as an event as a compensation first', () => {
    expect(mechanismOfCall(call(1, 'broker', 'inventory', 'onPaymentFailed', 'SUCCESS', 'EVENT'))).toBe('COMPENSATION')
  })

  it('counts outages, timeouts and errors as failures but not a business refusal', () => {
    for (const o of ['UNAVAILABLE', 'TIMEOUT', 'TECHNICAL_ERROR', 'BULKHEAD_REJECTED', 'CIRCUIT_OPEN']) expect(isFailure(call(1, 'a', 'b', 'op', o)), o).toBe(true)
    for (const o of ['SUCCESS', 'BUSINESS_FAILURE']) expect(isFailure(call(1, 'a', 'b', 'op', o)), o).toBe(false)
  })

  it('gives every mechanism its own line pattern and its own text, so none is told apart by colour alone', () => {
    const dashes = Object.values(MECHANISMS).map((m) => m.dash)
    expect(new Set(dashes).size).toBe(dashes.length)
    const shorts = Object.values(MECHANISMS).map((m) => m.short)
    expect(new Set(shorts).size).toBe(shorts.length)
    for (const key of Object.keys(MECHANISMS)) {
      expect(edgeAppearance({ state: 'active', mechanism: key }).dash, key).toBe(MECHANISMS[key].dash)
    }
  })

  it('draws a compensation in amber and a failure in red whatever the state, and other mechanisms in the state colour', () => {
    expect(edgeAppearance({ state: 'active', mechanism: 'COMPENSATION' }).stroke).toBe('var(--color-warning)')
    expect(edgeAppearance({ state: 'completed', mechanism: 'COMPENSATION' }).stroke).toBe('var(--color-warning)')
    expect(edgeAppearance({ state: 'active', mechanism: 'SYNC', failed: true }).stroke).toBe('var(--color-error)')
    expect(edgeAppearance({ state: 'active', mechanism: 'REFUSED' }).stroke).toBe('var(--color-error)')
    expect(edgeAppearance({ state: 'active', mechanism: 'EVENT' }).stroke).toBe('var(--color-accent-primary)')
    expect(edgeAppearance({ state: 'completed', mechanism: 'EVENT' }).stroke).toBe('var(--color-success)')
    expect(edgeAppearance({ state: 'unused', mechanism: 'EVENT' }).dash).toBe('4 5')
  })

  it('keeps the old edge appearance when no mechanism is given', () => {
    expect(edgeAppearance({ state: 'active' }).stroke).toBe('var(--color-accent-primary)')
    expect(edgeAppearance({ state: 'active', tone: 'error' }).stroke).toBe('var(--color-error)')
  })
})

describe('the lines a step draws', () => {
  it('finds a step\'s calls through its "Service call N" evidence', () => {
    const calls = [call(1, 'a', 'b', 'x'), call(2, 'b', 'c', 'y'), call(3, 'c', 'd', 'z')]
    expect(callsOfStep(withCalls([2, 3]), calls).map((c) => c.seq)).toEqual([2, 3])
    expect(callsOfStep(withCalls([9]), calls)).toEqual([])
  })

  it('draws a line per call, labelled by mechanism, and a failed call is marked failed with its outcome', () => {
    const calls = [call(1, 'order', 'inventory', 'reserve', 'TIMEOUT')]
    const [line] = drawnOfStep(withCalls([1]), calls)
    expect(line).toMatchObject({ id: 'order->inventory', mechanism: 'SYNC', failed: true, outcome: 'TIMEOUT' })
    expect(edgeLabel(line)).toBe('→ sync call · TIMEOUT')
  })

  it('adds a LOCAL transaction line for every service database a captured step touched', () => {
    const lines = drawnOfStep(step('s', { nodes: ['order', 'order-db', 'inventory', 'inventory-db'] }), [])
    expect(lines.map((l) => `${l.id}:${l.mechanism}`).sort()).toEqual(['inventory->inventory-db:LOCAL_TX', 'order->order-db:LOCAL_TX'])
    expect(drawnOfStep(step('s', { provenance: 'CONCEPTUAL', nodes: ['order', 'order-db'] }), [])).toEqual([])
  })

  it('draws the step\'s own edge when no call explains it, as an event to the channel, an outbox relay, or a local transaction', () => {
    expect(drawnOfStep(step('publish', { nodes: ['order', 'broker'], edgeFrom: 'order', edgeTo: 'broker' }), [])[0].mechanism).toBe('EVENT')
    expect(drawnOfStep(withCalls([1], { edgeFrom: 'order', edgeTo: 'broker' }), [call(1, 'client', 'order', 'relayOutbox')]).find((d) => d.id === 'order->broker').mechanism).toBe('OUTBOX')
    expect(drawnOfStep(step('own', { nodes: ['order', 'order-db'], edgeFrom: 'order', edgeTo: 'order-db' }), [])[0].mechanism).toBe('LOCAL_TX')
  })

  it('shows the most significant mechanism when one pair uses several in a step, and lists the others in the label', () => {
    const calls = [call(1, 'order', 'inventory', 'reserve'), call(2, 'order', 'inventory', 'release')]
    const [line] = drawnOfStep(withCalls([1, 2]), calls)
    expect(line.mechanism).toBe('COMPENSATION')
    expect(edgeLabel(line)).toBe('↺ compensation + sync call')
  })

  it('has the overlay draw ONLY the current step lines, hide the rest, and show how a line is used in that step', () => {
    const calls = [call(1, 'a', 'b', 'x'), call(2, 'a', 'b', 'release'), call(3, 'b', 'c', 'y')]
    const steps = enrichSteps([withCalls([1]), withCalls([2]), withCalls([3])], calls)
    expect(edgeOverlay(steps, 0)['a->b']).toMatchObject({ state: 'active', mechanism: 'SYNC' })
    expect(edgeOverlay(steps, 0)['b->c']).toMatchObject({ state: 'hidden' })
    expect(edgeOverlay(steps, 1)['a->b']).toMatchObject({ state: 'active', mechanism: 'COMPENSATION' })
    expect(edgeOverlay(steps, 2)['a->b']).toMatchObject({ state: 'hidden' })
    expect(edgeOverlay(steps, 2)['b->c'].state).toBe('active')
  })

  it('puts a DOWN / TIMEOUT / ERROR chip on a service whose latest call failed, and removes it once a call succeeds', () => {
    const calls = [call(1, 'client', 'inventory', 'reserve', 'UNAVAILABLE'), call(2, 'client', 'inventory', 'reserve')]
    const steps = enrichSteps([withCalls([1]), withCalls([2])], calls)
    expect(nodeOverlay(steps, 0).inventory.chips[0].text).toBe('DOWN')
    expect(nodeOverlay(steps, 1).inventory).toBeUndefined()
    expect(nodeOverlay(enrichSteps([withCalls([1])], [call(1, 'a', 'payment', 'charge', 'BULKHEAD_REJECTED')]), 0).payment.chips[0].text).toBe('BULKHEAD FULL')
  })
})

describe('the shared canvas applies the overlays', () => {
  const calls = [call(1, 'broker', 'notification', 'onOrderPlaced', 'SUCCESS', 'EVENT')]
  const run = { steps: [step('deliver', { nodes: ['broker', 'notification', 'notification-db'], edgeFrom: 'broker', edgeTo: 'notification', evidence: [{ label: 'Service call 1', value: 'x' }] })], calls }
  const scenario = liveScenario(run, { id: 'x', title: 'X' })

  it('carries the mechanism, label and state of each line into the React Flow edges', () => {
    const edges = buildEdges({ scenario, index: 0, compact: false, reducedMotion: true })
    const event = edges.find((e) => e.id === 'broker->notification')
    expect(event.data).toMatchObject({ state: 'active', mechanism: 'EVENT', label: '⇢ event' })
    const tx = edges.find((e) => e.id === 'notification->notification-db')
    expect(tx.data.mechanism).toBe('LOCAL_TX')
  })

  it('does not draw lines that belong to other steps', () => {
    const two = liveScenario({
      steps: [
        step('one', { nodes: ['client', 'order'], edgeFrom: 'client', edgeTo: 'order', evidence: [{ label: 'Service call 1', value: 'x' }] }),
        step('two', { nodes: ['order', 'payment'], edgeFrom: 'order', edgeTo: 'payment', evidence: [{ label: 'Service call 2', value: 'x' }] }),
      ],
      calls: [call(1, 'client', 'order', 'placeOrder'), call(2, 'order', 'payment', 'charge')],
    }, { id: 'x', title: 'X' })
    expect(buildEdges({ scenario: two, index: 0, compact: false, reducedMotion: true }).map((e) => e.id)).toEqual(['client->order'])
    expect(buildEdges({ scenario: two, index: 1, compact: false, reducedMotion: true }).map((e) => e.id)).toEqual(['order->payment'])
  })

  it('routes a line between two services of the same column that are not neighbours round the nodes in between', () => {
    expect(handlesFor('order', 'product')).toEqual(['l-s', 'l-t'])
    expect(handlesFor('order', 'inventory')).toEqual(['t-s', 'b-t'])
    expect(handlesFor('client', 'order')).toEqual(['r-s', 'l-t'])
    expect(handlesFor('order', 'order-db')).toEqual(['r-s', 'l-t'])
    expect(handlesFor('order', 'broker')).toEqual(['l-s', 'r-t'])
    const around = liveScenario({
      steps: [step('one', { nodes: ['order', 'product'], edgeFrom: 'order', edgeTo: 'product', evidence: [{ label: 'Service call 1', value: 'x' }] })],
      calls: [call(1, 'order', 'product', 'getProduct')],
    }, { id: 'x', title: 'X' })
    expect(around.layout.edgeMeta['order->product'].offset).toBeGreaterThan(24)
  })

  it('gives nodes a glyph and shape, marks simulated infrastructure, and carries health chips', () => {
    const nodes = buildNodes({ scenario, index: 0, compact: false }).filter((n) => n.type === 'lab')
    const byId = Object.fromEntries(nodes.map((n) => [n.id, n.data]))
    expect(byId.broker).toMatchObject({ glyph: 'broker', shape: 'broker', simulated: true })
    expect(byId['order-db']).toMatchObject({ glyph: 'database', shape: 'database' })
    expect(byId.order.glyph).toBe('service')
    expect(byId.order.simulated).toBeUndefined()
    expect(byId['notification-db'].state).toBe('active')
    expect(nodes).toHaveLength(19)
  })
})

describe('reading a run\'s evidence', () => {
  it('reads commits and rollbacks per service from the captured database calls', () => {
    const s = step('s', { evidence: [{ label: 'Other database calls observed', value: 'order: connection obtained → order: setAutoCommit(false) → order: commit() → inventory: rollback() → order: commit()' }] })
    expect(localTransactions(s)).toEqual([
      { service: 'order', kind: 'committed', count: 2 },
      { service: 'inventory', kind: 'rolled back', count: 1 },
    ])
    expect(localTransactions(step('none'))).toEqual([])
  })

  it('finds the latest breaker state and permits at or before a step', () => {
    const steps = [
      step('a', { evidence: [{ label: 'Breaker state', value: 'CLOSED' }, { label: 'Permits available to Payment', value: '2' }] }),
      step('b'),
      step('c', { evidence: [{ label: 'Breaker state', value: 'OPEN' }, { label: 'Permits available to Payment', value: '0' }, { label: 'Permits available to Inventory', value: '2' }] }),
    ]
    expect(breakerState(steps, 1)).toBe('CLOSED')
    expect(breakerState(steps, 2)).toBe('OPEN')
    expect(permits(steps, 1)).toEqual({ payment: 2 })
    expect(permits(steps, 2)).toEqual({ payment: 0, inventory: 2 })
    expect(latestEvidence(steps, 1, 'Nothing')).toBeNull()
    expect(breakerState([step('x', { evidence: [{ label: 'Breaker state', value: 'BROKEN' }] })], 0)).toBeNull()
  })

  it('parses the saga log, including a failure detail', () => {
    const s = step('saga', {
      evidence: [
        { label: 'Saga log 2 RESERVE_STOCK', value: 'COMPLETED (stock reserved)' },
        { label: 'Saga log 3 CHARGE_PAYMENT', value: 'FAILED (BUSINESS_FAILURE (DECLINED))' },
        { label: 'Saga log 4 COMPENSATE_RESERVE_STOCK', value: 'COMPENSATION_FAILED (UNAVAILABLE (CONNECTION_REFUSED))' },
      ],
    })
    expect(sagaLog(s)).toEqual([
      { seq: 2, step: 'RESERVE_STOCK', state: 'COMPLETED', detail: 'stock reserved' },
      { seq: 3, step: 'CHARGE_PAYMENT', state: 'FAILED', detail: 'BUSINESS_FAILURE (DECLINED)' },
      { seq: 4, step: 'COMPENSATE_RESERVE_STOCK', state: 'COMPENSATION_FAILED', detail: 'UNAVAILABLE (CONNECTION_REFUSED)' },
    ])
  })

  it('reads the headline outcome a step reports', () => {
    expect(headline(step('s', { evidence: [{ label: 'Caller sees', value: 'SUCCESS' }] }))).toBe('SUCCESS')
    expect(headline(step('s', { evidence: [{ label: 'Response status', value: '404 NO_ROUTE' }] }))).toBe('404 NO_ROUTE')
    expect(headline(step('s'))).toBeNull()
  })

  it('lays spans out as a tree with self time, children under their parent', () => {
    const sp = (spanId, parentSpanId, startMillis, simulatedMillis) => ({ spanId, parentSpanId, startMillis, simulatedMillis, service: 'x', operation: 'y', status: 'SUCCESS', correlationId: 'corr-1' })
    const rows = spanRows([sp('span-2', 'span-1', 5, 12), sp('span-3', 'span-1', 17, 15), sp('span-1', null, 0, 47)])
    expect(rows.map((r) => `${r.span.spanId}@${r.depth}`)).toEqual(['span-1@0', 'span-2@1', 'span-3@1'])
    expect(rows[0].selfMillis).toBe(20)
    expect(rows[1].selfMillis).toBe(12)
  })

  it('treats a span whose parent is missing as a root, which is what a broken trace looks like', () => {
    const sp = (spanId, parentSpanId) => ({ spanId, parentSpanId, startMillis: 0, simulatedMillis: 1, service: 'x', operation: 'y', status: 'SUCCESS', correlationId: 'c' })
    expect(spanRows([sp('span-1', null), sp('span-2', 'span-99')]).map((r) => r.depth)).toEqual([0, 0])
  })
})
