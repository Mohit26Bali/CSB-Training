/**
 * The simulation state machine, kept pure so it can be tested without a DOM.
 *
 * status:  'idle'      nothing started yet (index 0 shown)
 *          'playing'   auto-advancing on a timer
 *          'paused'    the learner is stepping manually
 *          'finished'  the last step is showing
 * index:   the step currently shown (0-based)
 * total:   number of steps (a scenario never changes length: a new scenario remounts the view)
 *
 * Rules worth knowing:
 *  - Next / Back / GoTo always pause auto-play first (the learner took control).
 *  - Play on a finished simulation (or on the last step) restarts from the beginning.
 *  - TICK is only honoured while playing, so a stale timer can never move a paused simulation.
 */
export const SPEEDS = [0.5, 1, 2]

export function initialState(total = 0) {
  return { status: 'idle', index: 0, total, speed: 1 }
}

export function simulationReducer(state, action) {
  const { total, index } = state
  const last = total - 1

  switch (action.type) {
    case 'PLAY': {
      if (total === 0) return state
      if (state.status === 'finished' || index >= last) return { ...state, index: 0, status: 'playing' }
      return { ...state, status: 'playing' }
    }
    case 'PAUSE':
      return state.status === 'playing' ? { ...state, status: 'paused' } : state
    case 'NEXT': {
      if (total === 0) return state
      if (index >= last) return { ...state, status: 'finished' }
      const next = index + 1
      return { ...state, index: next, status: next === last ? 'finished' : 'paused' }
    }
    case 'BACK': {
      if (total === 0) return state
      if (index === 0) return state.status === 'playing' ? { ...state, status: 'paused' } : state
      return { ...state, index: index - 1, status: 'paused' }
    }
    case 'RESET':
      return { ...initialState(total), speed: state.speed }
    case 'GOTO': {
      if (total === 0) return state
      const target = Math.min(Math.max(Number(action.index) || 0, 0), last)
      return { ...state, index: target, status: target === 0 ? 'idle' : target === last ? 'finished' : 'paused' }
    }
    case 'SET_SPEED':
      return SPEEDS.includes(action.speed) ? { ...state, speed: action.speed } : state
    case 'TICK': {
      if (state.status !== 'playing') return state
      if (index >= last) return { ...state, status: 'finished' }
      const next = index + 1
      return { ...state, index: next, status: next === last ? 'finished' : 'playing' }
    }
    default:
      return state
  }
}

export const canGoNext = (state) => state.index < state.total - 1
export const canGoBack = (state) => state.index > 0
