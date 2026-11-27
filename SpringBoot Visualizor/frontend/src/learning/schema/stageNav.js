import { STAGES } from './stages'

/** Which stage a location hash belongs to: "#stage-observe" itself, or an in-page link to something inside a stage. */
export function resolveStage(hash) {
  const value = (hash ?? '').replace(/^#/, '')
  if (value.startsWith('stage-')) {
    const id = value.slice('stage-'.length)
    return STAGES.some((s) => s.id === id) ? id : null
  }
  if (value.startsWith('experiment-')) return 'experiment'
  return null
}

/** Moves a lesson page to a stage (for example after an experiment finished). Works even when the hash is already that stage. */
export function goToStage(id) {
  window.history.replaceState(null, '', `#stage-${id}`)
  window.dispatchEvent(new HashChangeEvent('hashchange'))
}
