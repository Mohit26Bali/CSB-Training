import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, beforeAll, describe, expect, it } from 'vitest'
import * as jwt from '../scenarios/jwtSecurity.sim'
import * as rest from '../scenarios/restLifecycle.sim'
import FlowCanvas from './FlowCanvas'
import FlowCaption from './FlowCaption'

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

afterEach(cleanup)

describe('FlowCanvas (rendered)', () => {
  it('renders every node with its stage number and its state for the current step', () => {
    const index = rest.SIMULATED_STEPS.findIndex((s) => s.id === 'controller')
    render(<FlowCanvas scenario={rest.SIMULATED_SCENARIO} index={index} compact={false} reducedMotion={false} />)
    expect(screen.getByTestId('node-controller').getAttribute('data-state')).toBe('active')
    expect(screen.getByTestId('node-dispatcher').getAttribute('data-state')).toBe('completed')
    expect(screen.getByTestId('node-service').getAttribute('data-state')).toBe('upcoming')
    expect(screen.getByTestId('node-controller').getAttribute('aria-label')).toBe('Stage 6: Controller (current step)')
    expect(screen.getByTestId('node-dispatcher').textContent).toContain('HandlerMapping')
    expect(screen.getByTestId('node-dispatcher').textContent).toContain('HandlerAdapter')
  })

  it('shows the JWT stages including the provider chip and both rejection handlers', () => {
    render(<FlowCanvas scenario={jwt.SIMULATED_SCENARIO} index={0} compact={false} reducedMotion />)
    expect(screen.getByTestId('node-authManager').textContent).toContain('JwtAuthenticationProvider')
    expect(screen.getByTestId('node-entryPoint').getAttribute('aria-label')).toContain('401')
    expect(screen.getByTestId('node-accessDenied').getAttribute('aria-label')).toContain('403')
  })

  it('renders the same nodes in the phone column; the legend is a collapsed section under the diagram, never an overlay on it', () => {
    const { container, rerender } = render(<FlowCanvas scenario={jwt.SIMULATED_SCENARIO} index={0} compact={false} reducedMotion={false} />)
    const legend = screen.getByText('How to read this diagram').closest('details')
    expect(legend.hasAttribute('open')).toBe(false)
    expect(container.querySelector('.react-flow')?.contains(legend)).toBe(false)
    rerender(<FlowCanvas scenario={jwt.SIMULATED_SCENARIO} index={0} compact reducedMotion={false} />)
    for (const n of jwt.NODES) expect(screen.getByTestId(`node-${n.id}`)).toBeTruthy()
  })

  it('the login-versus-bearer note is visible under the JWT diagram, outside the canvas', () => {
    const { container } = render(<FlowCanvas scenario={jwt.SIMULATED_SCENARIO} index={0} compact={false} reducedMotion />)
    const note = screen.getByText(/Bearer requests never use UserDetailsService or PasswordEncoder/)
    expect(container.querySelector('.react-flow')?.contains(note)).toBe(false)
  })
})

describe('FlowCaption', () => {
  it('states the movement and the next step in words', () => {
    render(<FlowCaption scenario={rest.SIMULATED_SCENARIO} index={0} />)
    expect(screen.getByTestId('flow-caption').textContent).toContain('Request: Browser / client → Security filter chain')
    expect(screen.getByTestId('flow-caption').textContent).toContain('Next: ')
  })
})
