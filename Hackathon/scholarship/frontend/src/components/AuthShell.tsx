import { useEffect, useState, type ReactNode } from 'react'
import { config } from '../config'
import { look } from '../look'
import { ThemeToggle } from './ThemeToggle'

const slides = ['/hero/login-1.jpg', '/hero/login-2.jpg', '/hero/login-3.jpg']

const wordGradient = 'bg-linear-to-r from-white via-brand-100 to-amber-200 bg-clip-text text-transparent'

/** Index that advances every `ms`, for the animations that swap whole words. */
function useCycle(length: number, ms: number) {
  const [index, setIndex] = useState(0)
  useEffect(() => {
    const timer = window.setInterval(() => setIndex((i) => (i + 1) % length), ms)
    return () => window.clearInterval(timer)
  }, [length, ms])
  return index
}

/** Types a word letter by letter, holds it, deletes it and moves on. */
function TypedWord({ words }: { words: string[] }) {
  const [state, setState] = useState({ word: 0, shown: 0, deleting: false })
  useEffect(() => {
    const { word, shown, deleting } = state
    const target = words[word]
    let delay = 90
    let next = { word, shown: shown + 1, deleting }
    if (!deleting && shown >= target.length) {
      delay = 1500
      next = { word, shown, deleting: true }
    } else if (deleting && shown > 0) {
      delay = 45
      next = { word, shown: shown - 1, deleting }
    } else if (deleting) {
      delay = 250
      next = { word: (word + 1) % words.length, shown: 0, deleting: false }
    }
    const timer = window.setTimeout(() => setState(next), delay)
    return () => window.clearTimeout(timer)
  }, [state, words])
  return (
    <span aria-hidden="true" className="inline-flex items-baseline">
      <span className={wordGradient}>{words[state.word].slice(0, state.shown)}</span>
      <span className="ml-0.5 inline-block h-[0.85em] w-[3px] animate-pulse self-center rounded-sm bg-white" />
    </span>
  )
}

/** The changing word of the headline. look.headline picks the animation: rotate, typewriter, slide or fade. */
function HeadlineWord() {
  const words = config.headline.words
  const index = useCycle(words.length, look.headline === 'fade' ? 3000 : 2400)
  if (look.headline === 'typewriter') return <TypedWord words={words} />
  if (look.headline === 'slide') {
    return (
      <span aria-hidden="true" className="inline-flex overflow-hidden align-bottom leading-[1.25]">
        <span key={index} className={`slide-word inline-block ${wordGradient}`}>
          {words[index]}
        </span>
      </span>
    )
  }
  return (
    <span key={index} aria-hidden="true" className={`${look.headline === 'fade' ? 'fade-word' : 'word-in'} inline-block ${wordGradient}`}>
      {words[index]}
    </span>
  )
}

/**
 * The photo side of the screen. look.heroStyle picks how the photos are shown:
 * slideshow (cross-fading), kenburns (cross-fading with a slow zoom), duotone (one photo tinted in the brand colour)
 * or mosaic (a tilted collage of all three).
 */
function Backdrop() {
  const style = look.heroStyle
  return (
    <>
      <div aria-hidden="true" className="absolute inset-0 -z-20 overflow-hidden">
        {(style === 'slideshow' || style === 'kenburns') &&
          slides.map((src, i) => (
            <img key={src} src={src} alt="" className={`hero-slide absolute inset-0 size-full object-cover ${style === 'slideshow' ? 'fade-only' : ''}`} style={{ animationDelay: `${i * 8}s` }} />
          ))}
        {style === 'duotone' && <img src={slides[0]} alt="" className="absolute inset-0 size-full object-cover grayscale" />}
        {style === 'mosaic' && (
          <div className="absolute -right-16 -top-10 bottom-0 grid w-[78%] rotate-3 grid-cols-2 grid-rows-2 gap-3 opacity-90">
            <img src={slides[0]} alt="" className="mosaic-a row-span-2 size-full rounded-2xl object-cover" />
            <img src={slides[1]} alt="" className="mosaic-b size-full rounded-2xl object-cover" />
            <img src={slides[2]} alt="" className="mosaic-a size-full rounded-2xl object-cover" />
          </div>
        )}
      </div>
      <div
        aria-hidden="true"
        className={`absolute inset-0 -z-10 ${
          style === 'duotone'
            ? 'bg-brand-600/55 mix-blend-multiply'
            : style === 'mosaic'
              ? 'bg-linear-to-r from-brand-800 via-brand-800/85 to-brand-800/20'
              : 'bg-linear-to-t from-black/70 via-black/20 to-black/10'
        }`}
      />
      {style === 'duotone' && <div aria-hidden="true" className="absolute inset-0 -z-10 bg-linear-to-t from-black/65 via-black/10 to-transparent" />}
      <div aria-hidden="true" className="orb orb-a absolute -left-16 top-1/4 -z-10 size-72 rounded-full bg-white/10 blur-3xl" />
      <div aria-hidden="true" className="orb orb-b absolute -right-10 bottom-10 -z-10 size-80 rounded-full bg-amber-300/10 blur-3xl" />
    </>
  )
}

