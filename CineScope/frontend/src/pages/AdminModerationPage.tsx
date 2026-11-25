import { Link, useSearchParams } from 'react-router-dom'
import { errorMessage } from '../api/client'
import { useModerate, useModerationQueue } from '../api/queries'
import type { CommentView } from '../api/types'
import { AdminTabs } from '../components/AdminTabs'
import { ProcessingSteps } from '../components/ProcessingSteps'
import { Button, EmptyState, ErrorState, Skeleton } from '../components/ui'
import { useDocumentTitle } from '../hooks'
import { timeAgo } from '../lib/time'

/**
 * "Moderation" (route /admin/moderation, administrators only - auth/RequireAdmin): the comments the processing pipeline held
 * back (backend service/CommentProcessor: strong language, link spam), each with the text as masked, who wrote it, where, and
 * the full list of processing stages that led to the hold. The administrator approves it (it becomes public) or rejects it (only its
 * author keeps seeing it). The other tabs show what was rejected and what is published. The status and the page are in the
 * address (?status=&page=). Data and decisions go through GET /api/admin/comments and PUT /api/admin/comments/{id}/moderation.
 */
const STATUSES = [
  { value: 'PENDING', label: 'Waiting for review' },
  { value: 'REJECTED', label: 'Rejected' },
  { value: 'PUBLISHED', label: 'Published' },
]

export function AdminModerationPage() {
  useDocumentTitle('Moderation')
  const [params, setParams] = useSearchParams()
  const status = STATUSES.find((s) => s.value === params.get('status'))?.value ?? 'PENDING'
  const page = Math.max(1, Number(params.get('page')) || 1)
  const queue = useModerationQueue(status, page)
  const moderate = useModerate()

  function update(changes: Record<string, string | null>) {
    const next = new URLSearchParams(params)
    for (const [key, value] of Object.entries(changes)) {
      if (value) next.set(key, value)
      else next.delete(key)
    }
    setParams(next, { replace: true })
  }

  return (
    <div className="mx-auto max-w-4xl px-4 pb-10 pt-28 sm:px-8">
      <h1 className="text-3xl font-extrabold tracking-tight text-ink sm:text-4xl">Moderation</h1>
      <p className="mt-1 mb-6 text-sm text-muted">Comments are checked automatically when they are posted. Those the checks held back wait here for a person to decide.</p>
      <AdminTabs />

      <div role="group" aria-label="Show comments" className="flex flex-wrap gap-2">
        {STATUSES.map((s) => (
          <button
            key={s.value}
            type="button"
            aria-pressed={status === s.value}
            onClick={() => update({ status: s.value === 'PENDING' ? null : s.value, page: null })}
            className={`rounded-full border px-4 py-2 text-sm font-semibold transition-colors ${
              status === s.value ? 'border-ember-500 bg-ember-500 text-white' : 'border-line text-muted hover:border-ember-400 hover:text-ink'
            }`}
          >
            {s.label}
          </button>
        ))}
      </div>

      {moderate.isError && (
        <p role="alert" className="mt-4 rounded-xl border border-ember-500/40 bg-ember-500/10 px-4 py-3 text-sm text-ink">
          {errorMessage(moderate.error)}
        </p>
      )}

      <div className="mt-6 space-y-4">
        {queue.isLoading ? (
          <div aria-busy="true" className="space-y-3">
            {[0, 1].map((i) => (
              <Skeleton key={i} className="h-40 w-full" />
            ))}
          </div>
        ) : queue.isError ? (
          <ErrorState message={errorMessage(queue.error)} onRetry={() => queue.refetch()} />
        ) : queue.data!.items.length === 0 ? (
          <EmptyState title={status === 'PENDING' ? 'Nothing is waiting for review' : 'Nothing here'} hint={status === 'PENDING' ? 'Held comments will appear here.' : undefined} />
        ) : (
          queue.data!.items.map((comment) => <QueueCard key={comment.id} comment={comment} busy={moderate.isPending} onDecide={(action) => moderate.mutate({ id: comment.id, action })} />)
        )}
      </div>

      {queue.data && queue.data.totalPages > 1 && (
        <nav aria-label="Pages" className="mt-8 flex items-center justify-center gap-4">
          <Button variant="secondary" disabled={page <= 1} onClick={() => update({ page: page > 2 ? String(page - 1) : null })}>
            Previous
          </Button>
          <span className="text-sm text-muted">
            Page {queue.data.page} of {queue.data.totalPages}
          </span>
          <Button variant="secondary" disabled={page >= queue.data.totalPages} onClick={() => update({ page: String(page + 1) })}>
            Next
          </Button>
        </nav>
      )}
    </div>
  )
}

function QueueCard({ comment, busy, onDecide }: { comment: CommentView; busy: boolean; onDecide: (action: 'APPROVE' | 'REJECT') => void }) {
  return (
    <article data-comment-id={comment.id} data-author={comment.authorUsername} aria-label={`Comment by ${comment.authorName}`} className="rounded-2xl border border-line bg-surface p-5">
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm">
        <span className="font-semibold text-ink">{comment.authorName}</span>
        <span className="text-faint">@{comment.authorUsername}</span>
        <span className="text-faint">{timeAgo(comment.createdAt)}</span>
        <Link to={`/title/${comment.type}/${comment.tmdbId}`} className="font-semibold text-ember-400 hover:underline">
          Open the {comment.type === 'tv' ? 'show' : 'film'} page
        </Link>
        {comment.parentId !== null && <span className="rounded-full border border-line px-2 py-0.5 text-xs text-muted">a reply</span>}
        <span className="ml-auto rounded-full border border-line px-2.5 py-0.5 text-xs font-semibold text-muted">{comment.status.toLowerCase()}</span>
      </div>
      <p className="mt-3 whitespace-pre-line break-words text-ink/90">{comment.body}</p>
      <div className="mt-4 rounded-xl border border-line bg-surface-2 p-3">
        <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-faint">Why it is here</p>
        <ProcessingSteps steps={comment.steps} />
      </div>
      {comment.status !== 'PUBLISHED' && (
        <div className="mt-4 flex flex-wrap gap-3">
          <Button disabled={busy || comment.status === 'REJECTED'} onClick={() => onDecide('APPROVE')} aria-label={`Approve comment by ${comment.authorUsername}`}>
            Approve
          </Button>
          <Button variant="danger" disabled={busy || comment.status === 'REJECTED'} onClick={() => onDecide('REJECT')} aria-label={`Reject comment by ${comment.authorUsername}`}>
            Reject
          </Button>
          {comment.status === 'REJECTED' && (
            <Button variant="secondary" disabled={busy} onClick={() => onDecide('APPROVE')} aria-label={`Approve comment by ${comment.authorUsername} after all`}>
              Approve after all
            </Button>
          )}
        </div>
      )}
    </article>
  )
}
