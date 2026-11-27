import { describe, expect, it } from 'vitest'
import { canGoBack, canGoNext, initialState, simulationReducer as reduce } from './simulationReducer'

const run = (state, ...types) => types.reduce((s, t) => reduce(s, typeof t === 'string' ? { type: t } : t), state)

describe('simulationReducer', () => {
  it('starts idle on the first step', () => {
    expect(initialState(5)).toEqual({ status: 'idle', index: 0, total: 5, speed: 1 })
  })

  describe('play and pause', () => {
    it('play starts auto-advancing from where it is', () => {
      const s = run(initialState(5), 'NEXT', 'PLAY')
      expect(s).toMatchObject({ status: 'playing', index: 1 })
    })

    it('pause stops playing and keeps the step', () => {
      const s = run(initialState(5), 'PLAY', { type: 'TICK' }, 'PAUSE')
      expect(s).toMatchObject({ status: 'paused', index: 1 })
    })

    it('pause is a no-op unless playing', () => {
      const idle = initialState(5)
      expect(reduce(idle, { type: 'PAUSE' })).toBe(idle)
    })

    it('play on a finished simulation restarts from the beginning', () => {
      const finished = run(initialState(3), 'NEXT', 'NEXT')
      expect(finished.status).toBe('finished')
      expect(reduce(finished, { type: 'PLAY' })).toMatchObject({ status: 'playing', index: 0 })
    })

    it('does nothing for an empty scenario', () => {
      const empty = initialState(0)
      for (const type of ['PLAY', 'NEXT', 'BACK', 'GOTO']) expect(reduce(empty, { type, index: 3 })).toBe(empty)
    })
  })

  describe('next and back', () => {
    it('next advances one step and pauses', () => {
      expect(run(initialState(5), 'NEXT')).toMatchObject({ status: 'paused', index: 1 })
    })

    it('next while playing takes manual control (pauses)', () => {
      const s = run(initialState(5), 'PLAY', 'NEXT')
      expect(s).toMatchObject({ status: 'paused', index: 1 })
    })

    it('reaching the last step with next finishes the simulation', () => {
      expect(run(initialState(3), 'NEXT', 'NEXT')).toMatchObject({ status: 'finished', index: 2 })
    })

    it('next on the last step never goes past the end', () => {
      const s = run(initialState(2), 'NEXT', 'NEXT', 'NEXT')
      expect(s).toMatchObject({ status: 'finished', index: 1 })
    })

    it('back moves one step earlier and pauses', () => {
      expect(run(initialState(5), 'NEXT', 'NEXT', 'BACK')).toMatchObject({ status: 'paused', index: 1 })
    })

    it('back from a finished simulation leaves the finished state', () => {
      const s = run(initialState(2), 'NEXT', 'BACK')
      expect(s).toMatchObject({ status: 'paused', index: 0 })
    })

    it('back on the first step stays put, but still stops auto-play', () => {
      const idle = initialState(3)
      expect(reduce(idle, { type: 'BACK' })).toBe(idle)
      expect(run(idle, 'PLAY', 'BACK')).toMatchObject({ status: 'paused', index: 0 })
    })
  })

  describe('auto-play ticks', () => {
    it('tick advances while playing and finishes on the last step', () => {
      const s = run(initialState(3), 'PLAY', 'TICK', 'TICK')
      expect(s).toMatchObject({ status: 'finished', index: 2 })
    })

    it('a stale tick cannot move a paused or idle simulation', () => {
      const paused = run(initialState(4), 'PLAY', 'PAUSE')
      expect(reduce(paused, { type: 'TICK' })).toBe(paused)
      const idle = initialState(4)
      expect(reduce(idle, { type: 'TICK' })).toBe(idle)
    })

    it('a stale tick cannot move a finished simulation', () => {
      const finished = run(initialState(2), 'NEXT')
      expect(reduce(finished, { type: 'TICK' })).toBe(finished)
    })
  })

  describe('reset and goTo', () => {
    it('reset returns to the first step, idle, keeping the chosen speed', () => {
      const s = run(initialState(5), { type: 'SET_SPEED', speed: 2 }, 'PLAY', 'TICK', 'RESET')
      expect(s).toEqual({ status: 'idle', index: 0, total: 5, speed: 2 })
    })

    it('goTo jumps, pauses and clamps out-of-range indexes', () => {
      const s = initialState(5)
      expect(reduce(s, { type: 'GOTO', index: 2 })).toMatchObject({ index: 2, status: 'paused' })
      expect(reduce(s, { type: 'GOTO', index: 99 })).toMatchObject({ index: 4, status: 'finished' })
      expect(reduce(s, { type: 'GOTO', index: -4 })).toMatchObject({ index: 0, status: 'idle' })
      expect(reduce(run(s, 'PLAY'), { type: 'GOTO', index: 1 }).status).toBe('paused')
    })
  })

  describe('speed', () => {
    it('accepts only the supported speeds', () => {
      const s = initialState(3)
      expect(reduce(s, { type: 'SET_SPEED', speed: 0.5 }).speed).toBe(0.5)
      expect(reduce(s, { type: 'SET_SPEED', speed: 2 }).speed).toBe(2)
      expect(reduce(s, { type: 'SET_SPEED', speed: 100 })).toBe(s)
    })
  })

  it('ignores unknown actions', () => {
    const s = initialState(3)
    expect(reduce(s, { type: 'NOPE' })).toBe(s)
  })

  it('exposes whether next and back are possible', () => {
    const first = initialState(3)
    const last = run(first, 'NEXT', 'NEXT')
    expect([canGoBack(first), canGoNext(first)]).toEqual([false, true])
    expect([canGoBack(last), canGoNext(last)]).toEqual([true, false])
  })
})
