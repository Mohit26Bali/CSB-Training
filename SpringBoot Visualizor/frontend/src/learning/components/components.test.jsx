import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { EXPLAIN, SIMULATED_SCENARIO } from '../scenarios/restLifecycle.sim'
import { PROVENANCE } from '../schema/scenario'
import CodeInspector from './CodeInspector'
import ExplanationPanel from './ExplanationPanel'
import Explain5W1H from './Explain5W1H'
import ModeBadge from './ModeBadge'
import SimulationControls from './SimulationControls'
import StepList from './StepList'
import TestYourself from './TestYourself'

afterEach(cleanup)

describe('ModeBadge', () => {
  it('shows LIVE for CAPTURED only', () => {
    for (const [provenance, text] of [
      [PROVENANCE.CAPTURED, 'LIVE'],
      [PROVENANCE.DERIVED, 'INFERRED'],
      [PROVENANCE.CONCEPTUAL, 'SIMULATED'],
    ]) {
      const { unmount } = render(<ModeBadge provenance={provenance} />)
      const badge = screen.getByTestId('mode-badge')
      expect(badge.textContent).toContain(text)
      expect(badge.textContent?.includes('LIVE')).toBe(provenance === PROVENANCE.CAPTURED)
      unmount()
    }
  })

  it('never relies on colour alone: it has an icon, a text label and a screen-reader explanation', () => {
    render(<ModeBadge provenance={PROVENANCE.DERIVED} />)
    const badge = screen.getByTestId('mode-badge')
    expect(badge.querySelector('[aria-hidden="true"]')).not.toBeNull()
    expect(badge.querySelector('.sr-only')?.textContent).toMatch(/inferred/i)
  })
})

describe('Explain5W1H', () => {
  it('renders all six mandatory sections for a complete explanation', () => {
    render(<Explain5W1H explain={EXPLAIN} />)
    for (const key of ['what', 'why', 'who', 'where', 'when', 'how']) {
      expect(screen.getByTestId(`5w1h-${key}`)).toBeTruthy()
    }
  })

  it('refuses to render a partial lesson and says what is missing', () => {
    const partial = { ...EXPLAIN, when: undefined }
    render(<Explain5W1H explain={partial} />)
    const alert = screen.getByRole('alert')
    expect(alert.textContent).toContain('incomplete')
    expect(alert.textContent).toContain('"when"')
    expect(screen.queryByTestId('5w1h-what')).toBeNull()
  })
})

describe('SimulationControls', () => {
  const sim = (overrides = {}) => ({
    status: 'idle', index: 0, speed: 1, autoplayAllowed: true, canNext: true, canBack: false, isPlaying: false,
    play: vi.fn(), pause: vi.fn(), next: vi.fn(), back: vi.fn(), reset: vi.fn(), setSpeed: vi.fn(), ...overrides,
  })

  it('offers Play, Back, Next step and Reset, and reports the step', () => {
    const s = sim()
    render(<SimulationControls sim={s} total={12} />)
    fireEvent.click(screen.getByRole('button', { name: 'Play' }))
    fireEvent.click(screen.getByRole('button', { name: 'Next step' }))
    expect(s.play).toHaveBeenCalledOnce()
    expect(s.next).toHaveBeenCalledOnce()
    expect(screen.getByRole('button', { name: 'Back' })).toHaveProperty('disabled', true)
    expect(screen.getByRole('button', { name: 'Reset' })).toHaveProperty('disabled', true)
    expect(screen.getByTestId('step-counter').textContent).toBe('Step 1 of 12')
  })

  it('shows Pause while playing', () => {
    const s = sim({ isPlaying: true, status: 'playing', index: 3, canBack: true })
    render(<SimulationControls sim={s} total={12} />)
    fireEvent.click(screen.getByRole('button', { name: 'Pause' }))
    expect(s.pause).toHaveBeenCalledOnce()
    expect(screen.queryByRole('button', { name: 'Play' })).toBeNull()
  })

  it('disables auto-play and explains why when reduced motion is on, but keeps stepping available', () => {
    const s = sim({ autoplayAllowed: false })
    render(<SimulationControls sim={s} total={12} />)
    expect(screen.getByRole('button', { name: 'Play' })).toHaveProperty('disabled', true)
    expect(screen.getByRole('button', { name: 'Next step' })).toHaveProperty('disabled', false)
    expect(screen.getByText(/reduced motion is on/i)).toBeTruthy()
  })
})

