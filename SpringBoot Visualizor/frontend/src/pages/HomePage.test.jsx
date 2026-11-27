import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { cleanup, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import { HERO_DASHBOARD, HERO_HOME } from '../assets/heroImages'
import HomePage from './HomePage'

afterEach(cleanup)

describe('HomePage (public)', () => {
  it('invites a visitor to register or log in without needing an account', () => {
    render(<MemoryRouter><HomePage /></MemoryRouter>)
    expect(screen.getByRole('heading', { level: 1 }).textContent).toContain('Learn Spring Boot')
    expect(screen.getByRole('link', { name: 'Get started' }).getAttribute('href')).toBe('/register')
    expect(screen.getAllByRole('link', { name: /log in|already have an account/i }).some((l) => l.getAttribute('href') === '/login')).toBe(true)
  })

  it('lists the modules with their real availability and explains the three provenance badges', () => {
    render(<MemoryRouter><HomePage /></MemoryRouter>)
    expect(screen.getByTestId('module-rest-lifecycle')).toBeTruthy()
    expect(screen.getByTestId('module-jwt-security')).toBeTruthy()
    expect(screen.getByTestId('module-jpa-relationships')).toBeTruthy()
    const badges = within(screen.getByRole('main')).getAllByTestId('mode-badge').map((b) => b.getAttribute('data-tone'))
    expect(badges.sort()).toEqual(['derived', 'live', 'simulated'])
  })

  it('the photograph is decoration (empty alt) and its credit is on the page', () => {
    const { container } = render(<MemoryRouter><HomePage /></MemoryRouter>)
    const img = container.querySelector('img')
    expect(img.getAttribute('alt')).toBe('')
    expect(img.getAttribute('src')).toBe(HERO_HOME.src)
    expect(screen.getAllByText(HERO_HOME.credit).length).toBeGreaterThan(0)
  })
})

describe('hero images', () => {
  const credits = JSON.parse(readFileSync(resolve(process.cwd(), 'public/images/credits.json'), 'utf8'))

  it('every image exists in public/images and its credit names the real photographer from Pexels', () => {
    for (const hero of [HERO_HOME, HERO_DASHBOARD]) {
      const entry = credits.find((c) => `/${c.file}` === hero.src)
      expect(entry, hero.src).toBeTruthy()
      expect(hero.credit).toContain(entry.photographer)
      expect(readFileSync(resolve(process.cwd(), 'public', hero.src.slice(1))).length).toBeGreaterThan(10_000)
    }
  })

  it('the Pexels API key is not stored in the project', () => {
    const everything = JSON.stringify(credits) + readFileSync(resolve(process.cwd(), 'src/assets/heroImages.js'), 'utf8')
    expect(everything).not.toMatch(/Authorization|api[_-]?key/i)
  })
})
