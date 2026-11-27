import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { errorMessage } from '../../api/client'
import { bookApi, loanApi } from '../../api/endpoints'
import type { Book } from '../../api/types'
import { Banner, BookCover } from '../../components/BookCover'
import { useToast } from '../../components/Toast'
import { Button, EmptyState, ErrorState, LoadingState, StatusBadge } from '../../components/ui'
import { useDebounced } from '../../hooks'

export function CataloguePage() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [search, setSearch] = useState('')
  const [category, setCategory] = useState('')
  const [availableOnly, setAvailableOnly] = useState(false)
  const debouncedSearch = useDebounced(search)

  const books = useQuery({
    queryKey: ['books', debouncedSearch, category, availableOnly],
    queryFn: () => bookApi.search(debouncedSearch || undefined, category || undefined, availableOnly),
    placeholderData: (previous) => previous,
  })
  const categories = useQuery({ queryKey: ['categories'], queryFn: bookApi.categories })

  const reserve = useMutation({
    mutationFn: (book: Book) => loanApi.reserve(book.id),
    onSuccess: (l) => toast.success(`“${l.bookTitle}” is reserved. Collect it from the desk`),
    onError: (err) => toast.error(errorMessage(err)),
    onSettled: () => queryClient.invalidateQueries(), // copies may have changed under us
  })
  const busyId = reserve.isPending ? reserve.variables?.id : undefined
  const filtered = search !== '' || category !== '' || availableOnly

  return (
    <>
      <Banner src="/banner/shelves.jpg" title="Catalogue" text="Search the shelves, then reserve a copy. Collect it from the desk." />

      <div className="mb-4 flex flex-wrap gap-3">
        <div className="min-w-56 flex-1">
          <label htmlFor="search" className="sr-only">
            Search books
          </label>
          <input
            id="search"
            type="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Title, author or ISBN"
            className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
          />
        </div>
        <label className="flex items-center gap-2 text-sm text-slate-700">
          <input type="checkbox" checked={availableOnly} onChange={(e) => setAvailableOnly(e.target.checked)} />
          Only books I can reserve now
        </label>
      </div>
      <div role="tablist" aria-label="Filter by category" className="mb-6 flex gap-1 overflow-x-auto">
        {['', ...(categories.data ?? [])].map((c) => (
          <button
            key={c || 'all'}
            role="tab"
            aria-selected={category === c}
            onClick={() => setCategory(c)}
            className={`whitespace-nowrap rounded-full px-3 py-1.5 text-sm font-medium ${category === c ? 'bg-brand-700 text-white' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'}`}
          >
            {c === '' ? 'All categories' : c}
          </button>
        ))}
      </div>

      {books.isPending ? (
        <LoadingState />
      ) : books.isError ? (
        <ErrorState message={errorMessage(books.error)} onRetry={() => books.refetch()} />
      ) : books.data.length === 0 ? (
        <EmptyState title={filtered ? 'No books match your search' : 'The catalogue is empty'} hint={filtered ? 'Try fewer words, or another category.' : undefined} />
      ) : (
        <ul className="grid gap-5 sm:grid-cols-2 xl:grid-cols-3">
          {books.data.map((b) => (
            <li key={b.id} className="flex overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
              <BookCover title={b.title} author={b.author} category={b.category} className="w-28 shrink-0" />
              <div className="flex min-w-0 flex-1 flex-col p-4">
                <p className="text-xs font-medium uppercase tracking-wide text-slate-500">
                  {b.category}
                  {b.publishedYear && ` · ${b.publishedYear}`}
                </p>
                <h2 className="mt-0.5 font-semibold leading-snug text-slate-900">{b.title}</h2>
                <p className="text-sm text-slate-600">{b.author}</p>
                {b.description && <p className="mt-2 line-clamp-2 text-xs text-slate-500">{b.description}</p>}
                <div className="mt-auto flex items-center justify-between gap-2 pt-3">
                  {b.myLoanStatus ? (
                    <StatusBadge status={b.myLoanStatus} />
                  ) : (
                    <span className={`text-xs font-medium ${b.availableCopies > 0 ? 'text-emerald-700' : 'text-rose-700'}`}>
                      {b.availableCopies > 0 ? `${b.availableCopies} of ${b.totalCopies} available` : 'All copies out'}
                    </span>
                  )}
                  {!b.myLoanStatus && (
                    <Button small disabled={b.availableCopies === 0 || reserve.isPending} loading={busyId === b.id} onClick={() => reserve.mutate(b)}>
                      Reserve
                    </Button>
                  )}
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
