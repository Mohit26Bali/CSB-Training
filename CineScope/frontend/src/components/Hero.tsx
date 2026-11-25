import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import type { TitleSummary } from '../api/types'
import { useFetchTitle } from '../api/queries'
import { usePrefersReducedMotion } from '../hooks'
import { backdropUrl } from '../lib/images'
import { formatRating } from '../lib/format'
import { buttonClass } from '../lib/buttonStyles'
import { Icon, Star } from './Icon'
import { TrailerModal } from './TrailerModal'

/**
 * The large rotating banner at the top of the home page (pages/HomePage): a full-width backdrop of one of today's trending
 * titles with its name, score, short description and two actions, changing to the next title every few seconds with a
 * cross-fade. The titles come from {@code HomeResponse.heroes} (backend CatalogService, up to six).
 *
 * It rotates by itself but never against the viewer: it stops while the pointer or keyboard focus is inside, while a trailer
 * is open, after the pause button is pressed, and always for people who asked their system for reduced motion. Arrows and
 * dots choose a title directly. "More info" opens the detail page; "Watch trailer" fetches the title's videos on demand
 * (rows do not carry them) and plays the best one in a dialog, or says that none exists.
 */
const ROTATE_MS = 7000

export function Hero({ titles }: { titles: TitleSummary[] }) {
  const fetchTitle = useFetchTitle()
  const reducedMotion = usePrefersReducedMotion()
  const [index, setIndex] = useState(0)
  const [hovering, setHovering] = useState(false)
  const [userPaused, setUserPaused] = useState(false)
  const [trailerKey, setTrailerKey] = useState<string | null>(null)
  const [state, setState] = useState<'idle' | 'loading' | 'none' | 'error'>('idle')
  const count = titles.length
  const title = titles[Math.min(index, count - 1)]
  const rotating = count > 1 && !reducedMotion && !userPaused && !hovering && trailerKey === null

  // `index` is a dependency so that choosing a title by hand restarts the countdown for that title.
  useEffect(() => {
    if (!rotating) return
    const timer = window.setTimeout(() => {
      setIndex((index + 1) % count)
      setState('idle')
    }, ROTATE_MS)
    return () => window.clearTimeout(timer)
  }, [rotating, index, count])

  function go(next: number) {
    setIndex(((next % count) + count) % count)
    setState('idle')
  }

  async function watchTrailer() {
    setState('loading')
    try {
      const detail = await fetchTitle(title.type, title.id)
      const video = detail.videos[0]
      if (video) {
        setTrailerKey(video.key)
        setState('idle')
      } else {
        setState('none')
      }
    } catch {
      setState('error')
    }
  }

  const name = title.title ?? 'Untitled'

  return (
    <section
      aria-label="Featured title"
      aria-roledescription="carousel"
      onMouseEnter={() => setHovering(true)}
      onMouseLeave={() => setHovering(false)}
      onFocus={() => setHovering(true)}
      onBlur={() => setHovering(false)}
      className="relative -mt-16 min-h-[78vh] overflow-hidden bg-surface-2 sm:min-h-[85vh]"
    >
      {titles.map((t, i) => {
        const url = backdropUrl(t.backdropPath)
        return url ? (
          <img
            key={`${t.type}-${t.id}`}
            src={url}
            alt=""
            fetchPriority={i === 0 ? 'high' : 'auto'}
            className={`absolute inset-0 size-full object-cover transition-opacity duration-1000 ${i === index ? 'kenburns opacity-100' : 'opacity-0'}`}
          />
        ) : null
      })}
      <div className="absolute inset-0 bg-linear-to-t from-bg via-bg/30 to-black/20" />
      <div className="absolute inset-0 bg-linear-to-r from-bg/85 via-bg/20 to-transparent" />

      <div className="relative mx-auto flex min-h-[78vh] max-w-7xl items-end px-4 pb-20 pt-32 sm:min-h-[85vh] sm:px-8 sm:pb-24">
        <div key={`${title.type}-${title.id}`} className="fade-up max-w-2xl" aria-live={rotating ? 'off' : 'polite'}>
          <span className="inline-flex items-center gap-2 rounded-full bg-ember-500/20 px-3 py-1 text-xs font-bold uppercase tracking-widest text-ember-300">
            <span className="size-1.5 rounded-full bg-ember-400" /> Trending today
          </span>
          <h1 className="mt-4 text-4xl font-extrabold leading-[1.05] tracking-tight text-ink drop-shadow-lg sm:text-6xl">{name}</h1>
          <p className="mt-4 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm font-medium text-muted">
            {title.rating > 0 && (
              <span className="inline-flex items-center gap-1 font-bold text-ink">
                <Star className="size-4 text-gold" /> {formatRating(title.rating)}
              </span>
            )}
            {title.year && <span>{title.year}</span>}
            <span className="rounded border border-line px-1.5 py-0.5 text-xs uppercase">{title.type === 'tv' ? 'TV show' : 'Movie'}</span>
          </p>
          {title.overview && <p className="clamp-3 mt-4 text-base leading-relaxed text-ink/85 sm:text-lg">{title.overview}</p>}

          <div className="mt-7 flex flex-wrap items-center gap-3">
            <button type="button" onClick={watchTrailer} disabled={state === 'loading'} className={buttonClass('primary', 'px-6 py-3 disabled:opacity-70')}>
              <Icon name="play" className="size-5" /> {state === 'loading' ? 'Loading…' : 'Watch trailer'}
            </button>
            <Link to={`/title/${title.type}/${title.id}`} className={buttonClass('glass', 'px-6 py-3')}>
              <Icon name="info" className="size-5" /> More info
            </Link>
          </div>
          {state === 'none' && <p role="status" className="mt-3 text-sm text-muted">No trailer is available for this title yet.</p>}
          {state === 'error' && <p role="alert" className="mt-3 text-sm text-ember-300">Couldn’t load the trailer. Please try again.</p>}
        </div>
      </div>

      {count > 1 && (
        <div className="absolute inset-x-0 bottom-4 z-10 mx-auto flex max-w-7xl items-center justify-between gap-3 px-4 sm:bottom-6 sm:px-8">
          <div role="group" aria-label="Choose a featured title" className="flex items-center gap-2">
            {titles.map((t, i) => (
              <button
                key={`${t.type}-${t.id}`}
                type="button"
                onClick={() => go(i)}
                aria-label={`Show ${t.title ?? 'title'} (${i + 1} of ${count})`}
                aria-current={i === index}
                className="group grid h-6 place-items-center px-0.5"
              >
                <span className={`block h-1.5 rounded-full transition-all duration-300 ${i === index ? 'w-8 bg-ember-400' : 'w-4 bg-white/35 group-hover:bg-white/60'}`} />
              </button>
            ))}
          </div>
          <div className="flex items-center gap-1">
            <button type="button" onClick={() => setUserPaused((p) => !p)} aria-pressed={userPaused} aria-label={userPaused ? 'Play slideshow' : 'Pause slideshow'} className={arrow}>
              <Icon name={userPaused || reducedMotion ? 'play' : 'pause'} className="size-4" />
            </button>
            <button type="button" onClick={() => go(index - 1)} aria-label="Previous featured title" className={arrow}>
              <Icon name="chevronLeft" className="size-5" />
            </button>
            <button type="button" onClick={() => go(index + 1)} aria-label="Next featured title" className={arrow}>
              <Icon name="chevronRight" className="size-5" />
            </button>
          </div>
        </div>
      )}

      <TrailerModal videoKey={trailerKey} title={`${name} — trailer`} onClose={() => setTrailerKey(null)} />
    </section>
  )
}

const arrow = 'grid size-10 place-items-center rounded-full border border-white/25 bg-black/35 text-white backdrop-blur transition-colors hover:bg-black/60'
