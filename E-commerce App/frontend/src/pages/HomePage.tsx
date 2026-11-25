import { useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getHome } from '../api/endpoints'
import type { HomeSection } from '../api/types'
import { ProductRail } from '../components/ProductCard'
import { ErrorState, PageSpinner } from '../components/ui'
import { errorMessage } from '../hooks'

const SLIDES = [
  { key: 'electronics', title: 'The latest phones & laptops', text: 'Apple, Samsung, Oppo and more, with up to 3 storage options', colour: 'from-brand-800 to-brand-500' },
  { key: 'women', title: "Women's fashion week", text: 'Dresses, handbags, jewellery and footwear in every size', colour: 'from-rose-700 to-pink-500' },
  { key: 'kitchen-dining', title: 'Cook like a pro', text: 'Cookware, appliances and tools for every kitchen', colour: 'from-amber-700 to-orange-500' },
  { key: 'grocery', title: 'Fresh groceries, delivered', text: 'Fruits, vegetables, dairy and pantry staples', colour: 'from-emerald-800 to-green-500' },
]

/** A rotating banner per department, showing three of its best sellers. */
function HeroCarousel({ sections }: { sections: HomeSection[] }) {
  const [index, setIndex] = useState(0)
  useEffect(() => {
    const timer = setInterval(() => setIndex((i) => (i + 1) % SLIDES.length), 5000)
    return () => clearInterval(timer)
  }, [])
  const slide = SLIDES[index]
  const products = sections.find((s) => s.key === slide.key)?.products.slice(0, 3) ?? []

  return (
    <section aria-roledescription="carousel" aria-label="Featured departments"
      className={`relative overflow-hidden rounded-lg bg-gradient-to-r ${slide.colour} text-white shadow-sm`}>
      <div className="flex min-h-56 flex-col items-start justify-between gap-6 p-6 sm:flex-row sm:items-center sm:p-10">
        <div className="max-w-md">
          <p className="text-xs font-semibold uppercase tracking-widest text-white/70">CartNest specials</p>
          <h1 className="mt-2 text-2xl font-bold sm:text-4xl">{slide.title}</h1>
          <p className="mt-2 text-sm text-white/85 sm:text-base">{slide.text}</p>
          <Link to={`/c/${slide.key}`} className="mt-5 inline-block rounded-md bg-accent-400 px-5 py-2.5 text-sm font-semibold text-slate-900 hover:bg-accent-300">
            Shop now
          </Link>
        </div>
        <div className="hidden gap-3 sm:flex">
          {products.map((p) => (
            <Link key={p.id} to={`/p/${p.slug}`} className="flex h-36 w-32 items-center justify-center rounded-lg bg-white/95 p-2 shadow-lg transition hover:-translate-y-1">
              <img src={p.thumbnailUrl} alt={p.title} className="max-h-full object-contain" />
            </Link>
          ))}
        </div>
      </div>
      <div className="absolute bottom-3 left-1/2 flex -translate-x-1/2 gap-2">
        {SLIDES.map((s, i) => (
          <button key={s.key} onClick={() => setIndex(i)} aria-label={`Show ${s.title}`} aria-current={i === index}
            className={`h-2 rounded-full transition-all ${i === index ? 'w-6 bg-white' : 'w-2 bg-white/50'}`} />
        ))}
      </div>
    </section>
  )
}

const OFFERS = [
  ['WELCOME10', '10% off your first order (up to ₹500)'],
  ['FLAT200', '₹200 off on ₹1,499 and above'],
  ['BIGSAVE1000', '₹1,000 off on ₹9,999 and above'],
]

export function HomePage() {
  const home = useQuery({ queryKey: ['home'], queryFn: getHome, staleTime: 60_000 })
  if (home.isPending) return <PageSpinner />
  if (home.isError) return <ErrorState message={errorMessage(home.error)} onRetry={() => home.refetch()} />
  const { categories, sections } = home.data

  return (
    <div className="space-y-5">
      <nav aria-label="Shop by department" className="no-scrollbar flex gap-2 overflow-x-auto rounded-lg bg-white p-3 shadow-sm lg:justify-between">
        {categories.map((c) => (
          <Link key={c.slug} to={`/c/${c.slug}`} className="flex w-24 shrink-0 flex-col items-center gap-1.5 rounded-md p-1 text-center hover:bg-brand-50">
            <img src={c.imageUrl ?? ''} alt="" className="h-14 w-14 rounded-full bg-slate-50 object-contain p-1" loading="lazy" />
            <span className="text-xs font-medium leading-tight text-slate-700">{c.name}</span>
          </Link>
        ))}
      </nav>

      <HeroCarousel sections={sections} />

      <div className="grid gap-3 sm:grid-cols-3">
        {OFFERS.map(([code, text]) => (
          <div key={code} className="flex items-center gap-3 rounded-lg border border-dashed border-brand-300 bg-brand-50 px-4 py-3">
            <span className="rounded bg-brand-700 px-2 py-1 font-mono text-xs font-bold text-white">{code}</span>
            <span className="text-sm text-brand-900">{text}</span>
          </div>
        ))}
      </div>

      {sections.map((s) => (
        <ProductRail key={s.key} title={s.title} products={s.products}
          viewAll={s.viewAll.startsWith('category=') ? `/c/${s.viewAll.slice('category='.length)}` : `/search?${s.viewAll}`} />
      ))}
    </div>
  )
}
