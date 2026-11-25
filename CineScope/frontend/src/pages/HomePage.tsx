import { errorMessage } from '../api/client'
import { useHome } from '../api/queries'
import { Hero } from '../components/Hero'
import { Row } from '../components/Row'
import { CardSkeleton, TitleCard } from '../components/TitleCard'
import { ErrorState, Skeleton } from '../components/ui'

/**
 * The home page (route /): the rotating hero banner (components/Hero) and the horizontal rows (trending today, trending this week, popular, in cinemas in India, top rated,
 * coming soon, TV). One request to GET /api/home returns everything; the backend fetches the rows from TMDB in
 * parallel and caches them, so this is fast after the first visit. Each row's "See all" opens pages/ListPage.
 */
export function HomePage() {
  const home = useHome()

  if (home.isError) {
    return (
      <div className="px-4 pb-10 pt-24">
        <ErrorState message={errorMessage(home.error)} onRetry={() => home.refetch()} />
      </div>
    )
  }

  if (home.isLoading || !home.data) {
    return (
      <div aria-busy="true">
        <Skeleton className="-mt-16 min-h-[70vh] rounded-none" />
        <div className="mx-auto max-w-7xl space-y-10 px-4 pt-10 sm:px-8">
          {[0, 1].map((row) => (
            <div key={row}>
              <Skeleton className="mb-4 h-7 w-56" />
              <div className="flex gap-4 overflow-hidden">
                {Array.from({ length: 8 }, (_, i) => (
                  <CardSkeleton key={i} />
                ))}
              </div>
            </div>
          ))}
        </div>
      </div>
    )
  }

  const { heroes, rows } = home.data
  return (
    <div>
      {heroes.length > 0 ? <Hero titles={heroes} /> : <div className="h-20" />}
      <div className="relative z-10 mx-auto max-w-7xl space-y-12 px-4 pt-8 sm:px-8">
        {rows.map((row, index) => (
          <div key={row.key} className="fade-up" style={{ animationDelay: `${Math.min(index, 4) * 0.06}s` }}>
            <Row title={row.title} seeAllTo={`/list/${row.category}/${row.mediaType}`}>
              {row.items.map((item) => (
                <TitleCard key={`${item.type}-${item.id}`} title={item} />
              ))}
            </Row>
          </div>
        ))}
      </div>
    </div>
  )
}
