import { useState } from 'react'
import type { RoomType } from '../api/types'

const gradients: Record<RoomType, string> = {
  STANDARD: 'from-sky-700 to-slate-800',
  DELUXE: 'from-indigo-700 to-slate-800',
  SUITE: 'from-amber-700 to-stone-800',
  FAMILY: 'from-emerald-700 to-slate-800',
}

/** The room's photo (two per type, picked by the room's id), or a coloured tile when it is missing. */
export function RoomPhoto({ type, id, className = '' }: { type: RoomType; id: number; className?: string }) {
  const [failed, setFailed] = useState(false)
  if (!failed) {
    return <img src={`/rooms/${type.toLowerCase()}-${(id % 2) + 1}.jpg`} alt="" loading="lazy" onError={() => setFailed(true)} className={`object-cover ${className}`} />
  }
  return (
    <div aria-hidden="true" className={`flex items-center justify-center bg-linear-to-br text-4xl font-semibold text-white/80 ${gradients[type]} ${className}`}>
      {type.charAt(0)}
    </div>
  )
}
