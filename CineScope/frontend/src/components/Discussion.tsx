import { useState } from 'react'
import { errorMessage } from '../api/client'
import { useComments, useDiscussionSummary, usePostComment } from '../api/queries'
import type { CommentSort, MediaType } from '../api/types'
import { CommentForm } from './CommentForm'
import { CommentItem } from './CommentItem'
import { DiscussionSummaryPanel } from './DiscussionSummaryPanel'
import { Button, ErrorState, SectionTitle, Skeleton } from './ui'

/**
 * "Reviews &amp; discussion" on a title page (pages/TitlePage): the numbers the backend worked out from the comments
 * (components/DiscussionSummaryPanel), a form to write a review with an optional 1-10 rating (components/CommentForm, which
 * shows the processing of the text as it happens), and the comments themselves (components/CommentItem) sorted by what you pick -
 * most helpful, newest, most positive or most critical - five at a time. Everything comes from the endpoints of backend
 * controller/CommentController through api/queries.ts.
 */
const SORTS: { value: CommentSort; label: string }[] = [
  { value: 'helpful', label: 'Most helpful' },
  { value: 'newest', label: 'Newest' },
  { value: 'positive', label: 'Most positive' },
  { value: 'critical', label: 'Most critical' },
]

export function Discussion({ type, id }: { type: MediaType; id: number }) {
  const summary = useDiscussionSummary(type, id)
  const [sort, setSort] = useState<CommentSort>('helpful')
  const comments = useComments(type, id, sort)
  const post = usePostComment(type, id)
  const items = comments.data?.pages.flatMap((page) => page.items) ?? []
  const total = comments.data?.pages[0]?.totalResults ?? 0

  return (
    <section aria-label="Reviews and discussion">
      <SectionTitle>Reviews &amp; discussion</SectionTitle>

      {summary.isLoading ? (
        <div aria-busy="true" className="grid gap-4 md:grid-cols-3">
          {[0, 1, 2].map((i) => (
            <Skeleton key={i} className="h-44" />
          ))}
        </div>
      ) : summary.isError ? (
        <p role="alert" className="text-sm text-muted">Couldn’t load the numbers. {errorMessage(summary.error)}</p>
      ) : (
        <DiscussionSummaryPanel summary={summary.data!} />
      )}

      <div className="mt-6 rounded-2xl border border-line bg-surface p-4 sm:p-5">
        <CommentForm
          label="Write a review"
          placeholder="What did you think? Your comment is checked for spam and bad language and its tone is analysed when you post."
          submitLabel="Post comment"
          allowRating
          showProcessing
          onSubmit={(payload) => post.mutateAsync(payload)}
        />
      </div>

      <div className="mt-8 flex flex-wrap items-center justify-between gap-3">
        <h3 className="text-lg font-bold text-ink">
          Comments <span className="text-sm font-normal text-faint">({total})</span>
        </h3>
        <div role="group" aria-label="Sort comments" className="flex flex-wrap gap-2">
          {SORTS.map((s) => (
            <button
              key={s.value}
              type="button"
              aria-pressed={sort === s.value}
              onClick={() => setSort(s.value)}
              className={`rounded-full border px-3.5 py-1.5 text-sm font-semibold transition-colors ${
                sort === s.value ? 'border-ember-500 bg-ember-500 text-white' : 'border-line text-muted hover:border-ember-400 hover:text-ink'
              }`}
            >
              {s.label}
            </button>
          ))}
        </div>
      </div>

      <div className="mt-4 space-y-4">
        {comments.isLoading ? (
          <div aria-busy="true" className="space-y-3">
            {[0, 1].map((i) => (
              <Skeleton key={i} className="h-28 w-full" />
            ))}
          </div>
        ) : comments.isError ? (
          <ErrorState message={errorMessage(comments.error)} onRetry={() => comments.refetch()} />
        ) : items.length === 0 ? (
          <p className="rounded-2xl border border-dashed border-line p-8 text-center text-sm text-muted">No comments yet. Start the conversation!</p>
        ) : (
          items.map((comment) => <CommentItem key={comment.id} comment={comment} type={type} id={id} />)
        )}
      </div>

      {comments.hasNextPage && (
        <div className="mt-5 text-center">
          <Button variant="secondary" loading={comments.isFetchingNextPage} onClick={() => comments.fetchNextPage()}>
            Show more comments
          </Button>
        </div>
      )}
    </section>
  )
}
