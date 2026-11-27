import { useState } from 'react'

const gradients = ['from-teal-600 to-emerald-800', 'from-sky-600 to-indigo-800', 'from-violet-600 to-fuchsia-800', 'from-amber-500 to-rose-700']

/** The room-type photo, or a coloured tile when the picture is missing or fails to load. */
export function RoomImage({ type, className = '' }: { type: string; className?: string }) {
  const [failedFor, setFailedFor] = useState<string | null>(null)
  if (failedFor !== type) {
    return <img src={`/rooms/${type.toLowerCase()}.jpg`} alt="" loading="lazy" onError={() => setFailedFor(type)} className={`object-cover ${className}`} />
  }
  const hash = [...type].reduce((sum, ch) => sum + ch.charCodeAt(0), 0)
  return (
    <div aria-hidden="true" className={`flex items-center justify-center bg-linear-to-br text-3xl font-semibold text-white/90 ${gradients[hash % gradients.length]} ${className}`}>
      {type.charAt(0)}
    </div>
  )
}

/** Beds taken out of beds available, as a bar. */
export function Occupancy({ occupied, capacity }: { occupied: number; capacity: number }) {
  const pct = capacity === 0 ? 0 : Math.min(100, Math.round((100 * occupied) / capacity))
  return (
    <div role="img" aria-label={`${occupied} of ${capacity} beds taken`} className="h-1.5 overflow-hidden rounded-full bg-slate-100">
      <div className={`h-full rounded-full ${occupied >= capacity ? 'bg-rose-500' : pct >= 70 ? 'bg-amber-500' : 'bg-emerald-500'}`} style={{ width: `${pct}%` }} />
    </div>
  )
}
