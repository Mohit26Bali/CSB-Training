import { useState } from 'react'
import { errorMessage } from '../api/client'
import { useDeleteComment, useEditComment, useReplyToComment, useToggleHelpful } from '../api/queries'
import type { CommentView, MediaType, Sentiment } from '../api/types'
import { initials } from '../lib/format'
import { timeAgo } from '../lib/time'
import { CommentForm } from './CommentForm'
import { ConfirmDialog } from './ConfirmDialog'
import { ProcessingSteps } from './ProcessingSteps'
import { Button } from './ui'

/**
 * One comment of the discussion (components/Discussion) with everything you can do to it: mark it helpful, reply, see how it
 * was processed, and - for your own - edit or delete. Replies are drawn inside it, one level deep (the backend does not allow a
 * reply to a reply). The sentiment pill and the "How was this processed?" list show the result of the backend pipeline
 * (service/CommentProcessor); a comment of yours that was held for a moderator says so. Writes go through the hooks in
 * api/queries.ts, which refresh the list and the summary afterwards.
 */
const SENTIMENT: Record<Sentiment, { label: string; tone: string }> = {
  POSITIVE: { label: 'Positive', tone: 'border-emerald-500/40 bg-emerald-500/10 text-emerald-500' },
  NEUTRAL: { label: 'Neutral', tone: 'border-line text-muted' },
  NEGATIVE: { label: 'Critical', tone: 'border-rose-500/40 bg-rose-500/10 text-rose-400' },
}

