import { useSearchParams } from 'react-router-dom'
import { errorMessage } from '../api/client'
import { useDiscover, useGenres } from '../api/queries'
import type { MediaType, SortOption } from '../api/types'
import { Icon } from '../components/Icon'
import { TitleGrid } from '../components/TitleGrid'
import { EmptyState, ErrorState } from '../components/ui'
import { useDocumentTitle } from '../hooks'

/**
 * Browse with filters (route /browse). Movies or TV, then optional genre, release year, minimum rating and sort order.
 * Every choice lives in the URL (for example /browse?type=movie&genre=28&sort=rating), so a filtered view can be
 * bookmarked or shared and the back button works. Results come from GET /api/discover and load page by page.
 */
const SORTS: { value: SortOption; label: string }[] = [
  { value: 'popularity', label: 'Most popular' },
  { value: 'rating', label: 'Top rated' },
  { value: 'newest', label: 'Newest first' },
]
const RATINGS = [5, 6, 7, 8]
// Years offered in the filter: next year back to 1950. Computed once, not on every render.
const THIS_YEAR = new Date().getFullYear()
const YEARS = Array.from({ length: THIS_YEAR + 1 - 1950 }, (_, i) => THIS_YEAR + 1 - i)
const field =
  'h-11 w-full appearance-none rounded-xl border border-line bg-surface px-3.5 pr-9 text-sm text-ink focus:border-ember-400 focus:outline-none'

function intParam(value: string | null): number | undefined {
  const n = value ? Number(value) : NaN
  return Number.isInteger(n) && n > 0 ? n : undefined
}

export function BrowsePage() {
  const [params, setParams] = useSearchParams()
  const type: MediaType = params.get('type') === 'tv' ? 'tv' : 'movie'
  const genre = intParam(params.get('genre'))
  const year = intParam(params.get('year'))
  const minRating = intParam(params.get('minRating'))
  const sortParam = params.get('sort')
  const sort: SortOption = sortParam === 'rating' || sortParam === 'newest' ? sortParam : 'popularity'

  useDocumentTitle(type === 'tv' ? 'Browse TV shows' : 'Browse movies')

  const genres = useGenres(type)
  const results = useDiscover({ type, genre, year, minRating, sort })
  const items = results.data?.pages.flatMap((page) => page.items) ?? []
  const total = results.data?.pages[0]?.totalResults

  function update(changes: Record<string, string | null>) {
    const next = new URLSearchParams(params)
    for (const [key, value] of Object.entries(changes)) {
      if (value === null || value === '') next.delete(key)
      else next.set(key, value)
    }
    setParams(next, { replace: true })
  }

  const filtersOn = genre !== undefined || year !== undefined || minRating !== undefined

  return (
    <div className="mx-auto max-w-7xl px-4 pb-8 pt-10 sm:px-8">
      <h1 className="text-3xl font-extrabold tracking-tight text-ink sm:text-4xl">{type === 'tv' ? 'TV shows' : 'Movies'}</h1>
      <p className="mt-1 text-muted">Filter by genre, year and rating to find what to watch next.</p>

      <div className="mt-6 flex flex-wrap items-center gap-3">
        <div role="radiogroup" aria-label="Media type" className="inline-flex rounded-xl border border-line bg-surface p-1">
          {(['movie', 'tv'] as const).map((option) => (
            <button
              key={option}
              type="button"
              role="radio"
              aria-checked={type === option}
              onClick={() => update({ type: option, genre: null })}
              className={`inline-flex items-center gap-2 rounded-lg px-4 py-2 text-sm font-semibold transition-colors ${type === option ? 'bg-ember-500 text-white' : 'text-muted hover:text-ink'}`}
            >
              <Icon name={option === 'tv' ? 'tv' : 'film'} className="size-4" /> {option === 'tv' ? 'TV shows' : 'Movies'}
            </button>
          ))}
        </div>
      </div>

      <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
        <label className="block text-xs font-semibold uppercase tracking-wide text-faint">
          Genre
          <div className="relative mt-1.5 normal-case tracking-normal">
            <select value={genre ?? ''} onChange={(e) => update({ genre: e.target.value })} className={field}>
              <option value="">All genres</option>
              {genres.data?.map((g) => (
                <option key={g.id} value={g.id}>
                  {g.name}
                </option>
              ))}
            </select>
            <Icon name="chevronDown" className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 text-faint" />
          </div>
        </label>
        <label className="block text-xs font-semibold uppercase tracking-wide text-faint">
          Year
          <div className="relative mt-1.5 normal-case tracking-normal">
            <select value={year ?? ''} onChange={(e) => update({ year: e.target.value })} className={field}>
              <option value="">Any year</option>
              {YEARS.map((y) => (
                <option key={y} value={y}>
                  {y}
                </option>
              ))}
            </select>
            <Icon name="chevronDown" className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 text-faint" />
          </div>
        </label>
        <label className="block text-xs font-semibold uppercase tracking-wide text-faint">
          Minimum rating
          <div className="relative mt-1.5 normal-case tracking-normal">
            <select value={minRating ?? ''} onChange={(e) => update({ minRating: e.target.value })} className={field}>
              <option value="">Any rating</option>
              {RATINGS.map((r) => (
                <option key={r} value={r}>
                  {r}+ ★
                </option>
              ))}
            </select>
            <Icon name="chevronDown" className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 text-faint" />
          </div>
        </label>
        <label className="block text-xs font-semibold uppercase tracking-wide text-faint">
          Sort by
          <div className="relative mt-1.5 normal-case tracking-normal">
            <select value={sort} onChange={(e) => update({ sort: e.target.value === 'popularity' ? null : e.target.value })} className={field}>
              {SORTS.map((s) => (
                <option key={s.value} value={s.value}>
                  {s.label}
                </option>
              ))}
            </select>
            <Icon name="chevronDown" className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 text-faint" />
          </div>
        </label>
        <div className="flex items-end">
          <button
            type="button"
            disabled={!filtersOn}
            onClick={() => update({ genre: null, year: null, minRating: null })}
            className="h-11 w-full rounded-xl border border-line px-4 text-sm font-semibold text-muted transition-colors hover:border-ember-400 hover:text-ember-400 disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:border-line disabled:hover:text-muted"
          >
            Clear filters
          </button>
        </div>
      </div>

      <div className="mt-8">
        {results.isError ? (
          <ErrorState message={errorMessage(results.error)} onRetry={() => results.refetch()} />
        ) : !results.isLoading && items.length === 0 ? (
          <EmptyState title="Nothing matches these filters" hint="Try a different year or a lower minimum rating." />
        ) : (
          <TitleGrid
            items={items}
            loading={results.isLoading}
            hasMore={!!results.hasNextPage}
            loadingMore={results.isFetchingNextPage}
            onMore={() => results.fetchNextPage()}
            total={total}
          />
        )}
      </div>
    </div>
  )
}
