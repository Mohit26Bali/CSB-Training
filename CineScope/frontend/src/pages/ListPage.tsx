import { Link, useParams } from 'react-router-dom'
import { errorMessage } from '../api/client'
import { useList } from '../api/queries'
import { Icon } from '../components/Icon'
import { TitleGrid } from '../components/TitleGrid'
import { EmptyState, ErrorState } from '../components/ui'
import { useDocumentTitle } from '../hooks'

/**
 * One complete TMDB list (route /list/:category/:type), opened from a home-page row's "See all". Categories are
 * trending, trending_today, popular, top_rated, now_playing, upcoming and on_the_air; the backend validates the combination and answers
 * 400 for an impossible one (for example now_playing for TV), which is shown as the error below.
 */
const CATEGORY_TITLES: Record<string, string> = {
  trending_today: 'Trending today',
  trending: 'Trending this week',
  popular: 'Popular',
  top_rated: 'Top rated',
  now_playing: 'In cinemas now',
  upcoming: 'Coming soon',
  on_the_air: 'Currently airing',
}

export function ListPage() {
  const { category = 'popular', type = 'movie' } = useParams()
  const list = useList(category, type)
  const items = list.data?.pages.flatMap((page) => page.items) ?? []
  const kind = type === 'tv' ? 'TV shows' : type === 'all' ? 'movies & shows' : 'movies'
  const heading = `${CATEGORY_TITLES[category] ?? category} ${kind}`
  useDocumentTitle(heading)

  return (
    <div className="mx-auto max-w-7xl px-4 pb-8 pt-10 sm:px-8">
      <Link to="/" className="inline-flex items-center gap-1 text-sm font-medium text-muted hover:text-ink">
        <Icon name="chevronLeft" className="size-4" /> Home
      </Link>
      <h1 className="mt-3 text-3xl font-extrabold tracking-tight text-ink sm:text-4xl">{heading}</h1>

      <div className="mt-8">
        {list.isError ? (
          <ErrorState message={errorMessage(list.error)} onRetry={() => list.refetch()} />
        ) : !list.isLoading && items.length === 0 ? (
          <EmptyState icon="film" title="Nothing to show here" />
        ) : (
          <TitleGrid
            items={items}
            loading={list.isLoading}
            hasMore={!!list.hasNextPage}
            loadingMore={list.isFetchingNextPage}
            onMore={() => list.fetchNextPage()}
            total={list.data?.pages[0]?.totalResults}
          />
        )}
      </div>
    </div>
  )
}
