import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError, errorMessage } from '../api/client'
import { useTitle } from '../api/queries'
import type { MediaType, TitleDetail } from '../api/types'
import { buttonClass } from '../lib/buttonStyles'
import { Icon } from '../components/Icon'
import { RatingRing } from '../components/RatingRing'
import { Row } from '../components/Row'
import { TitleCard } from '../components/TitleCard'
import { Discussion } from '../components/Discussion'
import { EpisodeHeatmap } from '../components/EpisodeHeatmap'
import { Franchise } from '../components/Franchise'
import { TrailerModal } from '../components/TrailerModal'
import { WatchlistControl } from '../components/WatchlistControl'
import { WhereToWatch } from '../components/WhereToWatch'
import { Chip, EmptyState, ErrorState, SectionTitle, Skeleton } from '../components/ui'
import { useDocumentTitle } from '../hooks'
import { formatCount, formatDate, formatLanguage, formatMoney, formatRuntime, initials } from '../lib/format'
import { backdropUrl, posterUrl, profileUrl, youtubeThumbnail } from '../lib/images'

/**
 * The detail page of one movie or TV show (route /title/:type/:id): backdrop header with poster, score, genres,
 * runtime, age rating (for India, falling back to the US one) and overview; then trailers, facts, cast and similar
 * titles, "Where to watch" (components/WhereToWatch, its own request) the series a film belongs to (components/Franchise), the episode-rating heatmap of a show (components/EpisodeHeatmap) and the reviews and discussion (components/Discussion). Most of it comes from one request (GET /api/titles/{type}/{id}); the backend asks TMDB for credits, videos and
 * recommendations in the same call. Trailers play in a dialog (components/TrailerModal).
 */
export function TitlePage() {
  const params = useParams()
  const type: MediaType | null = params.type === 'movie' || params.type === 'tv' ? params.type : null
  const id = Number(params.id)
  const title = useTitle(type ?? 'movie', id)
  useDocumentTitle(title.data?.title)

  if (!type || !Number.isInteger(id) || id <= 0) {
    return <div className="px-4 pt-28"><EmptyState icon="film" title="That title doesn’t exist" hint="The link may be wrong." /></div>
  }
  if (title.isLoading) return <DetailSkeleton />
  if (title.isError) {
    const notFound = title.error instanceof ApiError && title.error.status === 404
    return (
      <div className="px-4 pt-28">
        {notFound ? (
          <EmptyState icon="film" title="Title not found" hint="It may have been removed from TMDB." />
        ) : (
          <ErrorState message={errorMessage(title.error)} onRetry={() => title.refetch()} />
        )}
      </div>
    )
  }
  return <Detail d={title.data!} />
}

function DetailSkeleton() {
  return (
    <div aria-busy="true" className="mx-auto grid max-w-7xl gap-8 px-4 pt-28 sm:px-8 md:grid-cols-[18rem_1fr]">
      <Skeleton className="aspect-2/3 w-56 md:w-full" />
      <div className="space-y-4">
        <Skeleton className="h-10 w-3/4" />
        <Skeleton className="h-5 w-1/2" />
        <Skeleton className="h-24 w-full" />
      </div>
    </div>
  )
}

