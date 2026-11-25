import { Link, useSearchParams } from 'react-router-dom'
import { errorMessage } from '../api/client'
import { useTonight } from '../api/queries'
import type { Recommendation, WatchProvider, WatchProviders } from '../api/types'
import { Icon } from '../components/Icon'
import { RatingRing } from '../components/RatingRing'
import { Row } from '../components/Row'
import { TitleCard } from '../components/TitleCard'
import { WatchlistControl } from '../components/WatchlistControl'
import { Button, Chip, EmptyState, ErrorState, Skeleton } from '../components/ui'
import { useDocumentTitle } from '../hooks'
import { buttonClass } from '../lib/buttonStyles'
import { formatRuntime } from '../lib/format'
import { backdropUrl, logoUrl, posterUrl } from '../lib/images'
import { LANGUAGES, MOODS, TIMES, newSeed, type TonightWishes } from '../lib/tonight'

/**
 * "What should I watch tonight?" (route /tonight). The visitor picks a few wishes - movie or show, a mood, how much time
 * they have, a language, and whether it must stream in India - and the backend (GET /api/tonight, api/queries.ts
 * useTonight) answers with one pick, why it fits, where to watch it, and a few alternatives.
 *
 * Everything lives in the address (?type=&mood=&time=&lang=&stream=1&family=1&seed=): reloading or sharing the link shows the same
 * pick, because the same wishes with the same seed always give the same answer. "Find something" and "Show me another"
 * just put a new seed in the address. Because everyone here is signed in, the answer is always personal: what they have watched is skipped and titles on their
 * "want to watch" list are preferred.
 */
export function TonightPage() {
  useDocumentTitle('What to watch tonight')
  const [params, setParams] = useSearchParams()

  const wishes: TonightWishes = {
    type: params.get('type') === 'tv' ? 'tv' : 'movie',
    mood: MOODS.find((m) => m.key === params.get('mood'))?.key ?? null,
    maxMinutes: TIMES.find((t) => String(t.value) === params.get('time'))?.value ?? null,
    language: LANGUAGES.find((l) => l.code === params.get('lang'))?.code ?? null,
    streamingOnly: params.get('stream') === '1',
    familySafe: params.get('family') === '1',
  }
  const seed = Number(params.get('seed')) || null
  const result = useTonight(wishes, seed)

  /** Change wishes in the address; if a result is showing, draw again for the new wishes. */
  function update(changes: Record<string, string | null>) {
    const next = new URLSearchParams(params)
    for (const [key, value] of Object.entries(changes)) {
      if (value) next.set(key, value)
      else next.delete(key)
    }
    if (seed) next.set('seed', String(newSeed()))
    setParams(next, { replace: true })
  }

  const find = () => {
    const next = new URLSearchParams(params)
    next.set('seed', String(newSeed()))
    setParams(next, { replace: true })
  }

  return (
    <div className="mx-auto max-w-5xl px-4 pb-10 pt-28 sm:px-8">
      <h1 className="text-3xl font-extrabold tracking-tight text-ink sm:text-5xl">What should I watch tonight?</h1>
      <p className="mt-2 max-w-2xl text-muted">Tell us the mood and how much time you have. We’ll pick one thing and have a few backups ready.</p>

      <section aria-label="Your wishes" className="mt-8 space-y-6 rounded-2xl border border-line bg-surface p-5 sm:p-6">
        <Choice label="I feel like">
          <Pill selected={wishes.type === 'movie'} onClick={() => update({ type: null })}>
            A movie
          </Pill>
          <Pill selected={wishes.type === 'tv'} onClick={() => update({ type: 'tv' })}>
            A TV show
          </Pill>
        </Choice>

        <Choice label="Mood">
          <Pill selected={!wishes.mood} onClick={() => update({ mood: null })}>
            Anything
          </Pill>
          {MOODS.map((m) => (
            <Pill key={m.key} selected={wishes.mood === m.key} onClick={() => update({ mood: m.key })}>
              {m.label}
            </Pill>
          ))}
        </Choice>

        <Choice label={wishes.type === 'tv' ? 'Time per episode' : 'Time I have'}>
          <Pill selected={!wishes.maxMinutes} onClick={() => update({ time: null })}>
            Any length
          </Pill>
          {TIMES.map((t) => (
            <Pill key={t.value} selected={wishes.maxMinutes === t.value} onClick={() => update({ time: String(t.value) })}>
              {t.label}
            </Pill>
          ))}
        </Choice>

        <div className="flex flex-wrap items-end gap-x-8 gap-y-4">
          <label className="block text-sm font-semibold text-ink">
            Language
            <select
              value={wishes.language ?? ''}
              onChange={(e) => update({ lang: e.target.value || null })}
              className="mt-1.5 block h-11 min-w-44 rounded-xl border border-line bg-bg px-3 text-sm font-normal text-ink focus:border-ember-400 focus:outline-none"
            >
              <option value="">Any language</option>
              {LANGUAGES.map((l) => (
                <option key={l.code} value={l.code}>
                  {l.label}
                </option>
              ))}
            </select>
          </label>
          <label className="flex h-11 cursor-pointer items-center gap-2.5 text-sm font-semibold text-ink">
            <input
              type="checkbox"
              checked={wishes.streamingOnly}
              onChange={(e) => update({ stream: e.target.checked ? '1' : null })}
              className="size-5 accent-ember-500"
            />
            Only what I can stream in India
          </label>
          <label className="flex h-11 cursor-pointer items-center gap-2.5 text-sm font-semibold text-ink">
            <input
              type="checkbox"
              checked={wishes.familySafe}
              onChange={(e) => update({ family: e.target.checked ? '1' : null })}
              className="size-5 accent-ember-500"
            />
            Safe for the whole family
          </label>
        </div>

        <div className="flex flex-wrap items-center gap-4">
          <Button onClick={find} loading={!!seed && result.isFetching} className="px-6 py-3">
            {!seed ? 'Find something' : 'Show me another'}
          </Button>
        </div>
      </section>

      <div className="mt-10" aria-live="polite">
        {!seed ? null : result.isLoading ? (
          <div aria-busy="true" className="grid gap-6 md:grid-cols-[14rem_1fr]">
            <Skeleton className="aspect-2/3 w-44 md:w-full" />
            <div className="space-y-3">
              <Skeleton className="h-10 w-2/3" />
              <Skeleton className="h-28 w-full" />
            </div>
          </div>
        ) : result.isError ? (
          <ErrorState message={errorMessage(result.error)} onRetry={() => result.refetch()} />
        ) : (
          <Answer r={result.data!} />
        )}
      </div>
    </div>
  )
}

