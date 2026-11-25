import { CardSkeleton, TitleCard, type CardTitle } from './TitleCard'
import { Button } from './ui'

/**
 * A responsive grid of {@link TitleCard}s with a skeleton while loading and a "Load more" button for the next TMDB page.
 * Shared by pages/BrowsePage, ListPage and SearchPage, which supply the items from an infinite query
 * (api/queries.ts). {@code total} is the number TMDB reports, shown under the grid.
 */
export function TitleGrid({
  items,
  loading,
  hasMore,
  loadingMore,
  onMore,
  total,
}: {
  items: CardTitle[]
  loading: boolean
  hasMore: boolean
  loadingMore: boolean
  onMore: () => void
  total?: number
}) {
  return (
    <>
      <div className="grid grid-cols-2 gap-x-4 gap-y-7 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6">
        {loading
          ? Array.from({ length: 12 }, (_, i) => <CardSkeleton key={i} fluid />)
          : items.map((item) => <TitleCard key={`${item.type}-${item.id}`} title={item} fluid />)}
      </div>
      {!loading && (
        <div className="mt-10 flex flex-col items-center gap-3">
          {hasMore && (
            <Button variant="secondary" loading={loadingMore} onClick={onMore}>
              Load more
            </Button>
          )}
          {total !== undefined && total > 0 && (
            <p className="text-xs text-faint">
              Showing {items.length.toLocaleString()} of {total.toLocaleString()}
            </p>
          )}
        </div>
      )}
    </>
  )
}
