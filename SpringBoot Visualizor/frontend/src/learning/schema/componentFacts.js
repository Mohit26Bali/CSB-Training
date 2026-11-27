/**
 * Turns the running application's component facts (GET /api/lab/jwt/components) into the one-line statements shown
 * next to each glossary entry. Returns null when the backend has no value, so the UI can say so instead of guessing.
 */
export function factText(facts, key) {
  if (!facts || !key) return null
  const text = (v) => (typeof v === 'string' && v.length > 0 ? v : null)
  switch (key) {
    case 'filterChains': {
      const chains = facts.filterChains
      if (!Array.isArray(chains) || chains.length === 0) return null
      return `${chains.length} chain(s): ${chains.map((c) => c?.matcher ?? '(unknown)').join('  |  ')}`
    }
    case 'authenticationManager': {
      const m = facts.authenticationManager
      if (!text(m?.type)) return null
      const providers = Array.isArray(m.providers) ? m.providers : []
      // An empty provider list means the backend could not report them; say that rather than show "[]".
      return providers.length ? `${m.type} with providers [${providers.join(', ')}]` : `${m.type} (providers not reported)`
    }
    case 'authenticationManager.providers': {
      const providers = facts.authenticationManager?.providers
      return Array.isArray(providers) && providers.length ? providers.join(', ') : null
    }
    case 'userDetailsService':
      return text(facts.userDetailsService)
    case 'passwordEncoder':
      return text(facts.passwordEncoder)
    case 'jwtDecoder': {
      const d = facts.jwtDecoder
      return text(d?.type) ? `${d.type} (${Number.isFinite(d.beanCount) ? d.beanCount : '?'} bean)` : null
    }
    case 'jwtAuthenticationConverter':
      return text(facts.jwtAuthenticationConverter)
    default:
      return null
  }
}
