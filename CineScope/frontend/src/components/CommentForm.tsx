import { useState, type FormEvent } from 'react'
import { ApiError, errorMessage } from '../api/client'
import type { CommentPayload, CommentView } from '../api/types'
import { ProcessingSteps } from './ProcessingSteps'
import { Button } from './ui'

/**
 * One form for every way of writing a comment: a new review (with the 1-10 rating buttons), a reply (no rating), or an edit
 * (starting from the existing text). The caller supplies {@code onSubmit}, which performs the request and returns the saved
 * comment. When {@code showProcessing} is on (new reviews and replies), the saved comment's processing stages then appear below
 * the form one after another - the visible result of the backend pipeline (service/CommentProcessor): what was cleaned or
 * masked, whether it was held for a moderator, and the sentiment it was judged to have. Server refusals (already rated, posting
 * too fast, a duplicate) are shown in plain words under the box.
 */
export function CommentForm({
  label,
  placeholder,
  submitLabel,
  initialBody = '',
  initialRating = null,
  allowRating,
  showProcessing = false,
  autoFocus = false,
  onSubmit,
  onDone,
  onCancel,
}: {
  label: string
  placeholder: string
  submitLabel: string
  initialBody?: string
  initialRating?: number | null
  allowRating: boolean
  showProcessing?: boolean
  autoFocus?: boolean
  onSubmit: (payload: CommentPayload) => Promise<CommentView>
  /** Called after a successful save (the edit and reply forms close themselves here). */
  onDone?: () => void
  onCancel?: () => void
}) {
  const [body, setBody] = useState(initialBody)
  const [rating, setRating] = useState<number | null>(initialRating)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState<CommentView | null>(null)
  const [finished, setFinished] = useState(false)
  const trimmed = body.trim()

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    if (trimmed.length < 2) {
      setError('Write at least a couple of characters')
      return
    }
    setBusy(true)
    try {
      const result = await onSubmit({ body: trimmed, rating: allowRating ? rating : null })
      if (showProcessing) {
        setSaved(result)
        setFinished(false)
        setBody('')
        setRating(null)
      }
      onDone?.()
    } catch (err) {
      const fields = err instanceof ApiError ? Object.values(err.fieldErrors) : []
      setError(fields.length > 0 ? fields.join('. ') : errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div>
      <form onSubmit={submit} noValidate className="space-y-3">
        <label className="block">
          <span className="mb-1.5 block text-sm font-semibold text-ink">{label}</span>
          <textarea
            value={body}
            maxLength={1000}
            rows={3}
            autoFocus={autoFocus}
            placeholder={placeholder}
            onChange={(e) => {
              setBody(e.target.value)
              if (saved) setSaved(null) // a new thought starts: the last result is no longer news
            }}
            className="w-full resize-y rounded-xl border border-line bg-bg px-3 py-2 text-sm text-ink placeholder:text-faint focus:border-ember-400 focus:outline-none"
          />
        </label>

        {allowRating && (
          <div role="group" aria-label="Review rating (optional)">
            <p className="mb-1.5 text-sm font-semibold text-ink">
              Your rating <span className="font-normal text-faint">(optional{rating ? `: ${rating}/10` : ''})</span>
            </p>
            <div className="flex flex-wrap gap-1.5">
              {Array.from({ length: 10 }, (_, i) => i + 1).map((n) => (
                <button
                  key={n}
                  type="button"
                  aria-label={`Review rating: ${n} out of 10`}
                  aria-pressed={rating === n}
                  onClick={() => setRating(rating === n ? null : n)}
                  className={`grid size-9 place-items-center rounded-lg border text-sm font-bold transition-colors ${
                    rating === n ? 'border-gold bg-gold text-black' : 'border-line text-muted hover:border-gold hover:text-ink'
                  }`}
                >
                  {n}
                </button>
              ))}
            </div>
          </div>
        )}

        {error && (
          <p role="alert" className="rounded-xl border border-ember-500/40 bg-ember-500/10 px-4 py-2.5 text-sm text-ink">
            {error}
          </p>
        )}

        <div className="flex flex-wrap items-center gap-3">
          <Button type="submit" loading={busy} disabled={trimmed.length < 2}>
            {submitLabel}
          </Button>
          {onCancel && (
            <Button variant="ghost" onClick={onCancel} disabled={busy}>
              Cancel
            </Button>
          )}
          <span className="ml-auto text-xs text-faint" aria-live="off">
            {body.length}/1000
          </span>
        </div>
      </form>

      {saved && (
        <section aria-label="Processing result" className="mt-4 rounded-xl border border-line bg-surface-2 p-4">
          <p className="mb-2 text-sm font-bold text-ink">Processing your comment</p>
          <ProcessingSteps steps={saved.steps} animate onFinished={() => setFinished(true)} />
          {finished && (
            <p role="status" className="mt-3 text-sm font-semibold text-ink">
              {saved.status === 'PENDING' ? 'Held for review. Only you and the moderators can see it for now.' : 'Published. Thanks for sharing!'}
            </p>
          )}
        </section>
      )}
    </div>
  )
}
