import { useQuery } from '@tanstack/react-query'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { getSuggestions } from '../api/endpoints'
import { formatPrice } from '../lib/format'

/** The header search: suggestions (products, categories, brands) appear while typing; Enter opens the results. */
export function SearchBox() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const [text, setText] = useState(params.get('q') ?? '')
  const [debounced, setDebounced] = useState(text)
  const [open, setOpen] = useState(false)
  const boxRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(text.trim()), 200)
    return () => clearTimeout(timer)
  }, [text])

  useEffect(() => {
    const close = (event: MouseEvent) => {
      if (!boxRef.current?.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', close)
    return () => document.removeEventListener('mousedown', close)
  }, [])

  const suggestions = useQuery({
    queryKey: ['suggest', debounced],
    queryFn: () => getSuggestions(debounced),
    enabled: debounced.length >= 2,
    staleTime: 60_000,
  })

  const submit = (event: FormEvent) => {
    event.preventDefault()
    const q = text.trim()
    if (!q) return
    setOpen(false)
    navigate(`/search?q=${encodeURIComponent(q)}`)
  }

  const data = suggestions.data
  const hasSuggestions = data && (data.products.length || data.categories.length || data.brands.length)

  return (
    <div ref={boxRef} className="relative w-full">
      <form onSubmit={submit} role="search">
        <label htmlFor="site-search" className="sr-only">
          Search for products, brands and more
        </label>
        <input
          id="site-search"
          type="search"
          value={text}
          autoComplete="off"
          placeholder="Search for products, brands and more"
          onChange={(e) => {
            setText(e.target.value)
            setOpen(true)
          }}
          onFocus={() => setOpen(true)}
          onKeyDown={(e) => e.key === 'Escape' && setOpen(false)}
          className="w-full rounded-md border-0 bg-white py-2.5 pl-4 pr-12 text-sm text-slate-900 shadow-sm outline-none ring-1 ring-white/20 placeholder:text-slate-400 focus:ring-2 focus:ring-accent-400"
        />
        <button type="submit" aria-label="Search" className="absolute inset-y-0 right-0 flex w-11 items-center justify-center text-brand-700">
          <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="2.2" aria-hidden>
            <circle cx="11" cy="11" r="7" />
            <path d="m20 20-3.5-3.5" strokeLinecap="round" />
          </svg>
        </button>
      </form>

      {open && debounced.length >= 2 && hasSuggestions ? (
        <div className="absolute left-0 right-0 top-full z-40 mt-1 overflow-hidden rounded-md bg-white text-slate-800 shadow-xl ring-1 ring-slate-200">
          {data.products.map((p) => (
            <Link
              key={p.id}
              to={`/p/${p.slug}`}
              onClick={() => setOpen(false)}
              className="flex items-center gap-3 px-4 py-2 hover:bg-slate-50"
            >
              <img src={p.thumbnailUrl} alt="" className="h-10 w-10 rounded object-contain" loading="lazy" />
              <span className="flex-1 truncate text-sm">{p.title}</span>
              <span className="text-sm font-semibold">{formatPrice(p.price)}</span>
            </Link>
          ))}
          {(data.categories.length > 0 || data.brands.length > 0) && (
            <div className="flex flex-wrap gap-2 border-t border-slate-100 px-4 py-3">
              {data.categories.map((c) => (
                <Link key={c.slug} to={`/c/${c.slug}`} onClick={() => setOpen(false)}
                  className="rounded-full bg-brand-50 px-3 py-1 text-xs font-medium text-brand-700 hover:bg-brand-100">
                  in {c.name}
                </Link>
              ))}
              {data.brands.map((b) => (
                <Link key={b.slug} to={`/search?q=${encodeURIComponent(text.trim())}&brand=${b.slug}`} onClick={() => setOpen(false)}
                  className="rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-700 hover:bg-slate-200">
                  {b.name}
                </Link>
              ))}
            </div>
          )}
        </div>
      ) : null}
    </div>
  )
}
