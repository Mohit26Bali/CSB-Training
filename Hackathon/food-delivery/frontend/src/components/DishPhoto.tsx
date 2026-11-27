import { useState } from 'react'
import type { MenuCategory } from '../api/types'

const emoji: Record<MenuCategory, string> = {
  STARTERS: '🍢',
  MAINS: '🍛',
  BREADS_AND_RICE: '🫓',
  DESSERTS: '🍮',
  DRINKS: '🥤',
}

const tint: Record<MenuCategory, string> = {
  STARTERS: 'from-amber-200 to-orange-300',
  MAINS: 'from-orange-300 to-amber-600',
  BREADS_AND_RICE: 'from-yellow-100 to-amber-300',
  DESSERTS: 'from-rose-200 to-amber-200',
  DRINKS: 'from-sky-200 to-amber-100',
}

/** The dish picture (/dishes/<imageKey>.jpg), or a warm tile with an emoji when it is missing. */
export function DishPhoto({ imageKey, category, className = '' }: { imageKey: string; category: MenuCategory; className?: string }) {
  const [failed, setFailed] = useState(false)
  if (!failed) {
    return <img src={`/dishes/${imageKey}.jpg`} alt="" loading="lazy" onError={() => setFailed(true)} className={`object-cover ${className}`} />
  }
  return (
    <div aria-hidden="true" className={`flex items-center justify-center bg-linear-to-br text-4xl ${tint[category]} ${className}`}>
      {emoji[category]}
    </div>
  )
}

/** The green/red dot used on Indian menus. */
export function VegMark({ vegetarian }: { vegetarian: boolean }) {
  const color = vegetarian ? 'border-emerald-600 text-emerald-600' : 'border-rose-700 text-rose-700'
  return (
    <span title={vegetarian ? 'Vegetarian' : 'Non-vegetarian'} className={`inline-grid size-4 shrink-0 place-items-center border-2 ${color}`}>
      <span className="size-2 rounded-full bg-current" />
    </span>
  )
}
