import { useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { errorMessage } from '../api/client'
import { useRemoveWatchlist, useSaveWatchlist, useWatchlistEntry } from '../api/queries'
import type { MediaType, WatchStatus, WatchlistEntry } from '../api/types'
import { useAuth } from '../auth/useAuth'
import { buttonClass } from '../lib/buttonStyles'
import { STATUS_LABEL } from '../lib/watchlist'
import { Icon } from './Icon'
import { Button } from './ui'

/**
 * The "my list" panel on a title's detail page (pages/TitlePage). Signed out it is a single "Add to watchlist" link to
 * the login page (returning here afterwards). Signed in it loads this title's entry (GET /api/watchlist/{type}/{id});
 * with no entry it offers "Add to watchlist"; with one it shows the status buttons (want / watching / watched), the
 * 1-10 rating (only once WATCHED, matching the backend rule), a note, and Remove. Every change is one PUT, so what is
 * on screen is always what the server saved (api/queries.ts updates the cache from the answer).
 */
const STATUSES: WatchStatus[] = ['WANT', 'WATCHING', 'WATCHED']

export function WatchlistControl({ type, id }: { type: MediaType; id: number }) {
  const { session } = useAuth()
  const location = useLocation()
  const entry = useWatchlistEntry(type, id, !!session)
  const save = useSaveWatchlist(type, id)

  if (!session) {
    return (
      <Link to={`/login?next=${encodeURIComponent(location.pathname)}`} className={buttonClass('secondary', 'px-5 py-3')}>
        <Icon name="bookmark" className="size-5" /> Add to watchlist
      </Link>
    )
  }
  if (entry.isLoading) return <span className="text-sm text-faint">Loading your list…</span>
  if (entry.isError) return <span role="alert" className="text-sm text-muted">Couldn’t load your list. {errorMessage(entry.error)}</span>

  if (!entry.data) {
    return (
      <div className="flex flex-col items-start gap-2">
        <Button variant="secondary" className="px-5 py-3" loading={save.isPending} onClick={() => save.mutate({ status: 'WANT' })}>
          {!save.isPending && <Icon name="bookmark" className="size-5" />} Add to watchlist
        </Button>
        {save.isError && <p role="alert" className="text-sm text-ember-400">{errorMessage(save.error)}</p>}
      </div>
    )
  }
  return <Editor key={entry.data.updatedAt} type={type} id={id} entry={entry.data} />
}

function Editor({ type, id, entry }: { type: MediaType; id: number; entry: WatchlistEntry }) {
  const save = useSaveWatchlist(type, id)
  const remove = useRemoveWatchlist(type, id)
  const [note, setNote] = useState(entry.note ?? '')
  const busy = save.isPending || remove.isPending
  const error = save.error ?? remove.error

  function setStatus(status: WatchStatus) {
    if (status === entry.status) return
    // Moving away from WATCHED drops the rating (the backend does the same), so only a still-WATCHED entry keeps it.
    save.mutate({ status, rating: status === 'WATCHED' ? entry.rating : null, note: entry.note })
  }

  return (
    <section aria-label="My watchlist entry" className="w-full max-w-xl rounded-2xl border border-line bg-surface/80 p-4 backdrop-blur">
      <p className="mb-3 flex items-center gap-2 text-sm font-semibold text-ink">
        <Icon name="check" className="size-4 text-ember-400" /> On your watchlist
      </p>

      <div role="group" aria-label="Status" className="flex flex-wrap gap-2">
        {STATUSES.map((status) => (
          <button
            key={status}
            type="button"
            disabled={busy}
            aria-pressed={entry.status === status}
            onClick={() => setStatus(status)}
            className={`rounded-full border px-4 py-1.5 text-sm font-semibold transition-colors disabled:opacity-60 ${
              entry.status === status ? 'border-ember-500 bg-ember-500 text-white' : 'border-line text-muted hover:border-ember-400 hover:text-ink'
            }`}
          >
            {STATUS_LABEL[status]}
          </button>
        ))}
      </div>

      {entry.status === 'WATCHED' && (
        <div className="mt-4">
          <p className="mb-2 text-sm font-semibold text-ink">Your rating{entry.rating ? `: ${entry.rating}/10` : ''}</p>
          <div role="group" aria-label="Your rating" className="flex flex-wrap gap-1.5">
            {Array.from({ length: 10 }, (_, i) => i + 1).map((n) => (
              <button
                key={n}
                type="button"
                disabled={busy}
                aria-label={`Rate ${n} out of 10`}
                aria-pressed={entry.rating === n}
                onClick={() => save.mutate({ status: 'WATCHED', rating: entry.rating === n ? null : n, note: entry.note })}
                className={`grid size-9 place-items-center rounded-lg border text-sm font-bold transition-colors disabled:opacity-60 ${
                  entry.rating === n ? 'border-gold bg-gold text-black' : 'border-line text-muted hover:border-gold hover:text-ink'
                }`}
              >
                {n}
              </button>
            ))}
          </div>
        </div>
      )}

      <label className="mt-4 block">
        <span className="mb-1.5 block text-sm font-semibold text-ink">Note</span>
        <textarea
          value={note}
          maxLength={500}
          rows={2}
          onChange={(e) => setNote(e.target.value)}
          placeholder="Why you want to watch it, or what you thought"
          className="w-full resize-y rounded-xl border border-line bg-bg px-3 py-2 text-sm text-ink placeholder:text-faint focus:border-ember-400 focus:outline-none"
        />
      </label>

      <div className="mt-3 flex flex-wrap items-center gap-2">
        <Button
          variant="secondary"
          className="py-2"
          disabled={busy || note.trim() === (entry.note ?? '')}
          onClick={() => save.mutate({ status: entry.status, rating: entry.rating, note: note.trim() || null })}
        >
          Save note
        </Button>
        <Button variant="ghost" className="py-2" disabled={busy} onClick={() => remove.mutate()}>
          <Icon name="trash" className="size-4" /> Remove from list
        </Button>
      </div>
      {error && <p role="alert" className="mt-3 text-sm text-ember-400">{errorMessage(error)}</p>}
    </section>
  )
}