function Choice({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div role="group" aria-label={label}>
      <p className="mb-2 text-sm font-semibold text-ink">{label}</p>
      <div className="flex flex-wrap gap-2">{children}</div>
    </div>
  )
}

function Pill({ selected, onClick, children }: { selected: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      aria-pressed={selected}
      onClick={onClick}
      className={`rounded-full border px-4 py-2 text-sm font-semibold transition-colors ${
        selected ? 'border-ember-500 bg-ember-500 text-white' : 'border-line text-muted hover:border-ember-400 hover:text-ink'
      }`}
    >
      {children}
    </button>
  )
}

function Answer({ r }: { r: Recommendation }) {
  if (!r.pick) {
    return (
      <EmptyState
        icon="film"
        title="Nothing matches all of that"
        hint="Try fewer filters: a longer time, any language, turn off “only what I can stream”, or turn off “safe for the whole family” (unrated titles are left out of it)."
      />
    )
  }
  const pick = r.pick
  const d = r.details
  const backdrop = backdropUrl(pick.backdropPath)
  const poster = posterUrl(pick.posterPath, 'w500')
  const runtime = formatRuntime(d?.runtime ?? null)

  return (
    <div className="space-y-12">
      <section aria-label="Tonight’s pick" className="relative overflow-hidden rounded-3xl border border-line">
        {backdrop && <img src={backdrop} alt="" className="absolute inset-0 size-full object-cover opacity-35" />}
        <div className="absolute inset-0 bg-linear-to-r from-bg via-bg/85 to-bg/40" />
        <div className="relative grid gap-6 p-5 sm:p-8 md:grid-cols-[14rem_1fr] md:gap-8">
          <div className="mx-auto w-44 md:mx-0 md:w-full">
            {poster ? (
              <img src={poster} alt={`Poster of ${pick.title}`} className="card-shadow aspect-2/3 w-full rounded-2xl object-cover ring-1 ring-line" />
            ) : (
              <div className="grid aspect-2/3 w-full place-items-center rounded-2xl bg-surface p-3 text-center font-semibold text-muted ring-1 ring-line">{pick.title}</div>
            )}
          </div>

          <div>
            <p className="text-sm font-bold uppercase tracking-wide text-ember-400">Tonight, watch</p>
            <h2 className="mt-1 text-3xl font-extrabold leading-tight tracking-tight text-ink sm:text-4xl">
              {pick.title} {pick.year && <span className="font-medium text-faint">({pick.year})</span>}
            </h2>

            <div className="mt-4 flex flex-wrap items-center gap-2.5">
              {d?.certification && <Chip className="border-ink/40 font-bold text-ink">{d.certification}</Chip>}
              <Chip>{pick.type === 'tv' ? 'TV show' : 'Movie'}</Chip>
              {runtime && <Chip>{runtime}{pick.type === 'tv' ? ' / episode' : ''}</Chip>}
              {d?.genres.map((g) => (
                <Chip key={g.id}>{g.name}</Chip>
              ))}
            </div>

            <div className="mt-5 flex items-center gap-3">
              <RatingRing rating={pick.rating} />
              <p className="text-sm font-semibold text-ink">User score</p>
            </div>

            {pick.overview && <p className="mt-5 max-w-2xl leading-relaxed text-ink/85">{pick.overview}</p>}

            {r.reasons.length > 0 && (
              <ul aria-label="Why this one" className="mt-5 flex flex-wrap gap-2">
                {r.reasons.map((reason) => (
                  <li key={reason} className="rounded-full bg-ember-500/15 px-3 py-1 text-xs font-semibold text-ember-400">
                    {reason}
                  </li>
                ))}
              </ul>
            )}

            <Streaming providers={r.providers} />

            <div className="mt-6 flex flex-wrap items-start gap-3">
              <Link to={`/title/${pick.type}/${pick.id}`} className={buttonClass('primary', 'px-6 py-3')}>
                <Icon name="info" className="size-5" /> Details &amp; trailer
              </Link>
              <WatchlistControl type={pick.type === 'tv' ? 'tv' : 'movie'} id={pick.id} />
            </div>
            <p className="mt-4 text-xs text-faint">Weighed {r.considered} titles.</p>
          </div>
        </div>
      </section>

      {r.alternatives.length > 0 && (
        <Row title="Or maybe one of these">
          {r.alternatives.map((s) => (
            <TitleCard key={`${s.type}-${s.id}`} title={{ ...s, type: s.type === 'tv' ? 'tv' : 'movie' }} />
          ))}
        </Row>
      )}
    </div>
  )
}

