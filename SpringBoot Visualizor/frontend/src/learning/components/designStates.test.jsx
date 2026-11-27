import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeAll, describe, expect, it } from 'vitest'
import { buttonClass } from '../../components/ui/buttonStyles'
import Card from '../../components/ui/Card'
import Disclosure from '../../components/ui/Disclosure'
import * as jwt from '../scenarios/jwtSecurity.sim'
import FlowCanvas from './FlowCanvas'
import ModeBadge from './ModeBadge'

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
afterEach(cleanup)

describe('provenance badges keep their meaning', () => {
  it.each([
    ['CAPTURED', 'live', 'LIVE', 'text-captured'],
    ['DERIVED', 'derived', 'INFERRED', 'text-derived'],
    ['CONCEPTUAL', 'simulated', 'SIMULATED', 'text-conceptual'],
  ])('%s is shown as %s with its own colour and a text label', (provenance, tone, label, colour) => {
    render(<ModeBadge provenance={provenance} />)
    const badge = screen.getByTestId('mode-badge')
    expect(badge.getAttribute('data-tone')).toBe(tone)
    expect(badge.textContent).toContain(label)
    expect(badge.className).toContain(colour)
    expect(badge.className.includes('border-dashed')).toBe(tone === 'simulated') // not colour alone: simulated is also dashed
  })
})

describe('buttons', () => {
  it('has primary, secondary, ghost and destructive variants; only the primary is lime and a destructive one never is', () => {
    const v = (variant) => buttonClass({ variant })
    expect(v('primary')).toContain('bg-accent')
    expect(v('primary')).toContain('text-on-accent')
    expect(v('primary')).toContain('hover:bg-accent-hover')
    expect(v('primary')).toContain('active:bg-accent-active')
    expect(v('secondary')).not.toContain('bg-accent')
    expect(v('ghost')).toContain('bg-transparent')
    expect(v('danger')).toContain('bg-error')
    expect(v('danger')).not.toContain('bg-accent')
    for (const variant of ['primary', 'secondary', 'ghost', 'danger']) {
      expect(v(variant)).toContain('focus-visible:ring-2')
      expect(v(variant)).toContain('disabled:opacity-45')
    }
  })
})

describe('cards and disclosure', () => {
  it('a card is a graphite surface with a subtle border and 12px radius, not a shadow box', () => {
    render(<Card data-testid="c">x</Card>)
    const cls = screen.getByTestId('c').className
    expect(cls).toContain('bg-surface')
    expect(cls).toContain('border-line')
    expect(cls).toContain('rounded-xl')
    expect(cls).not.toContain('shadow')
  })

  it('a disclosure is closed until opened (progressive disclosure) and its content stays in the page', () => {
    const { container, rerender } = render(<Disclosure title="All 12 steps">inside</Disclosure>)
    expect(container.querySelector('details').hasAttribute('open')).toBe(false)
    expect(screen.getByText('inside')).toBeTruthy()
    expect(screen.getByText('All 12 steps').closest('summary')).toBeTruthy()
    rerender(<Disclosure title="All 12 steps" defaultOpen>inside</Disclosure>)
    expect(container.querySelector('details').hasAttribute('open')).toBe(true)
  })
})

describe('diagram execution states carry text, not just colour', () => {
  const step = (id, nodes, extra = {}) => ({ id, title: id, nodes, edgeFrom: null, edgeTo: null, direction: 'REQUEST', provenance: 'CAPTURED', explanation: 'x'.repeat(30), evidence: [], sql: [], codeRefId: null, ...extra })
  const scenario = {
    ...jwt.SIMULATED_SCENARIO,
    mode: 'LIVE',
    steps: [step('a', ['client']), step('b', ['bearerFilter']), step('c', ['authorization']), step('d', ['accessDenied'], { edgeFrom: 'authorization', edgeTo: 'accessDenied' })],
  }

  it('shows Now, Passed and Not reached as words, and Rejected with the error treatment for a refusal', () => {
    render(<FlowCanvas scenario={scenario} index={3} compact={false} reducedMotion />)
    expect(screen.getByTestId('node-accessDenied').textContent).toContain('Rejected')
    expect(screen.getByTestId('node-accessDenied').className).toContain('border-error')
    expect(screen.getByTestId('node-client').textContent).toContain('Passed')
    expect(screen.getByTestId('node-client').className).toContain('border-')
    expect(screen.getByTestId('node-controller').textContent).toContain('Not reached')
    expect(screen.getByTestId('node-controller').className).toContain('border-dashed')
  })

  it('the current step is lime with the word Now; the aria label states the state in words', () => {
    render(<FlowCanvas scenario={scenario} index={1} compact={false} reducedMotion />)
    const node = screen.getByTestId('node-bearerFilter')
    expect(node.className).toContain('border-accent')
    expect(node.textContent).toContain('Now')
    expect(node.getAttribute('aria-label')).toContain('current step')
    expect(screen.getByTestId('node-client').getAttribute('aria-label')).toContain('already passed')
    expect(screen.getByTestId('node-authorization').getAttribute('aria-label')).toContain('comes later')
  })

  it('the legend explains arrow direction and the rejection colour, collapsed by default', () => {
    render(<FlowCanvas scenario={scenario} index={0} compact={false} reducedMotion />)
    expect(screen.getByText('How to read this diagram').closest('details').hasAttribute('open')).toBe(false)
    expect(screen.getByText(/Arrowheads show the direction/)).toBeTruthy()
    expect(screen.getByText(/rejection path/)).toBeTruthy()
  })

  it('the diagram has a text alternative and a caption for people who cannot read the picture', () => {
    const { container } = render(<MemoryRouter><FlowCanvas scenario={scenario} index={0} compact={false} reducedMotion /></MemoryRouter>)
    expect(container.querySelector('[role="img"]').getAttribute('aria-label')).toMatch(/step list and explanation describe the same path in text/)
  })
})
