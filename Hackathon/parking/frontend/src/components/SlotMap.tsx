import type { Slot } from '../api/types'

export const floorLabel = (floor: number) => (floor === 0 ? 'Ground floor' : `Floor ${floor}`)

function tileClass(slot: Slot, selected: boolean) {
  const base = 'flex h-16 flex-col items-center justify-center rounded-lg border text-xs font-semibold transition'
  const ring = selected ? ' ring-2 ring-brand-600' : ''
  if (slot.occupied) return `${base} border-rose-200 bg-rose-50 text-rose-700${ring}`
  if (slot.status === 'RESERVED') return `${base} border-violet-200 bg-violet-50 text-violet-700${ring}`
  if (slot.status === 'MAINTENANCE') return `${base} border-amber-200 bg-amber-50 text-amber-700${ring}`
  return `${base} border-emerald-200 bg-emerald-50 text-emerald-700${ring}`
}

function tileLabel(slot: Slot) {
  if (slot.occupied) return 'In use'
  if (slot.status === 'RESERVED') return 'Reserved'
  if (slot.status === 'MAINTENANCE') return 'Closed'
  return 'Free'
}

/** The lot, floor by floor, as a grid of coloured tiles. onPick makes the tiles clickable. */
export function SlotMap({ slots, selectedId, onPick }: { slots: Slot[]; selectedId?: number | null; onPick?: (slot: Slot) => void }) {
  const floors = [...new Set(slots.map((s) => s.floor))].sort((a, b) => a - b)
  return (
    <div className="space-y-6">
      {floors.map((floor) => (
        <section key={floor}>
          <h3 className="mb-2 text-sm font-semibold text-slate-700">{floorLabel(floor)}</h3>
          <ul className="grid grid-cols-4 gap-2 sm:grid-cols-6 lg:grid-cols-8">
            {slots
              .filter((s) => s.floor === floor)
              .map((s) => (
                <li key={s.id}>
                  <button
                    type="button"
                    disabled={!onPick}
                    onClick={() => onPick?.(s)}
                    title={s.occupantName ? `${s.occupantName}, ${s.occupantVehicle}` : tileLabel(s)}
                    className={`w-full ${tileClass(s, selectedId === s.id)} ${onPick ? 'cursor-pointer hover:brightness-95' : 'cursor-default'}`}
                  >
                    <span>{s.code}</span>
                    <span className="text-[10px] font-medium opacity-80">{tileLabel(s)}</span>
                  </button>
                </li>
              ))}
          </ul>
        </section>
      ))}
      <ul className="flex flex-wrap gap-4 text-xs text-slate-600">
        {[
          ['bg-emerald-200', 'Free'],
          ['bg-rose-200', 'In use'],
          ['bg-violet-200', 'Reserved'],
          ['bg-amber-200', 'Closed'],
        ].map(([color, label]) => (
          <li key={label} className="flex items-center gap-1.5">
            <span className={`size-3 rounded ${color}`} />
            {label}
          </li>
        ))}
      </ul>
    </div>
  )
}
