import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../api/labJwtApi', () => ({
  issueToken: vi.fn(),
  inspectToken: vi.fn(),
}))

import { inspectToken, issueToken } from '../api/labJwtApi'
import { GLOSSARY } from '../scenarios/jwtSecurity.sim'
import ComponentGlossary from './ComponentGlossary'
import JwtAnatomy from './JwtAnatomy'
import TokenWorkshop from './TokenWorkshop'
import WhatIfPanel from './WhatIfPanel'

afterEach(cleanup)

const stage = (id, label, status, detail = 'detail') => ({ id, label, status, detail, provenance: 'DERIVED' })
const forged = {
  length: 250, parts: 3,
  header: { alg: 'HS256' },
  claims: { sub: 'sam', role: 'ADMIN', iss: 'springlab-lab', exp: 1793545892 },
  signaturePreview: 'abcdefghijkl… (43 characters)', decodeError: null,
  verification: { valid: false, category: 'BAD_SIGNATURE', frameworkMessage: 'Signed JWT rejected: Invalid signature', provenance: 'CAPTURED' },
  stages: [
    stage('structure', 'Three dot-separated parts', 'PASSED'), stage('decoding', 'Header and payload decode to JSON', 'PASSED'),
    stage('algorithm', 'Algorithm is the expected HS256', 'PASSED'),
    stage('signature', 'Signature verifies with the key', 'FAILED', 'the signature does not match'),
    stage('expiry', 'Not expired (exp)', 'NOT_REACHED'), stage('notBefore', 'Already valid (nbf)', 'NOT_REACHED'),
    stage('issuer', 'Issuer is the expected one (iss)', 'NOT_REACHED'),
  ],
  stagesAgreeWithDecoder: true,
}
const accepted = { ...forged, claims: { ...forged.claims, role: 'STUDENT' }, verification: { valid: true, category: 'VALID', frameworkMessage: 'accepted by the lab JwtDecoder', provenance: 'CAPTURED' }, stages: forged.stages.map((s) => ({ ...s, status: 'PASSED' })) }

