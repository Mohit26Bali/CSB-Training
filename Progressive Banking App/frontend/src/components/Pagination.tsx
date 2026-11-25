import { Button } from './ui'

/**
 * Previous / next controls under a paged list (the backend's PageResponse; page is zero-based).
 * Used with api/queries.ts#useHistory on the transaction pages.
 */
export function Pagination({
  page,
  totalPages,
  totalElements,
  onPage,
}: {
  page: number
  totalPages: number
  totalElements: number
  onPage: (page: number) => void
}) {
  if (totalPages <= 1) return <p className="pt-4 text-xs text-faint">{totalElements} transaction{totalElements === 1 ? '' : 's'}</p>
  return (
    <div className="flex items-center justify-between gap-3 pt-4">
      <p className="text-xs text-muted">
        Page {page + 1} of {totalPages} · {totalElements} transactions
      </p>
      <div className="flex gap-2">
        <Button variant="secondary" small disabled={page === 0} onClick={() => onPage(page - 1)}>
          Previous
        </Button>
        <Button variant="secondary" small disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>
          Next
        </Button>
      </div>
    </div>
  )
}
