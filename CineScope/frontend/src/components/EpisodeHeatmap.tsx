import { errorMessage } from '../api/client'
import { useEpisodes } from '../api/queries'
import type { EpisodeRating, EpisodeSeason } from '../api/types'
import { Skeleton } from './ui'

/**
 * The episode-rating heatmap on a show's page (components on pages/TitlePage): one row per season, one cell per episode, each coloured
 * by TMDB's average vote from red (poor) through amber to green (excellent), so where a series peaks and dips can be seen at a glance.
 * Beneath it: the best and worst episode, the show's average, and the strongest and weakest season. The data comes from
 * GET /api/titles/tv/{id}/episodes (backend service/EpisodeRatingsService, which skips the "Specials" season).
 *
 * It is a real table, so a screen reader reads "Season 2, episode 4, rated 4.2" cell by cell, and each cell prints its number rather
 * than relying on colour alone. An episode nobody has rated is shown as a dash, an episode not aired yet as "soon": neither is a zero.
 * A wide show scrolls inside its own box, never the whole page.
 */
const LOW = 4
const HIGH = 9.5

/** Red at 4 and below, green at 9.5 and above, amber between; white text is readable on all of it. */
function ratingColor(rating: number): string {
  const t = Math.min(1, Math.max(0, (rating - LOW) / (HIGH - LOW)))
  return `hsl(${Math.round(t * 125)} 62% 34%)`
}

const label = (e: EpisodeRating) => `Season ${e.season}, episode ${e.number}, “${e.name}”`
const short = (e: EpisodeRating) => `S${e.season}E${e.number}`

function cellTitle(e: EpisodeRating): string {
  const when = e.airDate ? ` · ${e.aired ? 'aired' : 'airs'} ${e.airDate}` : ''
  if (e.rating !== null) return `${short(e)} · ${e.name} · ${e.rating.toFixed(1)}/10 (${e.votes} votes)${when}`
  return `${short(e)} · ${e.name} · ${e.aired ? 'not rated yet' : 'not aired yet'}${when}`
}

