import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { readLastLesson, writeLastLesson } from '../../learning/lastLesson'
import { MODULES } from '../../learning/modules'
import ModuleCard from '../ModuleCard'
import ProgressBar from './ProgressBar'
import { ErrorState, EmptyState } from './States'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  window.localStorage.clear()
})

const byId = (id) => MODULES.find((m) => m.id === id)

describe('ModuleCard', () => {
  it('an available module offers a Start learning link to its route', () => {
    render(<MemoryRouter><ModuleCard module={byId('rest-lifecycle')} /></MemoryRouter>)
    const link = screen.getByRole('link', { name: /start learning/i })
    expect(link.getAttribute('href')).toBe('/learn/rest-lifecycle')
    expect(screen.getByText('Available')).toBeTruthy()
  })

  it('says Continue learning only when told the lesson was opened before', () => {
    render(<MemoryRouter><ModuleCard module={byId('jwt-security')} lastOpened /></MemoryRouter>)
    expect(screen.getByRole('link', { name: /continue learning/i }).getAttribute('href')).toBe('/learn/jwt-security')
  })

  it('an upcoming module has no link or button and says it is not available', () => {
    const upcoming = { id: 'future', title: 'Future module', description: 'Not built yet.', icon: 'database', path: null, status: 'upcoming', topicSlug: null }
    render(<MemoryRouter><ModuleCard module={upcoming} /></MemoryRouter>)
    expect(screen.queryByRole('link')).toBeNull()
    expect(screen.queryByRole('button')).toBeNull()
    expect(screen.getByText('Upcoming')).toBeTruthy()
    expect(screen.getByText(/not available yet/i)).toBeTruthy()
  })

  it('shows optional facts only when supplied (nothing is invented)', () => {
    const { rerender } = render(<MemoryRouter><ModuleCard module={byId('rest-lifecycle')} /></MemoryRouter>)
    expect(screen.queryByText(/practice question/i)).toBeNull()
    rerender(<MemoryRouter><ModuleCard module={byId('rest-lifecycle')} meta="6 practice question(s) in the Arena" /></MemoryRouter>)
    expect(screen.getByText(/6 practice question/i)).toBeTruthy()
  })

  it('the registry lists the three finished lessons as available, each with a route and an Arena topic', () => {
    for (const id of ['rest-lifecycle', 'jwt-security', 'jpa-relationships']) {
      expect(byId(id)).toMatchObject({ status: 'available' })
      expect(byId(id).path).toMatch(/^\/learn\//)
      expect(byId(id).topicSlug).toBeTruthy()
    }
    expect(byId('jpa-relationships')).toMatchObject({ path: '/learn/jpa-relationships', topicSlug: 'jpa-hibernate' })
  })
})

describe('ProgressBar', () => {
  it('exposes a clamped value to assistive technology', () => {
    render(<ProgressBar value={140} label="Accuracy" />)
    expect(screen.getByRole('meter', { name: 'Accuracy' }).getAttribute('aria-valuenow')).toBe('100')
    cleanup()
    render(<ProgressBar value="oops" label="Accuracy" />)
    expect(screen.getByRole('meter').getAttribute('aria-valuenow')).toBe('0')
  })
})

describe('states', () => {
  it('errors are announced and empty states carry their message', () => {
    render(<><ErrorState>Boom</ErrorState><EmptyState title="Nothing yet">Start one.</EmptyState></>)
    expect(screen.getByRole('alert').textContent).toContain('Boom')
    expect(screen.getByText('Nothing yet')).toBeTruthy()
  })
})

describe('last opened lesson (browser-only convenience)', () => {
  it('is remembered per user and read back', () => {
    writeLastLesson(7, 'jwt-security')
    expect(readLastLesson(7).id).toBe('jwt-security')
    expect(readLastLesson(8)).toBeNull()
  })

  it('ignores corrupt stored values', () => {
    window.localStorage.setItem('springlab.lastLesson.7', '{not json')
    expect(readLastLesson(7)).toBeNull()
    window.localStorage.setItem('springlab.lastLesson.7', JSON.stringify({ id: 5 }))
    expect(readLastLesson(7)).toBeNull()
  })

  it('does not throw when storage is unavailable', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    expect(readLastLesson(1)).toBeNull()
    expect(() => writeLastLesson(1, 'rest-lifecycle')).not.toThrow()
  })
})

describe('Button styling', () => {
  it('a link can be rendered with button styling', async () => {
    const { ButtonLink } = await import('./Button')
    render(<MemoryRouter><ButtonLink to="/arena">Go</ButtonLink></MemoryRouter>)
    const link = screen.getByRole('link', { name: 'Go' })
    expect(link.className).toContain('bg-accent')
    expect(link.className).toContain('text-on-accent') // dark text on the lime button
    fireEvent.focus(link)
  })
})