function Detail({ d }: { d: TitleDetail }) {
  const [trailer, setTrailer] = useState<string | null>(null)
  const name = d.title ?? 'Untitled'
  const backdrop = backdropUrl(d.backdropPath)
  const poster = posterUrl(d.posterPath, 'w500')
  const runtime = formatRuntime(d.runtime)
  const mainVideo = d.videos[0]

  const facts: [string, string | null][] = [
    ['Status', d.status],
    ['Original language', formatLanguage(d.language)],
    ['Release date', formatDate(d.releaseDate)],
    ['Seasons', d.seasons ? String(d.seasons) : null],
    ['Episodes', d.episodes ? String(d.episodes) : null],
    ['Budget', formatMoney(d.budget)],
    ['Revenue', formatMoney(d.revenue)],
  ]

  return (
    <div>
      <section aria-label="Overview" className="relative -mt-16 overflow-hidden">
        {backdrop && <img src={backdrop} alt="" className="absolute inset-0 size-full object-cover opacity-45" />}
        <div className="absolute inset-0 bg-linear-to-t from-bg via-bg/75 to-bg/30" />
        <div className="absolute inset-0 bg-linear-to-r from-bg/80 to-transparent" />

        <div className="relative mx-auto grid max-w-7xl gap-8 px-4 pb-12 pt-28 sm:px-8 md:grid-cols-[18rem_1fr] md:gap-12 md:pt-32">
          <div className="mx-auto w-52 md:mx-0 md:w-full">
            {poster ? (
              <img src={poster} alt={`Poster of ${name}`} className="card-shadow aspect-2/3 w-full rounded-2xl object-cover ring-1 ring-line" />
            ) : (
              <div className="grid aspect-2/3 w-full place-items-center rounded-2xl bg-surface p-4 text-center font-semibold text-muted ring-1 ring-line">{name}</div>
            )}
          </div>

          <div className="fade-up">
            <h1 className="text-3xl font-extrabold leading-tight tracking-tight text-ink sm:text-5xl">
              {name} {d.year && <span className="font-medium text-faint">({d.year})</span>}
            </h1>
            {d.tagline && <p className="mt-2 text-base italic text-muted">{d.tagline}</p>}

            <div className="mt-5 flex flex-wrap items-center gap-2.5">
              {d.certification && <Chip className="border-ink/40 font-bold text-ink">{d.certification}</Chip>}
              <Chip className={d.familyFriendly ? 'border-emerald-500/60 font-semibold text-emerald-500' : ''} data-testid="family-verdict">
                {d.familyFriendly ? 'Family friendly' : d.certification ? 'Check the rating before a family watch' : 'No age rating available'}
              </Chip>
              <Chip>{d.type === 'tv' ? 'TV show' : 'Movie'}</Chip>
              {runtime && <Chip>{runtime}{d.type === 'tv' ? ' / episode' : ''}</Chip>}
              {d.genres.map((g) => (
                <Link key={g.id} to={`/browse?type=${d.type}&genre=${g.id}`} className="inline-flex rounded-full border border-line px-3 py-1 text-xs font-medium text-muted transition-colors hover:border-ember-400 hover:text-ember-400">
                  {g.name}
                </Link>
              ))}
            </div>

            <div className="mt-6 flex flex-wrap items-center gap-5">
              <div className="flex items-center gap-3">
                <RatingRing rating={d.rating} />
                <div className="text-sm leading-tight">
                  <p className="font-semibold text-ink">User score</p>
                  <p className="text-faint">{d.voteCount > 0 ? `${formatCount(d.voteCount)} votes` : 'No votes yet'}</p>
                </div>
              </div>
              {mainVideo && (
                <button type="button" onClick={() => setTrailer(mainVideo.key)} className={buttonClass('primary', 'px-6 py-3')}>
                  <Icon name="play" className="size-5" /> Watch trailer
                </button>
              )}
              {d.homepage && (
                <a href={d.homepage} target="_blank" rel="noreferrer" className={buttonClass('secondary', 'px-5 py-3')}>
                  Official site <Icon name="external" className="size-4" />
                </a>
              )}
            </div>

            <div className="mt-6">
              <WatchlistControl type={d.type} id={d.id} />
            </div>

            <h2 className="mt-8 text-lg font-bold text-ink">Overview</h2>
            <p className="mt-2 max-w-3xl text-base leading-relaxed text-ink/85">{d.overview ?? 'No description is available yet.'}</p>

            {d.directors.length > 0 && (
              <p className="mt-6 text-sm text-muted">
                <span className="font-semibold text-ink">{d.type === 'tv' ? 'Created by' : d.directors.length > 1 ? 'Directors' : 'Director'}:</span>{' '}
                {d.directors.map((c, i) => (
                  <span key={c.id}>
                    {i > 0 && ', '}
                    <Link to={`/person/${c.id}`} className="text-ink/85 underline-offset-2 hover:text-ember-400 hover:underline">
                      {c.name}
                    </Link>
                  </span>
                ))}
              </p>
            )}
          </div>
        </div>
      </section>

      <div className="mx-auto max-w-7xl space-y-14 px-4 pt-4 sm:px-8">
        <WhereToWatch key={`providers-${d.type}-${d.id}`} type={d.type} id={d.id} />

        {d.videos.length > 0 && (
          <section aria-label="Videos">
            <SectionTitle>Trailers &amp; teasers</SectionTitle>
            <div className="no-scrollbar -mx-4 flex snap-x scroll-px-4 gap-4 overflow-x-auto px-4 pb-2 sm:-mx-8 sm:scroll-px-8 sm:px-8">
              {d.videos.map((video) => (
                <button
                  key={video.key}
                  type="button"
                  onClick={() => setTrailer(video.key)}
                  aria-label={`Play ${video.name ?? video.type}`}
                  className="group w-64 shrink-0 snap-start text-left sm:w-72"
                >
                  <div className="relative aspect-video overflow-hidden rounded-xl bg-surface ring-1 ring-line">
                    <img src={youtubeThumbnail(video.key)} alt="" loading="lazy" className="size-full object-cover transition-transform duration-500 group-hover:scale-105" />
                    <span className="absolute inset-0 grid place-items-center bg-black/30 transition-colors group-hover:bg-black/10">
                      <span className="grid size-12 place-items-center rounded-full bg-ember-500 text-white shadow-lg">
                        <Icon name="play" className="size-5" />
                      </span>
                    </span>
                  </div>
                  <p className="clamp-2 mt-2 text-sm font-medium text-ink">{video.name ?? video.type}</p>
                  <p className="text-xs text-faint">{video.type}</p>
                </button>
              ))}
            </div>
          </section>
        )}

        {facts.some(([, value]) => value) && (
          <section aria-label="Facts">
            <SectionTitle>Facts</SectionTitle>
            <dl className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
              {facts
                .filter(([, value]) => value)
                .map(([label, value]) => (
                  <div key={label} className="rounded-xl border border-line bg-surface p-4">
                    <dt className="text-xs font-semibold uppercase tracking-wide text-faint">{label}</dt>
                    <dd className="mt-1 font-semibold text-ink">{value}</dd>
                  </div>
                ))}
            </dl>
          </section>
        )}

        {d.cast.length > 0 && (
          <Row title="Top cast">
            {d.cast.map((person) => {
              const photo = profileUrl(person.profilePath)
              return (
                <Link key={`${person.id}-${person.character}`} to={`/person/${person.id}`} aria-label={`${person.name}${person.character ? ` as ${person.character}` : ''}`} className="group block w-28 shrink-0 snap-start sm:w-32">
                  <div className="aspect-2/3 overflow-hidden rounded-xl bg-surface ring-1 ring-line">
                    {photo ? (
                      <img src={photo} alt="" loading="lazy" className="size-full object-cover" />
                    ) : (
                      <div className="grid size-full place-items-center bg-linear-to-br from-slate-700 to-slate-900 text-xl font-bold text-white/70">{initials(person.name)}</div>
                    )}
                  </div>
                  <p className="clamp-2 mt-2 text-sm font-semibold leading-snug text-ink group-hover:text-ember-400">{person.name}</p>
                  {person.character && <p className="clamp-2 text-xs text-faint">{person.character}</p>}
                </Link>
              )
            })}
          </Row>
        )}

        {d.type === 'movie' && <Franchise filmId={d.id} />}
        {d.type === 'tv' && <EpisodeHeatmap showId={d.id} />}

        <Discussion key={`discussion-${d.type}-${d.id}`} type={d.type} id={d.id} />

        {d.similar.length > 0 && (
          <Row title={`More like ${name}`}>
            {d.similar.map((item) => (
              <TitleCard key={`${item.type}-${item.id}`} title={item} />
            ))}
          </Row>
        )}
      </div>

      <TrailerModal videoKey={trailer} title={`${name} — ${d.videos.find((v) => v.key === trailer)?.name ?? 'trailer'}`} onClose={() => setTrailer(null)} />
    </div>
  )
}
