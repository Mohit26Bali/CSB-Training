import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { errorMessage } from '../api/client'
import { useSearch } from '../api/queries'
import type { SearchItem } from '../api/types'
import { Icon } from '../components/Icon'
import { TitleGrid } from '../components/TitleGrid'
import type { CardTitle } from '../components/TitleCard'
import { EmptyState, ErrorState } from '../components/ui'
import { useDebounced, useDocumentTitle } from '../hooks'
import { initials } from '../lib/format'
import { profileUrl } from '../lib/images'

/**
 * Search (route /search?q=...). One box searches movies, TV shows and people together (GET /api/search). The text is
 * kept in the URL (so results can be shared) and debounced before it is sent, and the results are split
 * into All / Movies / TV shows / People tabs client-side from what has been loaded. People are shown first when they
 * are the best hit, and each links to pages/PersonPage.
 */
type Tab = 'all' | 'movie' | 'tv' | 'person'
const TABS: { key: Tab; label: string }[] = [
  { key: 'all', label: 'All' },
  { key: 'movie', label: 'Movies' },
  { key: 'tv', label: 'TV shows' },
  { key: 'person', label: 'People' },
]

export function SearchPage() {
  const [params, setParams] = useSearchParams()
  const [tab, setTab] = useState<Tab>('all')

  // The URL holds the text (so results can be shared and the header search box can set it); the request uses a debounced
  // copy so typing does not fire one request per key.
  const urlQuery = params.get('q') ?? ''
  const query = useDebounced(urlQuery.trim())
  const text = urlQuery
  const setText = (value: string) => setParams(value ? { q: value } : {}, { replace: true })

  useDocumentTitle(query ? `Search: ${query}` : 'Search')
  const search = useSearch(query)
  const all: SearchItem[] = search.data?.pages.flatMap((page) => page.items) ?? []
  const counts = { all: all.length, movie: all.filter((i) => i.type === 'movie').length, tv: all.filter((i) => i.type === 'tv').length, person: all.filter((i) => i.type === 'person').length }
  const shown = tab === 'all' ? all : all.filter((i) => i.type === tab)
  const titles: CardTitle[] = shown
    .filter((i): i is SearchItem & { type: 'movie' | 'tv' } => i.type !== 'person')
    .map((i) => ({ id: i.id, type: i.type, title: i.title, posterPath: i.imagePath, year: i.year, rating: i.rating }))
  const people = shown.filter((i) => i.type === 'person')
  // TMDB orders hits by relevance: when the best hit is a person ("nolan"), show people above the titles.
  const peopleFirst = shown[0]?.type === 'person'
  const compact = tab === 'all' // on the All tab only the top people are shown, so the titles below stay in reach
  const visiblePeople = compact ? people.slice(0, 6) : people
  const peopleSection =
    people.length > 0 ? (
      <section aria-label="People" className={peopleFirst ? '' : titles.length > 0 ? 'mt-12' : ''}>
        <h2 className="mb-4 text-xl font-bold text-ink">People</h2>
        <ul className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {visiblePeople.map((person) => (
            <PersonCard key={person.id} person={person} />
          ))}
        </ul>
        {compact && (people.length > visiblePeople.length || search.hasNextPage) && (
          <div className="mt-6">
            <button
              type="button"
              onClick={() => setTab('person')}
              className="rounded-xl border border-line px-5 py-2.5 text-sm font-semibold text-ink hover:border-ember-400"
            >
              See all people
            </button>
          </div>
        )}
        {!compact && search.hasNextPage && (
          <div className="mt-8 text-center">
            <button
              type="button"
              onClick={() => search.fetchNextPage()}
              className="rounded-xl border border-line px-5 py-2.5 text-sm font-semibold text-ink hover:border-ember-400"
            >
              {search.isFetchingNextPage ? 'Loading…' : 'Load more'}
            </button>
          </div>
        )}
      </section>
    ) : null

  return (
    <div className="mx-auto max-w-7xl px-4 pb-8 pt-10 sm:px-8">
      <h1 className="text-3xl font-extrabold tracking-tight text-ink sm:text-4xl">Search</h1>
      <form role="search" onSubmit={(e) => e.preventDefault()} className="relative mt-5 max-w-2xl">
        <label htmlFor="search-page-input" className="sr-only">
          Search movies, TV shows and people
        </label>
        <Icon name="search" className="pointer-events-none absolute left-4 top-1/2 size-5 -translate-y-1/2 text-faint" />
        <input
          id="search-page-input"
          type="search"
          autoFocus
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="Search movies, TV shows and people"
          autoComplete="off"
          className="h-13 w-full rounded-2xl border border-line bg-surface pl-12 pr-4 text-base text-ink placeholder:text-faint focus:border-ember-400 focus:outline-none focus:ring-4 focus:ring-ember-500/15"
        />
      </form>

      {!query && <div className="mt-10"><EmptyState title="Find something to watch" hint="Type a title, an actor or a director." /></div>}

      {query && (
        <>
          <div role="tablist" aria-label="Result types" className="mt-6 flex flex-wrap gap-2">
            {TABS.map(({ key, label }) => (
              <button
                key={key}
                type="button"
                role="tab"
                aria-selected={tab === key}
                onClick={() => setTab(key)}
                className={`rounded-full border px-4 py-1.5 text-sm font-semibold transition-colors ${
                  tab === key ? 'border-ember-500 bg-ember-500 text-white' : 'border-line text-muted hover:border-ember-400 hover:text-ink'
                }`}
              >
                {label} <span className={tab === key ? 'text-white/80' : 'text-faint'}>{counts[key]}</span>
              </button>
            ))}
          </div>

          <div className="mt-8">
            {search.isError ? (
              <ErrorState message={errorMessage(search.error)} onRetry={() => search.refetch()} />
            ) : !search.isLoading && shown.length === 0 ? (
              <EmptyState title={`No ${tab === 'all' ? 'results' : TABS.find((t) => t.key === tab)!.label.toLowerCase()} for “${query}”`} hint="Check the spelling or try fewer words." />
            ) : (
              <>
                {peopleFirst && peopleSection}
                {(titles.length > 0 || search.isLoading) && (
                  <div className={peopleFirst && people.length > 0 ? 'mt-12' : ''}>
                    <TitleGrid
                      items={titles}
                      loading={search.isLoading}
                      hasMore={!!search.hasNextPage && tab !== 'person'}
                      loadingMore={search.isFetchingNextPage}
                      onMore={() => search.fetchNextPage()}
                      total={search.data?.pages[0]?.totalResults}
                    />
                  </div>
                )}
                {!peopleFirst && peopleSection}
              </>
            )}
          </div>
        </>
      )}
    </div>
  )
}

function PersonCard({ person }: { person: SearchItem }) {
  const photo = profileUrl(person.imagePath)
  return (
    <li>
    <Link to={`/person/${person.id}`} className="flex items-center gap-4 rounded-2xl border border-line bg-surface p-4 transition-colors hover:border-ember-400/60">
      {photo ? (
        <img src={photo} alt="" loading="lazy" className="size-16 shrink-0 rounded-full object-cover" />
      ) : (
        <div className="grid size-16 shrink-0 place-items-center rounded-full bg-linear-to-br from-slate-600 to-slate-800 text-lg font-bold text-white/80">{initials(person.title ?? '?')}</div>
      )}
      <div className="min-w-0">
        <p className="truncate font-semibold text-ink">{person.title}</p>
        {person.knownFor && <p className="clamp-2 text-sm text-muted">Known for {person.knownFor}</p>}
      </div>
    </Link>
    </li>
  )
}
