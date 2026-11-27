import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const SRC = resolve(process.cwd(), 'src')
const css = readFileSync(join(SRC, 'index.css'), 'utf8')
const tokens = Object.fromEntries([...css.matchAll(/--([\w-]+):\s*(#[0-9a-fA-F]{6})/g)].map((m) => [m[1], m[2].toLowerCase()]))

const rgb = (hex) => [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16))
const luminance = (hex) => {
  const [r, g, b] = rgb(hex).map((v) => {
    const c = v / 255
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
  })
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}
const contrast = (a, b) => {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x)
  return (hi + 0.05) / (lo + 0.05)
}

describe('Graphite + Lime design tokens', () => {
  it('defines every specified token with the specified value', () => {
    const spec = {
      'color-bg-app': '#181a19', 'color-bg-sidebar': '#141715', 'color-bg-surface': '#222723', 'color-bg-elevated': '#292e2a',
      'color-bg-subtle': '#303831', 'color-bg-hover': '#343d35',
      'color-accent-primary': '#c6f36b', 'color-accent-primary-hover': '#d4ff83', 'color-accent-primary-active': '#a9d94f',
      'color-accent-secondary': '#a5b4a3', 'color-accent-secondary-hover': '#bbc8b9',
      'color-text-primary': '#f2f5ef', 'color-text-secondary': '#b3bdb1', 'color-text-disabled': '#667066', 'color-text-on-accent': '#181a19',
      'color-border-default': '#394239', 'color-border-strong': '#566354', 'color-border-subtle': '#303831', 'color-border-focus': '#c6f36b',
      'color-success': '#65d69a', 'color-warning': '#f2c66d', 'color-error': '#ff7777', 'color-info': '#76b9f8',
      'color-prov-captured': '#65d69a', 'color-prov-derived': '#f2c66d', 'color-prov-conceptual': '#b99aff',
      'diagram-client': '#76b9f8', 'diagram-mvc': '#56c8d2', 'diagram-security': '#b99aff', 'diagram-business': '#65d69a',
      'diagram-persistence': '#f2c66d', 'diagram-database': '#a5b4a3',
      'syntax-keyword': '#c6f36b', 'syntax-string': '#a8d99b', 'syntax-number': '#e7b879', 'syntax-type': '#76b9f8',
      'syntax-function': '#80d5d0', 'syntax-punctuation': '#d5ddd2', 'syntax-error': '#ff7777',
    }
    for (const [name, value] of Object.entries(spec)) expect(tokens[name], name).toBe(value)
    expect(css).toContain('--color-bg-overlay: rgba(0, 0, 0, 0.72)')
  })

  it('has a subtle background and a border variant for every semantic colour', () => {
    for (const name of ['success', 'warning', 'error', 'info']) {
      expect(css, name).toContain(`--color-${name}-subtle:`)
      expect(css, name).toContain(`--color-${name}-border:`)
    }
  })

  it('exposes the tokens as Tailwind 4 utilities through @theme inline, not a Tailwind 3 config', () => {
    expect(css).toContain('@theme inline')
    expect(css).toContain('@import "tailwindcss"')
    for (const alias of ['--color-app:', '--color-surface:', '--color-elevated:', '--color-accent:', '--color-fg:', '--color-fg-2:', '--color-muted:',
      '--color-line:', '--color-captured:', '--color-derived:', '--color-conceptual:', '--color-layer-security:', '--color-layer-database:']) {
      expect(css, alias).toContain(alias)
    }
    expect(readdirSync(resolve(process.cwd()))).not.toContain('tailwind.config.js')
  })

  it('is a single dark theme: dark colour scheme, no theme toggle, no light overrides', () => {
    expect(css).toContain('color-scheme: dark')
    expect(css).not.toMatch(/prefers-color-scheme\s*:\s*light/)
    expect(css).not.toMatch(/\.light\b|data-theme/)
  })

  it('keeps the provenance meanings: captured is green, derived is amber, conceptual is violet, and none is the accent', () => {
    expect(tokens['color-prov-captured']).toBe(tokens['color-success'])
    expect(tokens['color-prov-derived']).toBe(tokens['color-warning'])
    expect(new Set([tokens['color-prov-captured'], tokens['color-prov-derived'], tokens['color-prov-conceptual']]).size).toBe(3)
    for (const n of ['color-prov-captured', 'color-prov-derived', 'color-prov-conceptual']) expect(tokens[n]).not.toBe(tokens['color-accent-primary'])
  })
})

describe('WCAG contrast of the tokens (computed from index.css)', () => {
  const surfaces = ['color-bg-app', 'color-bg-sidebar', 'color-bg-surface', 'color-bg-elevated', 'color-bg-subtle']

  it('body, secondary and muted text reach 4.5:1 on every surface they sit on', () => {
    for (const text of ['color-text-primary', 'color-text-secondary', 'color-text-muted']) {
      for (const bg of surfaces) expect(contrast(tokens[text], tokens[bg]), `${text} on ${bg}`).toBeGreaterThanOrEqual(4.5)
    }
  })

  it('the accent, status, provenance and layer colours reach 4.5:1 as text on the surfaces they are used on', () => {
    const colours = ['color-accent-primary', 'color-accent-secondary', 'color-success', 'color-warning', 'color-error', 'color-info',
      'color-prov-conceptual', 'diagram-client', 'diagram-mvc', 'diagram-security', 'diagram-business', 'diagram-persistence', 'diagram-database']
    for (const c of colours) {
      for (const bg of ['color-bg-app', 'color-bg-surface', 'color-bg-elevated', 'color-bg-code']) {
        expect(contrast(tokens[c], tokens[bg]), `${c} on ${bg}`).toBeGreaterThanOrEqual(4.5)
      }
    }
  })

  it('dark text on every solid accent or status fill (buttons, pills, stage numbers) reaches 4.5:1', () => {
    for (const fill of ['color-accent-primary', 'color-accent-primary-hover', 'color-accent-primary-active', 'color-error', 'color-success',
      'diagram-client', 'diagram-mvc', 'diagram-security', 'diagram-business', 'diagram-persistence', 'diagram-database']) {
      expect(contrast(tokens['color-text-on-accent'], tokens[fill]), fill).toBeGreaterThanOrEqual(4.5)
    }
  })

  it('every syntax colour reaches 4.5:1 on the code panel', () => {
    for (const name of Object.keys(tokens).filter((n) => n.startsWith('syntax-'))) {
      expect(contrast(tokens[name], tokens['color-bg-code']), name).toBeGreaterThanOrEqual(4.5)
    }
  })

  it('the outline of a form control reaches 3:1 (WCAG 1.4.11) on the surfaces it sits on, and the focus colour too', () => {
    for (const bg of ['color-bg-app', 'color-bg-surface', 'color-bg-elevated']) {
      expect(contrast(tokens['color-border-input'], tokens[bg]), bg).toBeGreaterThanOrEqual(3)
      expect(contrast(tokens['color-border-focus'], tokens[bg]), `focus on ${bg}`).toBeGreaterThanOrEqual(3)
    }
  })

  it('disabled text is deliberately below the text threshold (it is not meant to be read as active)', () => {
    expect(contrast(tokens['color-text-disabled'], tokens['color-bg-surface'])).toBeLessThan(4.5)
  })
})

describe('no light-theme leftovers', () => {
  const files = []
  const walk = (dir) => {
    for (const name of readdirSync(dir)) {
      const full = join(dir, name)
      if (statSync(full).isDirectory()) walk(full)
      else if (/\.(jsx?|css)$/.test(name) && !/\.test\./.test(name) && name !== 'mappingFixture.js') files.push(full)
    }
  }
  walk(SRC)

  it('uses no raw palette class (slate, indigo, emerald, ...), bg-white or text-white in components', () => {
    const offenders = []
    const raw = /(?<![\w-])(?:bg|text|border|ring|divide|from|to|via|fill|stroke)-(?:white|black|slate|gray|indigo|violet|purple|emerald|green|amber|red|rose|sky|blue|cyan|teal)(?:-\d{2,3})?(?![\w-])/g
    for (const file of files.filter((f) => /\.jsx?$/.test(f))) {
      const hits = readFileSync(file, 'utf8').match(raw)
      if (hits) offenders.push(`${file.replace(SRC, '')}: ${[...new Set(hits)].join(', ')}`)
    }
    expect(offenders).toEqual([])
  })

  it('hard-codes no hex colour outside index.css (tokens are the only source)', () => {
    const offenders = []
    for (const file of files.filter((f) => /\.jsx?$/.test(f))) {
      const hits = readFileSync(file, 'utf8').match(/#[0-9a-fA-F]{6}\b/g)
      if (hits) offenders.push(`${file.replace(SRC, '')}: ${hits.join(', ')}`)
    }
    expect(offenders).toEqual([])
  })
})