/** Where the pick streams (in the default country), most useful groups first. */
function Streaming({ providers }: { providers: WatchProviders | null }) {
  if (!providers) return null
  const groups: [string, WatchProvider[]][] = [
    ['Stream', providers.stream],
    ['Free', providers.free],
    ['Rent', providers.rent],
    ['Buy', providers.buy],
  ]
  const shown = groups.filter(([, list]) => list.length > 0)
  if (shown.length === 0) return <p className="mt-5 text-sm text-muted">Not listed on any service in India right now.</p>
  return (
    <div aria-label="Where to watch it" role="group" className="mt-5 space-y-2">
      {shown.slice(0, 2).map(([label, list]) => (
        <div key={label} className="flex flex-wrap items-center gap-2">
          <span className="w-14 text-xs font-semibold uppercase tracking-wide text-faint">{label}</span>
          {list.slice(0, 6).map((p) => {
            const logo = logoUrl(p.logoPath)
            return (
              <span key={p.id} title={p.name} className="inline-flex items-center gap-1.5 rounded-lg bg-surface px-2 py-1 text-xs font-semibold text-ink ring-1 ring-line">
                {logo && <img src={logo} alt="" className="size-5 rounded" />}
                {p.name}
              </span>
            )
          })}
        </div>
      ))}
    </div>
  )
}
