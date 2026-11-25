import { Link } from 'react-router-dom'
import type { MediaType } from '../api/types'
import { posterUrl } from '../lib/images'
import { formatRating } from '../lib/format'
import { Star } from './Icon'

/**
 * The poster tile for one movie or show: poster, rating badge, title and year, linking to its detail page
 * ({@code /title/:type/:id}). Used in rows (fixed width, snaps in a scroller) and in grids ({@code fluid}).
 * A title without a poster gets a coloured placeholder showing its name, so the layout never has holes.
 */
export interface CardTitle {
  id: number
  type: MediaType
  title: string | null
  posterPath: string | null
  year: number | null
  rating: number
}

const placeholders = ['from-rose-900 to-slate-900', 'from-indigo-900 to-slate-900', 'from-emerald-900 to-slate-900', 'from-amber-900 to-slate-900']

export function TitleCard({ title, fluid = false }: { title: CardTitle; fluid?: boolean }) {
  const poster = posterUrl(title.posterPath)
  const name = title.title ?? 'Untitled'
  return (
    <Link
      to={`/title/${title.type}/${title.id}`}
      aria-label={`${name}${title.year ? `, ${title.year}` : ''}`}
      className={`group block ${fluid ? 'w-full' : 'w-36 shrink-0 snap-start sm:w-40 lg:w-44'}`}
    >
      <div className="card-shadow relative aspect-2/3 overflow-hidden rounded-xl bg-surface ring-1 ring-line transition-all duration-300 group-hover:-translate-y-1 group-hover:ring-2 group-hover:ring-ember-400/70">
        {poster ? (
          <img src={poster} alt="" loading="lazy" decoding="async" className="size-full object-cover transition-transform duration-500 group-hover:scale-105" />
        ) : (
          <div className={`flex size-full items-center justify-center bg-linear-to-br p-3 text-center text-sm font-semibold text-white/80 ${placeholders[title.id % placeholders.length]}`}>
            {name}
          </div>
        )}
        {title.rating > 0 && (
          <span className="absolute left-2 top-2 inline-flex items-center gap-1 rounded-full bg-black/70 px-2 py-0.5 text-xs font-bold text-white backdrop-blur">
            <Star className="size-3 text-gold" /> {formatRating(title.rating)}
          </span>
        )}
      </div>
      <p className="clamp-2 mt-2 text-sm font-semibold leading-snug text-ink group-hover:text-ember-400">{name}</p>
      <p className="text-xs text-faint">
        {title.year ?? '—'} · {title.type === 'tv' ? 'TV' : 'Movie'}
      </p>
    </Link>
  )
}

export function CardSkeleton({ fluid = false }: { fluid?: boolean }) {
  return (
    <div className={fluid ? 'w-full' : 'w-36 shrink-0 sm:w-40 lg:w-44'} aria-hidden="true">
      <div className="skeleton aspect-2/3 rounded-xl" />
      <div className="skeleton mt-2 h-4 w-4/5 rounded" />
      <div className="skeleton mt-1.5 h-3 w-1/2 rounded" />
    </div>
  )
}
