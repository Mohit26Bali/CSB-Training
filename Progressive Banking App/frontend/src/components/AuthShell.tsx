import type { ReactNode } from 'react'
import { Brand } from './Brand'
import { Icon } from './Icon'
import { ThemeToggle } from './ThemeToggle'

/**
 * Two-column frame of the login and register pages: a photo showcase on the left (large screens only) and the form
 * on the right. Images live in public/images (fetched with tools/fetch_images.py). The slides crossfade through
 * the .hero-slide animation in index.css; with reduced motion enabled only the first slide is shown.
 */
const slides = [
  { src: '/images/hero-skyline.jpg', alt: 'City skyline at sunset' },
  { src: '/images/hero-planning.jpg', alt: 'A couple planning their finances together' },
  { src: '/images/hero-mobile.jpg', alt: 'A woman using a banking app on her phone' },
  { src: '/images/hero-family.jpg', alt: 'A family settling into their new home' },
]

const perks = [
  { icon: 'shield', text: 'Secure, token-based sessions that expire automatically' },
  { icon: 'send', text: 'Instant transfers between accounts, 24x7' },
  { icon: 'receipt', text: 'A clear, auditable record of every transaction' },
] as const

export function AuthShell({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <div className="grid min-h-screen lg:grid-cols-[1.05fr_1fr]">
      <aside className="relative hidden overflow-hidden bg-brand-950 lg:block">
        {slides.map((slide, i) => (
          <img
            key={slide.src}
            src={slide.src}
            alt={slide.alt}
            loading={i === 0 ? 'eager' : 'lazy'}
            className="hero-slide absolute inset-0 size-full object-cover"
            style={{ animationDelay: `${i * 7}s` }}
          />
        ))}
        <div className="absolute inset-0 bg-linear-to-t from-brand-950 via-brand-950/55 to-brand-900/35" />
        <div className="absolute inset-0 bg-linear-to-r from-brand-950/60 to-transparent" />

        <div className="relative flex h-full flex-col justify-between p-10 xl:p-14">
          <Brand light />

          <div className="max-w-lg">
            <h2 className="fade-up text-4xl font-semibold leading-tight tracking-tight text-white xl:text-[2.6rem]">
              Banking that moves <span className="text-mint-400">as fast as you do.</span>
            </h2>
            <ul className="mt-7 space-y-3.5">
              {perks.map((perk, i) => (
                <li key={perk.text} className="fade-up flex items-center gap-3 text-white/85" style={{ animationDelay: `${0.15 + i * 0.1}s` }}>
                  <span className="grid size-8 shrink-0 place-items-center rounded-lg bg-white/12 backdrop-blur">
                    <Icon name={perk.icon} className="size-4 text-mint-400" />
                  </span>
                  {perk.text}
                </li>
              ))}
            </ul>

            <figure className="fade-up mt-10 flex items-center gap-4 rounded-2xl border border-white/15 bg-white/10 p-4 backdrop-blur-md" style={{ animationDelay: '0.5s' }}>
              <img src="/images/portrait-woman.jpg" alt="" className="size-12 rounded-full object-cover" />
              <div>
                <blockquote className="text-sm leading-snug text-white/90">“Opening an account took two minutes, and transfers land instantly.”</blockquote>
                <figcaption className="mt-1 text-xs text-white/60">Priya S. · Savings customer</figcaption>
              </div>
            </figure>
          </div>
        </div>
      </aside>

      <main className="relative flex flex-col px-5 py-6 sm:px-10">
        <div className="flex items-center justify-between">
          <div className="lg:invisible">
            <Brand />
          </div>
          <ThemeToggle />
        </div>

        <div className="mx-auto flex w-full max-w-md flex-1 flex-col justify-center py-8">
          <div className="fade-up">
            <h1 className="text-3xl font-semibold tracking-tight text-ink">{title}</h1>
            <p className="mt-2 text-muted">{subtitle}</p>
          </div>
          <div className="fade-up" style={{ animationDelay: '0.08s' }}>
            {children}
          </div>
        </div>

        <p className="flex items-center justify-center gap-1.5 text-xs text-faint">
          <Icon name="lock" className="size-3.5" /> Training project · not a real bank
        </p>
      </main>
    </div>
  )
}