interface Props {
  title: string
  subtitle: string
  children: ReactNode
}

/**
 * Shared frame for the login and register pages: a photo panel beside the form, filling exactly one screen on a desktop
 * (the text and cards shrink away on short windows instead of making the page scroll). The photo panel is always on the left.
 * Text comes from config.ts; the look (photo style, headline animation) from look.ts.
 */
export function AuthShell({ title, subtitle, children }: Props) {
  const { headline } = config
  return (
    <div className="min-h-dvh lg:grid lg:h-dvh lg:grid-cols-[1.15fr_1fr] lg:overflow-hidden">
      <aside className="relative isolate flex flex-col justify-between gap-6 overflow-hidden bg-brand-800 px-6 py-8 text-white lg:px-12 lg:py-10">
        <Backdrop />

        <div className="fade-up flex items-center gap-3">
          <span className="grid size-10 place-items-center rounded-xl bg-white/15 ring-1 ring-white/25 backdrop-blur">
            <svg viewBox="0 0 24 24" className="size-6" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              {config.logo.map((d) => (
                <path key={d} d={d} />
              ))}
            </svg>
          </span>
          <span className="text-lg font-semibold tracking-tight">{config.appName}</span>
        </div>

        <div>
          <h2 aria-label={`${headline.line1} ${headline.lead} ${headline.words.join(', ')}`} className="fade-up text-3xl font-semibold leading-tight tracking-tight sm:text-4xl lg:text-4xl xl:text-5xl" style={{ animationDelay: '0.1s' }}>
            {headline.line1}
            <br />
            {headline.lead} <HeadlineWord />
          </h2>
          <p className="fade-up mt-4 max-w-md text-base text-white/85" style={{ animationDelay: '0.2s' }}>
            {config.intro}
          </p>
          <div className="fade-up mt-6 hidden flex-wrap gap-2 lg:flex [@media(max-height:720px)]:hidden" style={{ animationDelay: '0.3s' }}>
            {config.chips.map((c, i) => (
              <span key={c} className="chip rounded-full bg-white/10 px-3 py-1 text-xs font-medium ring-1 ring-white/20 backdrop-blur" style={{ animationDelay: `${i * 0.35}s` }}>
                {c}
              </span>
            ))}
          </div>
        </div>

        <ol className="hidden gap-4 lg:grid lg:grid-cols-3 [@media(max-height:800px)]:hidden">
          {config.steps.map((step, i) => (
            <li
              key={step.title}
              className="fade-up rounded-2xl bg-white/10 p-4 ring-1 ring-white/15 backdrop-blur transition-transform duration-300 hover:-translate-y-1 hover:bg-white/15"
              style={{ animationDelay: `${0.4 + i * 0.12}s` }}
            >
              <span className="mb-3 grid size-9 place-items-center rounded-lg bg-white/15">
                <svg viewBox="0 0 24 24" className="size-5" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <path d={step.icon} />
                </svg>
              </span>
              <p className="text-sm font-semibold">
                <span className="mr-1.5 text-brand-100">{i + 1}.</span>
                {step.title}
              </p>
              <p className="mt-1 text-xs leading-relaxed text-white/80">{step.text}</p>
            </li>
          ))}
        </ol>
      </aside>

      <main className="relative flex items-center justify-center px-4 py-10 sm:px-8 lg:overflow-y-auto lg:py-6">
        <div className="absolute right-4 top-4">
          <ThemeToggle />
        </div>
        <div className="slide-in w-full max-w-sm">
          <h1 className="text-2xl font-semibold tracking-tight text-slate-900">{title}</h1>
          <p className="mt-1 text-sm text-slate-500">{subtitle}</p>
          {children}
        </div>
      </main>
    </div>
  )
}
