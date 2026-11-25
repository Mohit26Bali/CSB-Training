import type { DiscussionSummary } from '../api/types'

/**
 * The numbers above the discussion of a title (components/Discussion), all worked out by the backend (service/CommentService
 * #summary) from the published comments:
 *   - the CineScope score: TMDB's rating blended with the community's ratings (a Bayesian average, so a title with few ratings
 *     stays near TMDB's score and one with many follows the community);
 *   - a histogram of how many people gave each rating from 1 to 10;
 *   - the audience mood: the share of positive, neutral and critical comments, from the sentiment analysis of each comment;
 *   - the topics people mention most (common words and emotion words left out).
 * Shown as plain bars and chips; every bar carries its count in {@code data-count} for the tests and in its label.
 */
export function DiscussionSummaryPanel({ summary }: { summary: DiscussionSummary }) {
  const { sentiment: mood, histogram } = summary
  const total = summary.commentCount + summary.replyCount
  const tallest = Math.max(1, ...histogram)

  return (
    <div className="grid gap-4 md:grid-cols-3" data-testid="discussion-summary">
      <section aria-label="CineScope score" className="rounded-2xl border border-line bg-surface p-5">
        <h3 className="text-xs font-semibold uppercase tracking-wide text-faint">CineScope score</h3>
        <p className="mt-2 text-5xl font-extrabold tracking-tight text-ink" data-testid="community-score">
          {summary.communityScore !== null ? summary.communityScore.toFixed(1) : '—'}
          <span className="text-lg font-medium text-faint"> / 10</span>
        </p>
        <dl className="mt-3 space-y-1 text-sm text-muted">
          <div className="flex justify-between gap-3">
            <dt>TMDB</dt>
            <dd className="font-semibold text-ink">{summary.tmdbRating !== null ? summary.tmdbRating.toFixed(1) : 'not available'}</dd>
          </div>
          <div className="flex justify-between gap-3">
            <dt>Community</dt>
            <dd className="font-semibold text-ink" data-testid="community-average">
              {summary.averageRating !== null ? `${summary.averageRating.toFixed(1)} (${summary.ratingCount} rating${summary.ratingCount === 1 ? '' : 's'})` : 'no ratings yet'}
            </dd>
          </div>
        </dl>
        <p className="mt-3 text-xs text-faint">TMDB’s score blended with what members here rated it; more ratings, more weight.</p>
      </section>

      <section aria-label="Ratings" className="rounded-2xl border border-line bg-surface p-5">
        <h3 className="text-xs font-semibold uppercase tracking-wide text-faint">How members rated it</h3>
        <ul aria-label="Rating distribution" className="mt-4 flex h-28 items-end gap-1.5">
          {histogram.map((count, i) => (
            <li
              key={i}
              data-rating={i + 1}
              data-count={count}
              aria-label={`${i + 1} out of 10: ${count} rating${count === 1 ? '' : 's'}`}
              className="flex h-full flex-1 flex-col items-center justify-end gap-1"
            >
              <span className="text-[10px] font-semibold text-muted">{count > 0 ? count : ''}</span>
              <span className="w-full rounded-t bg-gold/80 transition-all duration-500" style={{ height: `${Math.max(count > 0 ? 8 : 2, (count / tallest) * 80)}%`, opacity: count > 0 ? 1 : 0.25 }} />
            </li>
          ))}
        </ul>
        <div aria-hidden="true" className="mt-1 flex gap-1.5 text-center text-[10px] text-faint">
          {histogram.map((_, i) => (
            <span key={i} className="flex-1">
              {i + 1}
            </span>
          ))}
        </div>
      </section>

      <section aria-label="Audience mood" className="rounded-2xl border border-line bg-surface p-5">
        <h3 className="text-xs font-semibold uppercase tracking-wide text-faint">Audience mood</h3>
        {total === 0 ? (
          <p className="mt-4 text-sm text-muted">No comments yet. Be the first to share what you thought.</p>
        ) : (
          <>
            <div className="mt-4 flex h-3 overflow-hidden rounded-full bg-line" role="img" aria-label={`${mood.positivePercent}% positive, ${mood.neutralPercent}% neutral, ${mood.negativePercent}% critical`}>
              <span className="bg-emerald-500" style={{ width: `${mood.positivePercent}%` }} />
              <span className="bg-slate-400" style={{ width: `${mood.neutralPercent}%` }} />
              <span className="bg-rose-500" style={{ width: `${mood.negativePercent}%` }} />
            </div>
            <ul className="mt-3 space-y-1 text-sm">
              <li data-mood="positive" className="flex justify-between text-emerald-500"><span>Positive</span><span className="font-semibold">{mood.positivePercent}%</span></li>
              <li data-mood="neutral" className="flex justify-between text-muted"><span>Neutral</span><span className="font-semibold">{mood.neutralPercent}%</span></li>
              <li data-mood="negative" className="flex justify-between text-rose-400"><span>Critical</span><span className="font-semibold">{mood.negativePercent}%</span></li>
            </ul>
            <p className="mt-2 text-xs text-faint">From the sentiment analysis of {total} comment{total === 1 ? '' : 's'} and repl{total === 1 ? 'y' : 'ies'}.</p>
          </>
        )}
      </section>

      {summary.topics.length > 0 && (
        <section aria-label="Topics people mention" className="rounded-2xl border border-line bg-surface p-5 md:col-span-3">
          <h3 className="text-xs font-semibold uppercase tracking-wide text-faint">What people mention</h3>
          <ul className="mt-3 flex flex-wrap gap-2">
            {summary.topics.map((topic) => (
              <li key={topic.word} data-topic={topic.word} className="rounded-full border border-line px-3 py-1 text-sm text-ink">
                {topic.word} <span className="text-faint">×{topic.count}</span>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  )
}
