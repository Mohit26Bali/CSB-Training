import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { ApiError, errorMessage } from '../../api/client'
import { bookApi, librarianApi } from '../../api/endpoints'
import type { Book, BookInput } from '../../api/types'
import { BookCover } from '../../components/BookCover'
import { useToast } from '../../components/Toast'
import { Button, EmptyState, ErrorState, Field, LoadingState, Modal, PageHeader, TextAreaField } from '../../components/ui'
import { useDebounced } from '../../hooks'

export function BooksPage() {
  const [search, setSearch] = useState('')
  const [editing, setEditing] = useState<Book | 'new' | null>(null)
  const debouncedSearch = useDebounced(search)
  const books = useQuery({ queryKey: ['books', debouncedSearch, '', false], queryFn: () => bookApi.search(debouncedSearch || undefined), placeholderData: (previous) => previous })

  return (
    <>
      <PageHeader title="Books" subtitle="The catalogue. Add titles, fix details and change the number of copies." actions={<Button onClick={() => setEditing('new')}>Add a book</Button>} />
      <div className="mb-4">
        <label htmlFor="search" className="sr-only">
          Search books
        </label>
        <input
          id="search"
          type="search"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Title, author or ISBN"
          className="w-full max-w-md rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
        />
      </div>
      {books.isPending ? (
        <LoadingState />
      ) : books.isError ? (
        <ErrorState message={errorMessage(books.error)} onRetry={() => books.refetch()} />
      ) : books.data.length === 0 ? (
        <EmptyState title="No books found" action={<Button onClick={() => setEditing('new')}>Add a book</Button>} />
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2">
          {books.data.map((b) => (
            <li key={b.id} className="flex overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
              <BookCover title={b.title} author={b.author} category={b.category} className="w-20 shrink-0" />
              <div className="flex min-w-0 flex-1 flex-col justify-between p-3">
                <div>
                  <h2 className="font-semibold leading-snug text-slate-900">{b.title}</h2>
                  <p className="text-sm text-slate-600">{b.author}</p>
                  <p className="mt-1 font-mono text-xs text-slate-500">
                    {b.isbn} · {b.category}
                  </p>
                </div>
                <div className="mt-2 flex items-center justify-between">
                  <span className="text-xs text-slate-600">
                    {b.availableCopies}/{b.totalCopies} in
                  </span>
                  <Button variant="secondary" small onClick={() => setEditing(b)}>
                    Edit
                  </Button>
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}
      {editing && <BookFormModal book={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
    </>
  )
}

function BookFormModal({ book, onClose }: { book: Book | null; onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const categories = useQuery({ queryKey: ['categories'], queryFn: bookApi.categories })
  const [form, setForm] = useState<BookInput>({
    title: book?.title ?? '',
    author: book?.author ?? '',
    isbn: book?.isbn ?? '',
    category: book?.category ?? '',
    description: book?.description ?? '',
    publishedYear: book?.publishedYear ?? '',
    totalCopies: book?.totalCopies ?? 1,
  })
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)

  const save = useMutation({
    mutationFn: () => (book ? librarianApi.updateBook(book.id, form) : librarianApi.createBook(form)),
    onSuccess: (saved) => {
      toast.success(book ? `Saved “${saved.title}”` : `Added “${saved.title}”`)
      queryClient.invalidateQueries()
      onClose()
    },
    onError: (err) => {
      if (err instanceof ApiError && Object.keys(err.fieldErrors).length > 0) {
        setFieldErrors(err.fieldErrors)
        setFormError(null)
      } else {
        setFieldErrors({})
        setFormError(errorMessage(err))
      }
    },
  })
  const set = (key: keyof BookInput) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [key]: e.target.value }))

  function submit(e: FormEvent) {
    e.preventDefault()
    setFieldErrors({})
    setFormError(null)
    save.mutate()
  }

  return (
    <Modal title={book ? 'Edit book' : 'Add a book'} onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-4">
        {formError && (
          <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {formError}
          </p>
        )}
        <Field label="Title" maxLength={150} value={form.title} onChange={set('title')} error={fieldErrors.title} />
        <div className="grid grid-cols-2 gap-4">
          <Field label="Author" maxLength={100} value={form.author} onChange={set('author')} error={fieldErrors.author} />
          <Field label="ISBN" placeholder="978-0-13-468599-1" maxLength={20} value={form.isbn} onChange={set('isbn')} error={fieldErrors.isbn} />
        </div>
        <div className="grid grid-cols-3 gap-4">
          <Field label="Category" list="categories" maxLength={40} value={form.category} onChange={set('category')} error={fieldErrors.category} />
          <Field label="Year (optional)" type="number" value={form.publishedYear} onChange={set('publishedYear')} error={fieldErrors.publishedYear} />
          <Field label="Copies" type="number" min={1} max={100} value={form.totalCopies} onChange={set('totalCopies')} error={fieldErrors.totalCopies} />
        </div>
        <datalist id="categories">
          {(categories.data ?? []).map((c) => (
            <option key={c} value={c} />
          ))}
        </datalist>
        <TextAreaField label="Description (optional)" rows={3} maxLength={1000} value={form.description} onChange={set('description')} error={fieldErrors.description} />
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose} disabled={save.isPending}>
            Cancel
          </Button>
          <Button type="submit" loading={save.isPending}>
            {book ? 'Save changes' : 'Add book'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
