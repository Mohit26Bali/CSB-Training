import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useSimulation } from './useSimulation'

describe('useSimulation (timer behaviour)', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('auto-advances on the interval while playing and finishes at the end', () => {
    const { result } = renderHook(() => useSimulation(3, { intervalMs: 1000 }))

    act(() => result.current.play())
    expect(result.current.isPlaying).toBe(true)
    act(() => vi.advanceTimersByTime(1000))
    expect(result.current.index).toBe(1)
    act(() => vi.advanceTimersByTime(1000))
    expect(result.current.index).toBe(2)
    expect(result.current.status).toBe('finished')
    act(() => vi.advanceTimersByTime(5000))
    expect(result.current.index).toBe(2) // nothing further happens after the end
  })

  it('pause stops the timer: no further steps, even after a long wait', () => {
    const { result } = renderHook(() => useSimulation(5, { intervalMs: 1000 }))
    act(() => result.current.play())
    act(() => vi.advanceTimersByTime(1000))
    act(() => result.current.pause())
    act(() => vi.advanceTimersByTime(10_000))

    expect(result.current.index).toBe(1)
    expect(result.current.status).toBe('paused')
  })

  it('speed shortens the interval', () => {
    const { result } = renderHook(() => useSimulation(5, { intervalMs: 1000 }))
    act(() => result.current.setSpeed(2))
    act(() => result.current.play())
    act(() => vi.advanceTimersByTime(500))
    expect(result.current.index).toBe(1)
  })

  it('manual next while playing pauses auto-play', () => {
    const { result } = renderHook(() => useSimulation(5, { intervalMs: 1000 }))
    act(() => result.current.play())
    act(() => result.current.next())
    act(() => vi.advanceTimersByTime(5000))
    expect(result.current.index).toBe(1)
    expect(result.current.status).toBe('paused')
  })

  it('reset stops the timer and returns to step one', () => {
    const { result } = renderHook(() => useSimulation(5, { intervalMs: 1000 }))
    act(() => result.current.play())
    act(() => vi.advanceTimersByTime(2000))
    act(() => result.current.reset())
    act(() => vi.advanceTimersByTime(5000))
    expect(result.current).toMatchObject({ index: 0, status: 'idle' })
  })

  it('clears its timer on unmount (no timers left running)', () => {
    const { result, unmount } = renderHook(() => useSimulation(5, { intervalMs: 1000 }))
    act(() => result.current.play())
    expect(vi.getTimerCount()).toBe(1)
    unmount()
    expect(vi.getTimerCount()).toBe(0)
  })

  describe('reduced motion', () => {
    it('refuses to auto-play but still supports stepping', () => {
      const { result } = renderHook(() => useSimulation(4, { intervalMs: 1000, autoplayAllowed: false }))

      act(() => result.current.play())
      expect(result.current.status).toBe('idle')
      expect(vi.getTimerCount()).toBe(0)
      act(() => result.current.next())
      act(() => result.current.next())
      expect(result.current.index).toBe(2)
      act(() => result.current.back())
      expect(result.current.index).toBe(1)
    })

    it('stops an auto-play already in progress when reduced motion is switched on', () => {
      let allowed = true
      const { result, rerender } = renderHook(() => useSimulation(5, { intervalMs: 1000, autoplayAllowed: allowed }))
      act(() => result.current.play())
      expect(result.current.isPlaying).toBe(true)

      allowed = false
      rerender()
      expect(result.current.isPlaying).toBe(false)
      act(() => vi.advanceTimersByTime(5000))
      expect(result.current.index).toBe(0)
    })
  })
})
