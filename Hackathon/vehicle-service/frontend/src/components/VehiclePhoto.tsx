import { useState } from 'react'
import type { VehicleType } from '../api/types'

const gradients: Record<VehicleType, string> = {
  CAR: 'from-green-700 to-slate-800',
  SUV: 'from-emerald-700 to-slate-800',
  BIKE: 'from-lime-700 to-slate-800',
  VAN: 'from-teal-700 to-slate-800',
}

/** A picture for the vehicle's kind (two per kind, picked by id), or a coloured tile when it is missing. */
export function VehiclePhoto({ type, id, className = '' }: { type: VehicleType; id: number; className?: string }) {
  const [failed, setFailed] = useState(false)
  if (!failed) {
    return <img src={`/vehicles/${type.toLowerCase()}-${(id % 2) + 1}.jpg`} alt="" loading="lazy" onError={() => setFailed(true)} className={`object-cover ${className}`} />
  }
  return (
    <div aria-hidden="true" className={`flex items-center justify-center bg-linear-to-br text-3xl font-semibold text-white/80 ${gradients[type]} ${className}`}>
      {type.charAt(0)}
    </div>
  )
}

/** The number plate look used wherever a registration number is shown. */
export function Plate({ number }: { number: string }) {
  return <span className="inline-block rounded border-2 border-slate-800 bg-amber-100 px-2 py-0.5 font-mono text-xs font-bold tracking-wider text-slate-900">{number}</span>
}
