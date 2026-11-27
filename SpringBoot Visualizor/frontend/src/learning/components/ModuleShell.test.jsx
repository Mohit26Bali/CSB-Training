import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../../auth/useAuth', () => ({ useAuth: () => ({ user: { id: 7, username: 'sam', role: 'STUDENT' } }) }))

import ModuleShell from './ModuleShell'
import { STAGES } from '../schema/stages'
import { resolveStage } from '../schema/stageNav'

const sections = Object.fromEntries(STAGES.map((s) => [s.id, <p key={s.id}>Content of {s.id}</p>]))

function renderShell() {
  return render(
    <MemoryRouter initialEntries={['/learn/jwt-security']}>
      <ModuleShell title="A lesson" summary="What it teaches." sections={sections} />
    </MemoryRouter>,
  )
}

beforeEach(() => {
  window.history.replaceState(null, '', '/')
  Element.prototype.scrollIntoView = vi.fn()
})
afterEach(cleanup)

describe('ModuleShell: one stage at a time', () => {
  it('shows only the first stage at first, and says where the learner is', () => {
    renderShell()
    expect(screen.getByText('Content of understand')).toBeTruthy()
    for (const s of STAGES.slice(1)) expect(screen.queryByText(`Content of ${s.id}`)).toBeNull()
    expect(screen.getByText('Stage 1 of 6')).toBeTruthy()
    const stages = screen.getByRole('navigation', { name: 'Lesson stages' })
    expect(within(stages).getByRole('button', { name: /Understand/ }).getAttribute('aria-current')).toBe('step')
    expect(within(stages).getByRole('button', { name: /Visualize/ }).getAttribute('aria-current')).toBeNull()
  })

  it('the stage bar switches stage, marks finished stages and keeps the stage in the address bar', () => {
    renderShell()
    const stages = screen.getByRole('navigation', { name: 'Lesson stages' })
    fireEvent.click(within(stages).getByRole('button', { name: /Observe/ }))
    expect(screen.getByText('Content of observe')).toBeTruthy()
    expect(screen.queryByText('Content of understand')).toBeNull()
    expect(screen.getByText('Stage 4 of 6')).toBeTruthy()
    expect(window.location.hash).toBe('#stage-observe')
    expect(within(stages).getByRole('button', { name: /Understand/ }).textContent).toContain('✓')
  })

  it('Previous and Next walk the six stages in order and stop at the ends', () => {
    renderShell()
    expect(screen.queryByRole('button', { name: /←/ })).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: /Next: Visualize/ }))
    expect(screen.getByText('Content of visualize')).toBeTruthy()
    fireEvent.click(screen.getAllByRole('button', { name: /Understand/ }).at(-1)) // the Previous button, after the stage bar
    expect(screen.getByText('Content of understand')).toBeTruthy()
    for (let i = 0; i < 5; i += 1) fireEvent.click(screen.getByRole('button', { name: /^Next:/ }))
    expect(screen.getByText('Content of test')).toBeTruthy()
    expect(screen.queryByRole('button', { name: /^Next:/ })).toBeNull()
    expect(screen.getByText('End of the lesson')).toBeTruthy()
  })

  it('opens the stage named in the address, and follows in-page links to the stage that owns them', async () => {
    window.history.replaceState(null, '', '/#stage-explain')
    renderShell()
    expect(screen.getByText('Content of explain')).toBeTruthy()
    act(() => {
      window.location.hash = '#experiment-no-token'
    })
    await waitFor(() => expect(screen.getByText('Content of experiment')).toBeTruthy())
  })

  it('resolves hashes: stage ids, experiment links, and ignores everything else', () => {
    expect(resolveStage('#stage-observe')).toBe('observe')
    expect(resolveStage('#experiment-n-plus-one')).toBe('experiment')
    expect(resolveStage('#stage-nonsense')).toBeNull()
    expect(resolveStage('#something')).toBeNull()
    expect(resolveStage('')).toBeNull()
  })
})