export function CommentItem({ comment, type, id }: { comment: CommentView; type: MediaType; id: number }) {
  const isReply = comment.parentId !== null
  const helpful = useToggleHelpful(type, id)
  const reply = useReplyToComment(type, id)
  const edit = useEditComment(type, id)
  const remove = useDeleteComment(type, id)
  const [replying, setReplying] = useState(false)
  const [editing, setEditing] = useState(false)
  const [showSteps, setShowSteps] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const sentiment = SENTIMENT[comment.sentiment]
  const score = `${comment.sentimentScore >= 0 ? '+' : ''}${comment.sentimentScore.toFixed(2)}`
  const published = comment.status === 'PUBLISHED'

  return (
    <article data-comment-id={comment.id} data-author={comment.authorUsername} aria-label={`Comment by ${comment.authorName}`} className={isReply ? 'pt-4' : 'rounded-2xl border border-line bg-surface p-4 sm:p-5'}>
      <div className="flex gap-3">
        <span aria-hidden="true" className="grid size-9 shrink-0 place-items-center rounded-full bg-linear-to-br from-slate-600 to-slate-800 text-xs font-bold text-white/80">
          {initials(comment.authorName)}
        </span>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-x-2.5 gap-y-1">
            <span className="font-semibold text-ink">{comment.authorName}</span>
            {comment.mine && <span className="rounded-full bg-ember-500/15 px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide text-ember-400">You</span>}
            <span className="text-xs text-faint">{timeAgo(comment.createdAt)}</span>
            {comment.rating !== null && (
              <span className="inline-flex items-center gap-1 rounded-full bg-gold/15 px-2.5 py-0.5 text-xs font-bold text-gold" aria-label={`Rated ${comment.rating} out of 10`}>
                ★ {comment.rating}/10
              </span>
            )}
            <span data-testid="sentiment" className={`rounded-full border px-2.5 py-0.5 text-xs font-semibold ${sentiment.tone}`} title="How the comment's wording was judged">
              {sentiment.label} ({score})
            </span>
          </div>

          {comment.status === 'PENDING' && (
            <p role="status" className="mt-2 rounded-lg border border-amber-500/40 bg-amber-500/10 px-3 py-2 text-xs text-ink">
              Held for review. Only you and the moderators can see this for now.
            </p>
          )}
          {comment.status === 'REJECTED' && (
            <p role="status" className="mt-2 rounded-lg border border-rose-500/40 bg-rose-500/10 px-3 py-2 text-xs text-ink">
              A moderator removed this from public view.
            </p>
          )}

          {editing ? (
            <div className="mt-3">
              <CommentForm
                label="Edit your comment"
                placeholder=""
                submitLabel="Save changes"
                initialBody={comment.body}
                initialRating={comment.rating}
                allowRating={!isReply}
                autoFocus
                onSubmit={(payload) => edit.mutateAsync({ commentId: comment.id, payload })}
                onDone={() => setEditing(false)}
                onCancel={() => setEditing(false)}
              />
            </div>
          ) : (
            <p className="mt-2 whitespace-pre-line break-words text-sm leading-relaxed text-ink/90">{comment.body}</p>
          )}

          {!editing && (
            <div className="mt-3 flex flex-wrap items-center gap-x-1 gap-y-1 text-sm">
              <Button
                variant="ghost"
                className="px-3 py-1.5"
                aria-pressed={comment.helpfulByMe}
                disabled={comment.mine || !published || helpful.isPending}
                title={comment.mine ? 'You can’t mark your own comment as helpful' : undefined}
                onClick={() => helpful.mutate(comment.id)}
              >
                <span aria-hidden="true">{comment.helpfulByMe ? '👍' : '👍🏻'}</span> Helpful{comment.helpfulCount > 0 ? ` · ${comment.helpfulCount}` : ''}
              </Button>
              {!isReply && published && (
                <Button variant="ghost" className="px-3 py-1.5" aria-expanded={replying} onClick={() => setReplying((r) => !r)}>
                  Reply
                </Button>
              )}
              <Button variant="ghost" className="px-3 py-1.5" aria-expanded={showSteps} onClick={() => setShowSteps((s) => !s)}>
                How was this processed?
              </Button>
              {comment.mine && (
                <>
                  <Button variant="ghost" className="px-3 py-1.5" onClick={() => setEditing(true)}>
                    Edit
                  </Button>
                  <Button variant="ghost" className="px-3 py-1.5" onClick={() => { remove.reset(); setConfirmDelete(true) }}>
                    Delete
                  </Button>
                </>
              )}
              {helpful.isError && <span role="alert" className="text-xs text-ember-400">{errorMessage(helpful.error)}</span>}
            </div>
          )}

          {showSteps && (
            <div className="mt-3 rounded-xl border border-line bg-surface-2 p-3">
              <ProcessingSteps steps={comment.steps} />
            </div>
          )}

          {replying && (
            <div className="mt-3 rounded-xl border border-line bg-surface-2 p-3">
              <CommentForm
                label={`Reply to ${comment.authorName}`}
                placeholder="Write a reply"
                submitLabel="Post reply"
                allowRating={false}
                showProcessing
                autoFocus
                onSubmit={(payload) => reply.mutateAsync({ parentId: comment.id, payload })}
                onCancel={() => setReplying(false)}
              />
            </div>
          )}

          {comment.replies.length > 0 && (
            <div className="mt-3 divide-y divide-line border-l-2 border-line pl-4" aria-label={`Replies to ${comment.authorName}`} role="group">
              {comment.replies.map((r) => (
                <CommentItem key={r.id} comment={r} type={type} id={id} />
              ))}
            </div>
          )}
        </div>
      </div>

      <ConfirmDialog
        open={confirmDelete}
        title="Delete this comment?"
        confirmLabel="Delete comment"
        busy={remove.isPending}
        error={remove.isError ? errorMessage(remove.error) : null}
        onConfirm={() => remove.mutate(comment.id, { onSuccess: () => setConfirmDelete(false) })}
        onCancel={() => setConfirmDelete(false)}
      >
        <p className="text-sm text-ink">
          {isReply ? 'This reply will be removed for good.' : 'This comment, its rating and all replies to it will be removed for good.'}
        </p>
      </ConfirmDialog>
    </article>
  )
}
