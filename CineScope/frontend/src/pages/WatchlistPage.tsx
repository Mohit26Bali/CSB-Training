import { Link, useSearchParams } from 'react-router-dom'
import { errorMessage } from '../api/client'
import { useRemoveWatchlistEntry, useWatchlist, useWatchlistSummary } from '../api/queries'
import type { WatchStatus, WatchlistEntry, WatchlistSort } from '../api/types'
import { Icon, Star } from '../components/Icon'
import { Button, EmptyState, ErrorState, Skeleton } from '../components/ui'
import { useDocumentTitle } from '../hooks'
import { posterUrl } from '../lib/images'
import { STATUS_LABEL } from '../lib/watchlist'

/**
 * The signed-in user's list (route /watchlist, guarded by auth/RequireAuth). Tabs for All / Want / Watching / Watched
 * show their counts from GET /api/watchlist/summary; the list itself is GET /api/watchlist with the chosen status,
 * sort and page. The tab, sort and page live in the URL (?status=WATCHED&sort=rating&page=2), so a reload or a shared
 * link shows the same view. Removing an entry is one DELETE; api/queries.ts then refreshes the list and the counts.
 */
const SORTS: { value: WatchlistSort; label: string }[] = [
  { value: 'added', label: 'Recently added' },
  { value: 'updated', label: 'Recently updated' },
  { value: 'title', label: 'Title A–Z' },
  { value: 'rating', label: 'My rating' },
]
const STATUSES: WatchStatus[] = ['WANT', 'WATCHING', 'WATCHED']

function parseStatus(value: string | null): WatchStatus | undefined {
  return STATUSES.find((s) => s === value)
}

export function WatchlistPage() {
  useDocumentTitle('My watchlist')
  const [params, setParams] = useSearchParams()
  const status = parseStatus(params.get('status'))
  const sort = SORTS.find((s) => s.value === params.get('sort'))?.value ?? 'added'
  const page = Math.max(1, Number(params.get('page')) || 1)

  const summary = useWatchlistSummary()
  const list = useWatchlist({ status, sort, page })

  function update(changes: Record<string, string | null>) {
    const next = new URLSearchParams(params)
    for (const [key, value] of Object.entries(changes)) {
      if (value) next.set(key, value)
      else next.delete(key)
    }
    setParams(next, { replace: true })
  }

  const counts: Record<string, number | undefined> = {
    ALL: summary.data?.total,
    WANT: summary.data?.want,
    WATCHING: summary.data?.watching,
    WATCHED: summary.data?.watched,
  }
  const tabs: { key: WatchStatus | undefined; label: string }[] = [
    { key: undefined, label: 'All' },
    ...STATUSES.map((s) => ({ key: s, label: STATUS_LABEL[s] })),
  ]

  return (
    <div className="mx-auto max-w-7xl px-4 pb-10 pt-28 sm:px-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <h1 className="text-3xl font-extrabold tracking-tight text-ink sm:text-4xl">My watchlist</h1>
        <label className="flex items-center gap-2 text-sm text-muted">
          Sort by
          <select
            value={sort}
            onChange={(e) => update({ sort: e.target.value === 'added' ? null : e.target.value, page: null })}
            className="h-10 rounded-xl border border-line bg-surface px-3 text-sm text-ink focus:border-ember-400 focus:outline-none"
          >
            {SORTS.map((s) => (
              <option key={s.value} value={s.value}>
                {s.label}
              </option>
            ))}
          </select>
        </label>
      </div>

      <div role="tablist" aria-label="Filter by status" className="no-scrollbar -mx-4 mt-6 flex gap-2 overflow-x-auto px-4 sm:mx-0 sm:px-0">
        {tabs.map((tab) => {
          const active = tab.key === status
          const count = counts[tab.key ?? 'ALL']
          return (
            <button
              key={tab.label}
              role="tab"
              type="button"
              aria-selected={active}
              onClick={() => update({ status: tab.key ?? null, page: null })}
              className={`shrink-0 rounded-full border px-4 py-2 text-sm font-semibold transition-colors ${
                active ? 'border-ember-500 bg-ember-500 text-white' : 'border-line text-muted hover:border-ember-400 hover:text-ink'
              }`}
            >
              {tab.label}
              {count !== undefined && <span className="ml-2 opacity-80">{count}</span>}
            </button>
          )
        })}
      </div>

      <div className="mt-8">
        {list.isLoading ? (
          <div aria-busy="true" className="space-y-3">
            {Array.from({ length: 4 }, (_, i) => (
              <Skeleton key={i} className="h-28 w-full" />
            ))}
          </div>
        ) : list.isError ? (
          <ErrorState message={errorMessage(list.error)} onRetry={() => list.refetch()} />
        ) : list.data!.items.length === 0 ? (
          <Empty status={status} />
        ) : (
          <>
            <ul className="space-y-3">
              {list.data!.items.map((entry) => (
                <Row key={`${entry.type}-${entry.tmdbId}`} entry={entry} />
              ))}
            </ul>
            {list.data!.totalPages > 1 && (
              <nav aria-label="Pages" className="mt-8 flex items-center justify-center gap-4">
                <Button variant="secondary" disabled={page <= 1} onClick={() => update({ page: page > 2 ? String(page - 1) : null })}>
                  Previous
                </Button>
                <span className="text-sm text-muted">
                  Page {list.data!.page} of {list.data!.totalPages}
                </span>
                <Button variant="secondary" disabled={page >= list.data!.totalPages} onClick={() => update({ page: String(page + 1) })}>
                  Next
                </Button>
              </nav>
            )}
          </>
        )}
      </div>
    </div>
  )
}

