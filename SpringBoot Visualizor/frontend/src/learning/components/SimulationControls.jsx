import { buttonClass } from '../../components/ui/buttonStyles'
import { SPEEDS } from '../engine/simulationReducer'

const secondary = buttonClass({ variant: 'secondary', size: 'sm' })
const primary = buttonClass({ variant: 'primary', size: 'sm' })

/** Play / Pause / Back / Next / Reset for any simulation (the object returned by useSimulation). */
export default function SimulationControls({ sim, total }) {
  const playTitle = sim.autoplayAllowed
    ? undefined
    : 'Auto-play is off because your system asks for reduced motion. Use Next and Back.'
  return (
    <div role="group" aria-label="Simulation controls" className="flex flex-wrap items-center gap-2 rounded-lg border border-line bg-surface p-2 shadow-sm">
      {sim.isPlaying ? (
        <button type="button" onClick={sim.pause} className={primary}>
          Pause
        </button>
      ) : (
        <button type="button" onClick={sim.play} disabled={!sim.autoplayAllowed} title={playTitle} className={primary}>
          Play
        </button>
      )}
      <button type="button" onClick={sim.back} disabled={!sim.canBack} className={secondary}>
        Back
      </button>
      <button type="button" onClick={sim.next} disabled={!sim.canNext} className={primary}>
        Next step
      </button>
      <button type="button" onClick={sim.reset} disabled={sim.status === 'idle' && sim.index === 0} className={secondary}>
        Reset
      </button>
      <label className="ml-1 text-sm text-fg-2">
        Speed{' '}
        <select
          value={sim.speed}
          onChange={(e) => sim.setSpeed(Number(e.target.value))}
          disabled={!sim.autoplayAllowed}
          className="rounded-md border border-line-strong px-1 py-1"
        >
          {SPEEDS.map((s) => (
            <option key={s} value={s}>
              {s}x
            </option>
          ))}
        </select>
      </label>
      <span className="ml-auto rounded-full bg-subtle px-3 py-1 text-sm font-medium text-fg-2" data-testid="step-counter">
        Step {sim.index + 1} of {total}
      </span>
      {!sim.autoplayAllowed && (
        <p className="basis-full text-xs text-muted">Reduced motion is on: auto-play and animations are off.</p>
      )}
    </div>
  )
}
