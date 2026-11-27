import { useState } from 'react'
import type { Category } from '../api/types'

const icons: Record<Category, string> = {
  LAPTOP: 'M4 5h16v10H4zM2 19h20',
  MONITOR: 'M3 4h18v12H3zM8 20h8M12 16v4',
  PHONE: 'M8 2h8a1 1 0 011 1v18a1 1 0 01-1 1H8a1 1 0 01-1-1V3a1 1 0 011-1zM11 18h2',
  TABLET: 'M5 3h14a1 1 0 011 1v16a1 1 0 01-1 1H5a1 1 0 01-1-1V4a1 1 0 011-1zM11 18h2',
  ACCESSORY: 'M12 3a4 4 0 014 4v4a4 4 0 01-8 0V7a4 4 0 014-4zM12 3v6',
  OTHER: 'M21 8l-9-5-9 5v8l9 5 9-5zM3 8l9 5 9-5M12 13v8',
}

/** A picture for the kind of equipment (/assets/<category>.jpg), or a line icon on a stone tile when it is missing. */
export function AssetPhoto({ category, className = '' }: { category: Category; className?: string }) {
  const [failed, setFailed] = useState(false)
  if (!failed) {
    return <img src={`/assets/${category.toLowerCase()}.jpg`} alt="" loading="lazy" onError={() => setFailed(true)} className={`object-cover ${className}`} />
  }
  return (
    <div aria-hidden="true" className={`flex items-center justify-center bg-linear-to-br from-stone-200 to-stone-300 text-stone-600 dark:from-stone-700 dark:to-stone-800 dark:text-stone-300 ${className}`}>
      <svg viewBox="0 0 24 24" className="size-10" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <path d={icons[category]} />
      </svg>
    </div>
  )
}
