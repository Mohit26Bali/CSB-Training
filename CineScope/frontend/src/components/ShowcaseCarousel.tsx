import { useEffect, useState } from 'react'
import { useShowcase } from '../api/queries'
import { backdropUrl } from '../lib/images'
import { usePrefersReducedMotion } from '../hooks'

/**
 * The picture side of the sign-in and sign-up screens (components/AuthScreen): famous films, each with a famous line, one
 * after the other. The slides come from GET /api/showcase (backend ShowcaseService, public because these screens are what a
 * visitor sees first); the backdrop and poster are TMDB images, the line and who says it are chosen by the backend.
 *
 * On a wide screen it fills the left column with the quote in large type; on a phone it becomes a dimmed full-screen
 * background behind the form (no quote, to keep the form readable). It starts at a random film, changes every few seconds
 * with a cross-fade, and stays still for people who asked their system for reduced motion. It is decoration, so it is
 * hidden from screen readers. While the slides load, or if there are none, a plain gradient with the tagline is shown, so the
 * form is never held up by the pictures.
 */
const SLIDE_MS = 6500

export function ShowcaseCarousel() {
  const showcase = useShowcase()
  const reducedMotion = usePrefersReducedMotion()
  const slides = showcase.data ?? []
  const count = slides.length
  const [start] = useState(() => Math.random()) // which film comes first changes from visit to visit
  const [step, setStep] = useState(0)

  useEffect(() => {
    if (count < 2 || reducedMotion) return
    const timer = window.setInterval(() => setStep((s) => s + 1), SLIDE_MS)
    return () => window.clearInterval(timer)
  }, [count, reducedMotion])

  const current = count > 0 ? (Math.floor(start * count) + step) % count : 0
  // Only the neighbours are mounted: the previous one fades out, the next one is already loaded for its turn.
  const shown = count > 0 ? [-1, 0, 1].map((offset) => (current + offset + count) % count) : []
  const slide = count > 0 ? slides[current] : null

  return (
    <aside
      aria-hidden="true"
      data-testid="showcase"
      className="fixed inset-0 overflow-hidden bg-linear-to-br from-slate-900 via-slate-950 to-black lg:sticky lg:inset-auto lg:top-0 lg:h-screen"
    >
      {shown.map((index) => {
        const s = slides[index]
        const url = backdropUrl(s.backdropPath, 'w1280')
        return url ? (
          <img
            key={s.quoteId}
            src={url}
            alt=""
            className={`absolute inset-0 size-full object-cover transition-opacity duration-[1400ms] ${index === current ? 'opacity-100' : 'opacity-0'}`}
          />
        ) : null
      })}

      <div className="absolute inset-0 bg-bg/80 lg:bg-transparent" />
      <div className="absolute inset-0 hidden bg-linear-to-t from-black/90 via-black/25 to-black/50 lg:block" />
      <div className="absolute inset-0 hidden bg-linear-to-r from-black/50 to-transparent lg:block" />

      <div className="absolute inset-x-0 bottom-0 hidden p-10 xl:p-14 lg:block">
        {slide ? (
          <figure key={slide.quoteId} className="fade-up max-w-2xl">
            <blockquote data-testid="showcase-quote" className="text-3xl font-extrabold leading-tight tracking-tight text-white drop-shadow-lg xl:text-5xl">
              “{slide.quote}”
            </blockquote>
            <figcaption className="mt-4 text-base font-medium text-white/80">
              <span data-testid="showcase-speaker" className="font-bold text-white">
                {slide.speaker}
              </span>{' '}
              · <span data-testid="showcase-film">{slide.title}</span>
              {slide.year ? ` (${slide.year})` : ''}
            </figcaption>
          </figure>
        ) : (
          <p className="max-w-lg text-3xl font-extrabold leading-tight tracking-tight text-white xl:text-5xl">Your next favourite film is waiting.</p>
        )}
        <p className="mt-8 flex items-center gap-3 text-xs text-white/60">
          <img src="/tmdb-logo.svg" alt="" className="h-4 w-auto opacity-80" />
          Film data and images from TMDB. Not endorsed or certified by TMDB.
        </p>
      </div>
    </aside>
  )
}