function Empty({ status }: { status: WatchStatus | undefined }) {
  return (
    <div className="text-center">
      <EmptyState
        icon="film"
        title={status ? `Nothing in “${STATUS_LABEL[status]}” yet` : 'Your watchlist is empty'}
        hint="Open any movie or show and press “Add to watchlist”."
      />
      <Link to="/browse?type=movie" className="mt-5 inline-block text-sm font-semibold text-ember-400 hover:underline">
        Find something to watch
      </Link>
    </div>
  )
}

function Row({ entry }: { entry: WatchlistEntry }) {
  const remove = useRemoveWatchlistEntry()
  const poster = posterUrl(entry.posterPath)
  const name = entry.title ?? 'Untitled'
  return (
    <li className="flex gap-4 rounded-2xl border border-line bg-surface p-3 sm:p-4">
      <Link to={`/title/${entry.type}/${entry.tmdbId}`} aria-label={name} className="w-16 shrink-0 sm:w-20">
        <div className="aspect-2/3 overflow-hidden rounded-lg bg-surface-2 ring-1 ring-line">
          {poster ? <img src={poster} alt="" loading="lazy" className="size-full object-cover" /> : <div className="grid size-full place-items-center p-1 text-center text-xs text-muted">{name}</div>}
        </div>
      </Link>
      <div className="min-w-0 flex-1">
        <Link to={`/title/${entry.type}/${entry.tmdbId}`} className="clamp-2 text-base font-bold text-ink hover:text-ember-400 sm:text-lg">
          {name}
        </Link>
        <p className="text-xs text-faint">
          {entry.year ?? '—'} · {entry.type === 'tv' ? 'TV' : 'Movie'}
        </p>
        <div className="mt-2 flex flex-wrap items-center gap-2">
          <span className="rounded-full border border-line px-3 py-0.5 text-xs font-semibold text-muted">{STATUS_LABEL[entry.status]}</span>
          {entry.rating && (
            <span className="inline-flex items-center gap-1 text-sm font-bold text-ink">
              <Star className="size-4 text-gold" /> {entry.rating}/10
            </span>
          )}
        </div>
        {entry.note && <p className="clamp-2 mt-2 text-sm text-muted">{entry.note}</p>}
      </div>
      <Button
        variant="ghost"
        aria-label={`Remove ${name}`}
        loading={remove.isPending}
        onClick={() => remove.mutate({ type: entry.type, id: entry.tmdbId })}
        className="h-fit shrink-0 px-3"
      >
        {!remove.isPending && <Icon name="trash" className="size-4" />}
      </Button>
    </li>
  )
}
