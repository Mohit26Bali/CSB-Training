import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../api/arenaApi', () => ({ listTopics: vi.fn(), listMyChallenges: vi.fn(), startChallenge: vi.fn() }))
vi.mock('../components/AppHeader', () => ({ default: () => <header /> }))

import { listMyChallenges, listTopics, startChallenge } from '../api/arenaApi'
import ArenaPage from './ArenaPage'

afterEach(cleanup)

const topics = [
  { slug: 'jdbc', name: 'JDBC and Spring JDBC', description: 'Connections.', activeQuestionCount: 27 },
  { slug: 'microservices', name: 'Microservices Architecture', description: 'Service boundaries and sagas.', activeQuestionCount: 34 },
]

function Where() {
  return <p data-testid="where">{useLocation().pathname}</p>
}

function renderAt(url) {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <Routes>
        <Route path="/arena" element={<ArenaPage />} />
        <Route path="/arena/attempts/:id" element={<Where />} />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  listTopics.mockResolvedValue(topics)
  listMyChallenges.mockResolvedValue({ content: [] })
})

describe('Challenge Arena opened from the Microservices Lab', () => {
  it('lists the Microservices topic with its question count and preselects it from the lab link', async () => {
    renderAt('/arena?topic=microservices&difficulty=HARD&count=7')
    await act(async () => {})
    expect(screen.getByText(/34 question\(s\) - Service boundaries and sagas/)).toBeTruthy()
    expect(screen.getByRole('radio', { name: /Microservices Architecture/ }).checked).toBe(true)
    expect(screen.getByRole('radio', { name: 'Hard' }).checked).toBe(true)
    expect(screen.getByLabelText(/Questions/).value).toBe('7')
  })

  it('starts the challenge for exactly the topic slug the lab sent, and moves to the attempt', async () => {
    startChallenge.mockResolvedValue({ id: 91 })
    renderAt('/arena?topic=microservices&count=5')
    await act(async () => {})
    fireEvent.click(screen.getByRole('button', { name: /Start challenge/ }))
    await act(async () => {})
    expect(startChallenge).toHaveBeenCalledWith({ topic: 'microservices', difficulty: '', count: 5 })
    expect(screen.getByTestId('where').textContent).toBe('/arena/attempts/91')
  })

  it('shows the server refusal and stays on the page when the challenge cannot start', async () => {
    startChallenge.mockRejectedValue({ response: { status: 409, data: { detail: 'No active questions match the selected topic and difficulty' } } })
    renderAt('/arena?topic=microservices')
    await act(async () => {})
    fireEvent.click(screen.getByRole('button', { name: /Start challenge/ }))
    await act(async () => {})
    expect(screen.getByRole('alert').textContent).toMatch(/No active questions match/)
    expect(screen.queryByTestId('where')).toBeNull()
  })

  it('cannot start a topic without active questions', async () => {
    listTopics.mockResolvedValue([{ ...topics[1], activeQuestionCount: 0 }])
    renderAt('/arena?topic=microservices')
    await act(async () => {})
    expect(screen.getByRole('button', { name: /Start challenge/ }).disabled).toBe(true)
    expect(screen.getByText(/No active questions in this topic yet/)).toBeTruthy()
  })

  it('shows an error state when the topics cannot be loaded', async () => {
    listTopics.mockRejectedValue({ response: { status: 500, data: { detail: 'Topics unavailable' } } })
    renderAt('/arena?topic=microservices')
    await act(async () => {})
    expect(screen.getByRole('alert').textContent).toMatch(/Topics unavailable|went wrong|error/i)
    expect(screen.getByRole('button', { name: /Start challenge/ }).disabled).toBe(true)
  })
})