describe('StepList', () => {
  it('marks the current step with aria-current and lets the learner jump to any step', () => {
    const onSelect = vi.fn()
    render(<StepList steps={SIMULATED_SCENARIO.steps} currentIndex={2} onSelect={onSelect} />)
    const items = within(screen.getByRole('list', { name: 'Steps' })).getAllByRole('button')
    expect(items).toHaveLength(SIMULATED_SCENARIO.steps.length)
    expect(items[2].getAttribute('aria-current')).toBe('step')
    expect(items.filter((b) => b.getAttribute('aria-current') === 'step')).toHaveLength(1)
    fireEvent.click(items[5])
    expect(onSelect).toHaveBeenCalledWith(5)
  })
})

describe('ExplanationPanel', () => {
  const captured = {
    id: 'sql-0', title: 'Hibernate prepares 1 SQL statement', provenance: 'CAPTURED', explanation: 'Text.',
    evidence: [{ label: 'Statements', value: '1' }],
    sql: [{ index: 1, sql: 'select u1_0.id from users u1_0 where u1_0.username=?', phase: 'HANDLER', trigger: 'UserRepository.findByUsername' }],
  }

  it('shows captured SQL together with the placeholder caveat', () => {
    render(<ExplanationPanel step={captured} index={0} total={3} />)
    const block = screen.getByTestId('sql-block')
    expect(block.textContent).toContain('where u1_0.username=?')
    expect(block.textContent).toMatch(/placeholders/i)
    expect(block.textContent).toMatch(/does not show/i)
    expect(screen.getByTestId('mode-badge').textContent).toContain('LIVE')
  })

  it('shows no SQL block and a non-LIVE badge for a conceptual step', () => {
    render(<ExplanationPanel step={{ ...SIMULATED_SCENARIO.steps[0] }} index={0} total={12} />)
    expect(screen.queryByTestId('sql-block')).toBeNull()
    expect(screen.getByTestId('mode-badge').textContent).not.toContain('LIVE')
  })
})

describe('CodeInspector', () => {
  const reference = {
    id: 'x', title: 'TopicService', file: 'src/main/java/com/springlab/service/TopicService.java', startLine: 19, endLine: 20,
    language: 'java', excerpt: '    @Transactional(readOnly = true)\n    public List<TopicResponse> list() {',
    notes: [{ line: 19, token: '@Transactional', text: 'Declares a read-only transaction.' }],
  }

  it('shows the real file path, line range, numbered source and the annotation notes', () => {
    render(<CodeInspector reference={reference} />)
    expect(screen.getByText(/TopicService\.java - lines 19-20/)).toBeTruthy()
    expect(screen.getByText('19')).toBeTruthy()
    // Once as highlighted source, once as the caption of its explanatory note.
    expect(screen.getAllByText('@Transactional')).toHaveLength(2)
    expect(screen.getByText(/Declares a read-only transaction/)).toBeTruthy()
  })

  it('says plainly that no source is shown when none is verified, instead of inventing code', () => {
    render(<CodeInspector reference={null} />)
    expect(screen.getByText(/No SpringLab source is associated/)).toBeTruthy()
  })
})

describe('TestYourself', () => {
  const topics = [{ slug: 'rest-lifecycle', activeQuestionCount: 4 }, { slug: 'jwt-security', activeQuestionCount: 0 }]

  it('links to the Challenge Arena with the topic and the supported parameters', () => {
    render(<MemoryRouter><TestYourself topicSlug="rest-lifecycle" topics={topics} /></MemoryRouter>)
    fireEvent.change(screen.getByLabelText(/Difficulty/), { target: { value: 'MEDIUM' } })
    const link = screen.getByRole('link', { name: /Test yourself/ })
    expect(link.getAttribute('href')).toBe('/arena?topic=rest-lifecycle&difficulty=MEDIUM&count=5')
  })

  it('offers no link when the topic has no active questions', () => {
    render(<MemoryRouter><TestYourself topicSlug="jwt-security" topics={topics} /></MemoryRouter>)
    expect(screen.queryByRole('link')).toBeNull()
    expect(screen.getByText(/No questions available/)).toBeTruthy()
  })
})
