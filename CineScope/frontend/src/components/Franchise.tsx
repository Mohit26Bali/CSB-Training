import { Link } from 'react-router-dom'
import { errorMessage } from '../api/client'
import { useAddFranchise, useFranchise } from '../api/queries'
import type { FranchisePart } from '../api/types'
import { Button } from './ui'
import { posterUrl } from '../lib/images'

/**
 * "The series" section of a film's page (components on pages/TitlePage): the other films of the same TMDB collection (for example
 * the Avengers or Harry Potter films), oldest first, how many the person has watched, and a button that puts the rest on their
 * watchlist as want-to-watch. Shown only for a film that belongs to a series (backend service/FranchiseService, GET
 * /api/titles/movie/{id}/franchise). Adding changes only titles that are not on the list yet and never adds a film that is not out,
 * so the button is hidden when nothing would change. Opening a part goes to its own page.
 */
const STATUS_LABEL: Record<NonNullable<FranchisePart['status']>, string> = { WANT: 'On my list', WATCHING: 'Watching', WATCHED: 'Watched' }

export function Franchise({ filmId }: { filmId: number }) {
  const franchise = useFranchise(filmId, true)
  const add = useAddFranchise()
  const data = franchise.data
  if (!data || data.total < 2) return null // not part of a series, or a series of one

  const percent = data.released > 0 ? Math.round((data.watched / data.released) * 100) : 0
  return (
    <section aria-label="The series" data-testid="franchise" className="mt-10">
      <h2 className="text-xl font-bold text-ink">{data.name}</h2>
      <p className="mt-1 text-sm text-muted">
        You have watched <strong data-testid="franchise-progress" className="text-ink">{data.watched} of {data.released}</strong> films that are out
        {data.total > data.released ? ` (${data.total - data.released} more to come)` : ''}.
      </p>
      <div role="progressbar" aria-label="Series progress" aria-valuemin={0} aria-valuemax={data.released} aria-valuenow={data.watched} className="mt-2 h-1.5 max-w-md overflow-hidden rounded-full bg-line">
        <div className="h-full bg-ember-500 transition-all" style={{ width: `${percent}%` }} />
      </div>

      <ol className="mt-4 flex gap-4 overflow-x-auto pb-2">
        {data.parts.map((part) => {
          const poster = posterUrl(part.posterPath, 'w185')
          const current = part.id === filmId
          return (
            <li key={part.id} data-part={part.id} data-status={part.status ?? 'NONE'} className={`w-28 shrink-0 ${part.released ? '' : 'opacity-60'}`}>
              <Link to={`/title/movie/${part.id}`} aria-current={current ? 'page' : undefined} className={`block rounded-lg ${current ? 'ring-2 ring-ember-500' : ''}`}>
                {poster ? <img src={poster} alt="" loading="lazy" className="aspect-2/3 w-full rounded-lg object-cover" /> : <div className="grid aspect-2/3 w-full place-items-center rounded-lg bg-surface-2 text-xs text-faint">No poster</div>}
                <span className="mt-2 block text-sm font-semibold leading-tight text-ink">{part.title}</span>
                <span className="block text-xs text-muted">{part.year ?? 'Date to be announced'}</span>
              </Link>
              <span data-testid={`franchise-badge-${part.id}`} className={`mt-1 inline-block rounded-full border px-2 py-0.5 text-xs font-semibold ${part.status === 'WATCHED' ? 'border-emerald-500/60 text-emerald-500' : 'border-line text-muted'}`}>
                {part.status ? STATUS_LABEL[part.status] : part.released ? 'Not on my list' : 'Not out yet'}
              </span>
            </li>
          )
        })}
      </ol>

      <div className="mt-3 flex flex-wrap items-center gap-3">
        {data.missing > 0 ? (
          <Button variant="secondary" loading={add.isPending} onClick={() => add.mutate(data.id)} data-testid="franchise-add">
            Add the other {data.missing} to my watchlist
          </Button>
        ) : (
          <span className="text-sm text-muted">Every film that is out is on your list.</span>
        )}
        {add.isSuccess && (
          <span role="status" data-testid="franchise-result" className="text-sm font-semibold text-emerald-500">
            Added {add.data.added} {add.data.added === 1 ? 'film' : 'films'} to your watchlist.
          </span>
        )}
        {add.isError && (
          <span role="alert" className="text-sm text-ember-400">
            {errorMessage(add.error)}
          </span>
        )}
      </div>
    </section>
  )
}