export function EpisodeHeatmap({ showId }: { showId: number }) {
  const episodes = useEpisodes(showId)

  if (episodes.isLoading) return <Skeleton className="mt-10 h-40 w-full" />
  if (episodes.isError) {
    return (
      <section aria-label="Episode ratings" className="mt-10">
        <h2 className="text-xl font-bold text-ink">Episode ratings</h2>
        <p className="mt-2 text-sm text-muted">{errorMessage(episodes.error)}</p>
      </section>
    )
  }
  const data = episodes.data!
  if (data.seasonCount === 0) return null // nothing to show (a mini-series TMDB lists no seasons for)

  const columns = Math.max(0, ...data.seasons.map((s) => Math.max(0, ...s.episodes.map((e) => e.number))))
  const ratedSeasons = data.seasons.filter((s): s is EpisodeSeason & { average: number } => s.average !== null)
  const strongest = ratedSeasons.length >= 2 ? ratedSeasons.reduce((a, b) => (b.average > a.average ? b : a)) : null
  const weakest = ratedSeasons.length >= 2 ? ratedSeasons.reduce((a, b) => (b.average < a.average ? b : a)) : null

  return (
    <section aria-label="Episode ratings" data-testid="heatmap" className="mt-10">
      <h2 className="text-xl font-bold text-ink">Episode ratings</h2>
      <p className="mt-1 text-sm text-muted">
        {data.ratedCount === 0 ? (
          'Nobody has rated the episodes yet.'
        ) : (
          <>
            <strong data-testid="heatmap-average" className="text-ink">{data.average?.toFixed(1)}</strong> on average across {data.ratedCount} rated {data.ratedCount === 1 ? 'episode' : 'episodes'}.
          </>
        )}
      </p>

      <div className="mt-4 overflow-x-auto rounded-xl border border-line bg-surface p-3">
        <table className="border-separate border-spacing-1 text-center text-xs">
          <caption className="sr-only">Average rating of each episode, by season</caption>
          <thead>
            <tr>
              <th scope="col" className="px-2 text-left font-semibold text-faint">
                <span className="sr-only">Season</span>
              </th>
              {Array.from({ length: columns }, (_, i) => (
                <th key={i} scope="col" className="min-w-9 font-semibold text-faint">
                  E{i + 1}
                </th>
              ))}
              <th scope="col" className="px-2 font-semibold text-faint">
                Avg
              </th>
            </tr>
          </thead>
          <tbody>
            {data.seasons.map((season) => (
              <tr key={season.number} data-season={season.number}>
                <th scope="row" className="whitespace-nowrap px-2 text-left font-semibold text-muted">
                  S{season.number}
                </th>
                {season.unavailable ? (
                  <td colSpan={columns + 1} className="px-2 py-2 text-left text-faint">
                    This season could not be loaded right now.
                  </td>
                ) : (
                  <>
                    {Array.from({ length: columns }, (_, i) => {
                      const e = season.episodes.find((x) => x.number === i + 1)
                      return e ? <Cell key={i} episode={e} /> : <td key={i} />
                    })}
                    <td data-testid={`season-average-${season.number}`} className="px-2 font-bold text-ink">
                      {season.average !== null ? season.average.toFixed(1) : '–'}
                    </td>
                  </>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="mt-3 flex flex-wrap items-center gap-x-6 gap-y-2 text-xs text-muted" aria-hidden="true">
        <span className="flex items-center gap-2">
          {LOW}
          <span className="h-2.5 w-28 rounded-full" style={{ background: `linear-gradient(to right, ${ratingColor(LOW)}, ${ratingColor(6.75)}, ${ratingColor(HIGH)})` }} />
          {HIGH}+
        </span>
        <span className="flex items-center gap-2">
          <span className="inline-block size-4 rounded border border-line" /> not rated yet
        </span>
        <span className="flex items-center gap-2">
          <span className="inline-block size-4 rounded border border-dashed border-line" /> not aired
        </span>
      </div>

      {(data.best || data.worst || strongest) && (
        <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-2 lg:grid-cols-4">
          {data.best && <Callout term="Best episode" testId="heatmap-best" tone="good" episode={data.best} />}
          {data.worst && <Callout term="Weakest episode" testId="heatmap-worst" tone="bad" episode={data.worst} />}
          {strongest && (
            <div className="rounded-xl border border-line bg-surface p-3">
              <dt className="text-xs font-semibold uppercase tracking-wide text-faint">Strongest season</dt>
              <dd data-testid="heatmap-strongest" className="mt-1 font-semibold text-ink">
                Season {strongest.number} · {strongest.average.toFixed(1)}
              </dd>
            </div>
          )}
          {weakest && (
            <div className="rounded-xl border border-line bg-surface p-3">
              <dt className="text-xs font-semibold uppercase tracking-wide text-faint">Weakest season</dt>
              <dd data-testid="heatmap-weakest" className="mt-1 font-semibold text-ink">
                Season {weakest.number} · {weakest.average.toFixed(1)}
              </dd>
            </div>
          )}
        </dl>
      )}
      {data.truncated && <p className="mt-3 text-xs text-faint">Only the first seasons are shown.</p>}
    </section>
  )
}

function Cell({ episode: e }: { episode: EpisodeRating }) {
  const rated = e.rating !== null
  return (
    <td
      data-episode={e.number}
      data-rating={rated ? e.rating : undefined}
      data-state={rated ? 'rated' : e.aired ? 'unrated' : 'unaired'}
      title={cellTitle(e)}
      className={`h-9 min-w-9 rounded-md align-middle font-bold ${rated ? 'text-white' : e.aired ? 'border border-line text-faint' : 'border border-dashed border-line text-faint'}`}
      style={rated ? { backgroundColor: ratingColor(e.rating!) } : undefined}
    >
      <span className="sr-only">{label(e)}, </span>
      {rated ? e.rating!.toFixed(1) : e.aired ? '–' : 'soon'}
      {!rated && <span className="sr-only">{e.aired ? ' not rated yet' : ' not aired yet'}</span>}
    </td>
  )
}

function Callout({ term, episode, tone, testId }: { term: string; episode: EpisodeRating; tone: 'good' | 'bad'; testId: string }) {
  return (
    <div className="rounded-xl border border-line bg-surface p-3">
      <dt className="text-xs font-semibold uppercase tracking-wide text-faint">{term}</dt>
      <dd data-testid={testId} className="mt-1 text-ink">
        <span className="font-semibold">
          {short(episode)} “{episode.name}”
        </span>{' '}
        <span className={`font-bold ${tone === 'good' ? 'text-emerald-500' : 'text-red-500'}`}>{episode.rating?.toFixed(1)}</span>
      </dd>
    </div>
  )
}
