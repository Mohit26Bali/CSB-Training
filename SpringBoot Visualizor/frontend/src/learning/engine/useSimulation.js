import { useCallback, useEffect, useMemo, useReducer } from 'react'
import { canGoBack, canGoNext, initialState, simulationReducer } from './simulationReducer'

/**
 * Drives a step-by-step simulation.
 *
 * @param total            number of steps
 * @param intervalMs       how long each step stays up while auto-playing (divided by the chosen speed)
 * @param autoplayAllowed  false when the learner prefers reduced motion: stepping still works, auto-play does not
 *
 * A new scenario must remount the component using this hook (give it a different `key`).
 */
export function useSimulation(total, { intervalMs = 2800, autoplayAllowed = true } = {}) {
  const [state, dispatch] = useReducer(simulationReducer, total, initialState)

  useEffect(() => {
    if (state.status !== 'playing') return undefined
    const timer = setTimeout(() => dispatch({ type: 'TICK' }), intervalMs / state.speed)
    return () => clearTimeout(timer) // runs on pause, step change, speed change and unmount
  }, [state.status, state.index, state.speed, intervalMs])

  // If reduced motion is switched on while playing, stop immediately.
  useEffect(() => {
    if (!autoplayAllowed && state.status === 'playing') dispatch({ type: 'PAUSE' })
  }, [autoplayAllowed, state.status])

  const play = useCallback(() => {
    if (autoplayAllowed) dispatch({ type: 'PLAY' })
  }, [autoplayAllowed])
  const pause = useCallback(() => dispatch({ type: 'PAUSE' }), [])
  const next = useCallback(() => dispatch({ type: 'NEXT' }), [])
  const back = useCallback(() => dispatch({ type: 'BACK' }), [])
  const reset = useCallback(() => dispatch({ type: 'RESET' }), [])
  const goTo = useCallback((index) => dispatch({ type: 'GOTO', index }), [])
  const setSpeed = useCallback((speed) => dispatch({ type: 'SET_SPEED', speed }), [])

  return useMemo(
    () => ({
      ...state,
      autoplayAllowed,
      canNext: canGoNext(state),
      canBack: canGoBack(state),
      isPlaying: state.status === 'playing',
      play,
      pause,
      next,
      back,
      reset,
      goTo,
      setSpeed,
    }),
    [state, autoplayAllowed, play, pause, next, back, reset, goTo, setSpeed],
  )
}