describe('JwtAnatomy: decoding versus verification', () => {
  it('shows the forged claims in the decode panel even though the verify panel rejects the token', () => {
    render(<JwtAnatomy inspection={forged} token="aaa.bbb.ccc" />)
    const decode = screen.getByTestId('decode-panel')
    const verify = screen.getByTestId('verify-panel')

    expect(within(decode).getByText('ADMIN')).toBeTruthy() // readable...
    expect(within(decode).getByText(/no key needed/i)).toBeTruthy()
    expect(screen.getByTestId('verdict').textContent).toContain('Rejected: BAD_SIGNATURE') // ...but not trustworthy
    expect(within(verify).getByText(/needs the key/i)).toBeTruthy()
    expect(screen.getByTestId('framework-message').textContent).toContain('Invalid signature')
  })

  it('explains that decoding is not verifying', () => {
    render(<JwtAnatomy inspection={forged} />)
    expect(screen.getByRole('note').textContent).toMatch(/Decoding is not verifying/)
  })

  it('lists the checks in order with failed and not-reached ones distinguished', () => {
    render(<JwtAnatomy inspection={forged} />)
    const items = within(screen.getByRole('list', { name: 'Verification checks' })).getAllByRole('listitem')

    expect(items.map((li) => li.getAttribute('data-status'))).toEqual(
      ['PASSED', 'PASSED', 'PASSED', 'FAILED', 'NOT_REACHED', 'NOT_REACHED', 'NOT_REACHED'])
    expect(items[3].textContent).toContain('the signature does not match')
    expect(items[4].textContent).toMatch(/not reached/i) // status is also conveyed in text, not only styling
  })

  it('labels the real decoder verdict as LIVE and the reconstructed check list as INFERRED', () => {
    render(<JwtAnatomy inspection={forged} />)
    const verify = screen.getByTestId('verify-panel')
    const badges = within(verify).getAllByTestId('mode-badge').map((b) => b.textContent)

    expect(badges.some((t) => t.includes('LIVE'))).toBe(true)
    expect(badges.some((t) => t.includes('INFERRED'))).toBe(true)
    expect(within(verify).getByText(/rebuilt by running the same framework components/i)).toBeTruthy()
  })

  it('shows an accepted token as accepted', () => {
    render(<JwtAnatomy inspection={accepted} />)
    expect(screen.getByTestId('verdict').textContent).toContain('Accepted by the JwtDecoder')
  })

  it('colours the three parts of the token and says it is a lab token', () => {
    render(<JwtAnatomy inspection={forged} token="HEADERPART.PAYLOADPART.SIGPART" />)
    const line = screen.getByLabelText(/three parts coloured/i)
    expect(within(line).getByTitle('header').textContent).toBe('HEADERPART')
    expect(within(line).getByTitle('payload').textContent).toBe('PAYLOADPART')
    expect(within(line).getByTitle('signature').textContent).toBe('SIGPART')
    expect(screen.getByText(/never the application's real key/)).toBeTruthy()
  })

  it('renders nothing without an inspection', () => {
    const { container } = render(<JwtAnatomy inspection={null} />)
    expect(container.innerHTML).toBe('')
  })
})

describe('ComponentGlossary', () => {
  const facts = {
    filterChains: [{ matcher: 'lab chain', filters: [] }, { matcher: 'main chain', filters: [] }],
    authenticationManager: { type: 'ProviderManager', providers: ['DaoAuthenticationProvider'] },
    userDetailsService: 'AppUserDetailsService', passwordEncoder: 'DelegatingPasswordEncoder',
    jwtDecoder: { type: 'NimbusJwtDecoder', beanCount: 1 }, jwtAuthenticationConverter: 'DatabaseBackedJwtAuthenticationConverter',
  }

  it('lists every component and where SpringLab uses it (login versus bearer)', () => {
    render(<ComponentGlossary glossary={GLOSSARY} facts={facts} codeRefs={{}} />)
    expect(screen.getAllByRole('article')).toHaveLength(GLOSSARY.length)
    expect(within(screen.getByTestId('component-userDetailsService')).getByText('LOGIN only')).toBeTruthy()
    expect(within(screen.getByTestId('component-jwtDecoder')).getByText('BEARER requests')).toBeTruthy()
    expect(within(screen.getByTestId('component-authenticationManager')).getByText('LOGIN and BEARER')).toBeTruthy()
  })

  it('shows values read from the running application', () => {
    render(<ComponentGlossary glossary={GLOSSARY} facts={facts} codeRefs={{}} />)
    expect(screen.getByTestId('fact-authenticationManager').textContent).toContain('ProviderManager with providers [DaoAuthenticationProvider]')
    expect(screen.getByTestId('fact-userDetailsService').textContent).toContain('AppUserDetailsService')
    expect(screen.getByTestId('fact-jwtDecoder').textContent).toContain('NimbusJwtDecoder (1 bean)')
  })

  it('says so when the live value is not available instead of inventing one', () => {
    render(<ComponentGlossary glossary={GLOSSARY} facts={null} factsError="Network Error" codeRefs={{}} />)
    expect(screen.getByText(/Could not read the running configuration: Network Error/)).toBeTruthy()
    expect(screen.getByTestId('fact-jwtDecoder').textContent).toContain('loading')
  })

  it('shows "(not reported)" for every live value when the backend sends partial metadata, and still renders', () => {
    render(<ComponentGlossary glossary={GLOSSARY} facts={{}} codeRefs={{}} />)
    expect(screen.getAllByRole('article')).toHaveLength(GLOSSARY.length)
    expect(screen.getByTestId('fact-authenticationManager').textContent).toContain('(not reported)')
    expect(screen.getByTestId('fact-jwtDecoder').textContent).toContain('(not reported)')
    expect(screen.getByTestId('component-authenticationManager').textContent).toContain('What it does')
  })

  it('distinguishes tested statements from documented framework behaviour', () => {
    render(<ComponentGlossary glossary={GLOSSARY} facts={facts} codeRefs={{}} />)
    expect(within(screen.getByTestId('component-userDetailsService')).getByText(/verified by an automated test/)).toBeTruthy()
    expect(within(screen.getByTestId('component-authenticationProvider')).getByText(/documented by Spring Security/)).toBeTruthy()
  })

  it('reveals the SpringLab source on demand', () => {
    const codeRefs = { 'user-details-service': { id: 'user-details-service', title: 'AppUserDetailsService', file: 'x.java', startLine: 1, endLine: 1, language: 'java', excerpt: 'class A {}', notes: [{ line: 1, token: 'class', text: 'A note.' }] } }
    render(<ComponentGlossary glossary={GLOSSARY} facts={facts} codeRefs={codeRefs} />)
    const entry = screen.getByTestId('component-userDetailsService')
    fireEvent.click(within(entry).getByRole('button', { name: 'See the code' }))
    expect(within(entry).getByText('A note.')).toBeTruthy()
    fireEvent.click(within(entry).getByRole('button', { name: 'Hide the code' }))
    expect(within(entry).queryByText('A note.')).toBeNull()
  })
})

describe('TokenWorkshop', () => {
  const variants = [
    { id: 'VALID', title: 'Valid token', description: 'Fine.', issued: true },
    { id: 'EXPIRED', title: 'Expired token', description: 'Old.', issued: true },
    { id: 'NO_TOKEN', title: 'No token', description: 'None.', issued: false },
  ]
  beforeEach(() => vi.clearAllMocks())

  it('offers only kinds the server can issue (not "no token")', () => {
    render(<TokenWorkshop variants={variants} />)
    const options = within(screen.getByLabelText('Kind of token')).getAllByRole('option').map((o) => o.textContent)
    expect(options).toEqual(['Valid token', 'Expired token'])
  })

  it('issues the chosen kind with the chosen claimed role and shows the anatomy', async () => {
    issueToken.mockResolvedValue({ token: 'a.b.c', inspection: forged })
    render(<TokenWorkshop variants={variants} />)
    fireEvent.change(screen.getByLabelText('Kind of token'), { target: { value: 'EXPIRED' } })
    fireEvent.change(screen.getByLabelText('Role the token claims'), { target: { value: 'ADMIN' } })
    fireEvent.click(screen.getByRole('button', { name: 'Issue and inspect' }))

    await waitFor(() => expect(screen.getByTestId('verdict')).toBeTruthy())
    expect(issueToken).toHaveBeenCalledWith('EXPIRED', 'ADMIN')
  })

  it('inspects a pasted token and trims it; the button stays disabled while empty', async () => {
    inspectToken.mockResolvedValue(forged)
    render(<TokenWorkshop variants={variants} />)
    const button = screen.getByRole('button', { name: 'Inspect' })
    expect(button).toHaveProperty('disabled', true)
    fireEvent.change(screen.getByLabelText('Token'), { target: { value: '  x.y.z  ' } })
    expect(button).toHaveProperty('disabled', false)
    fireEvent.click(button)

    await waitFor(() => expect(inspectToken).toHaveBeenCalledWith('x.y.z'))
    await waitFor(() => expect(screen.getByTestId('verdict')).toBeTruthy())
  })

  it('reports a failure instead of showing stale results', async () => {
    issueToken.mockRejectedValue({ response: { status: 400, data: { detail: 'Validation failed' } } })
    render(<TokenWorkshop variants={variants} />)
    fireEvent.click(screen.getByRole('button', { name: 'Issue and inspect' }))

    await waitFor(() => expect(screen.getByRole('alert').textContent).toContain('Validation failed'))
    expect(screen.queryByTestId('verdict')).toBeNull()
  })
})

describe('WhatIfPanel with per-result details', () => {
  const trace = (status, handlerInvoked, sqlCount, challenge, ids) => ({
    status, handlerInvoked, sqlCount,
    steps: ids.map((id) => ({ id, evidence: id === 'token-validation' && challenge ? [{ label: 'Observed WWW-Authenticate', value: challenge }] : [] })),
  })
  const exp = {
    id: 'expired-token', kind: 'LIVE', title: 'An expired token', changes: 'exp is in the past.', consequence: 'The decoder refuses it.',
    expectation: { '*': { status: 401, handlerInvoked: false, sqlCount: 0, challengeContains: 'expired' } },
  }
  const result = {
    status: 'done',
    baseline: { trace: trace(200, true, 1, null, ['client-request', 'database-lookup', 'controller-reached']) },
    experiment: { trace: trace(401, false, 0, 'Bearer error="invalid_token", error_description="Jwt expired at 2026"', ['client-request', 'token-validation']) },
  }

  it('compares with the normal request, checks the documented challenge and renders the injected details', () => {
    render(
      <WhatIfPanel experiments={[exp]} results={{ [exp.id]: result }} role="STUDENT" meta={null} codeRefs={{}}
        onRun={() => {}} onShowTrace={() => {}} renderDetails={(e) => <p>details for {e.id}</p>} />,
    )
    expect(screen.getByText('details for expired-token')).toBeTruthy()
    expect(screen.getByText(/Server's WWW-Authenticate mentions "expired": documented yes, observed yes/)).toBeTruthy()
    const table = screen.getByRole('table')
    expect(within(table).getByText('database-lookup, controller-reached')).toBeTruthy() // what the experiment skipped
  })

  it('flags an observation that differs from the documented one', () => {
    const wrong = { ...result, experiment: { trace: trace(200, true, 1, null, ['client-request']) } }
    render(<WhatIfPanel experiments={[exp]} results={{ [exp.id]: wrong }} role="STUDENT" meta={null} codeRefs={{}}
      onRun={() => {}} onShowTrace={() => {}} />)
    expect(screen.getByText(/differs from what this lesson documents/)).toBeTruthy()
  })

  it('works without a details renderer (the REST lifecycle module does not pass one)', () => {
    render(<WhatIfPanel experiments={[exp]} results={{ [exp.id]: result }} role="STUDENT" meta={null} codeRefs={{}}
      onRun={() => {}} onShowTrace={() => {}} />)
    expect(screen.getByRole('button', { name: 'Run again' })).toBeTruthy()
  })
})
