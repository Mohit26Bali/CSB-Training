import { useEffect, useState, type FormEvent } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { Brand } from './Brand'
import { Icon } from './Icon'
import { ThemeToggle } from './ThemeToggle'
import { UserMenu } from './UserMenu'

/**
 * The frame around every page: a sticky header (logo, navigation, search box, theme toggle) and a footer with the
 * attribution TMDB requires. The header's account corner is components/UserMenu. Rendered by App.tsx as the parent route; each page appears in {@code <Outlet />}.
 *
 * The header is transparent over the home page's hero picture and turns into a solid blurred bar once the page
 * scrolls. Submitting the search box goes to /search?q=..., where pages/SearchPage does the work.
 */
interface NavItem {
  to: string
  label: string
  end?: boolean
  match?: (path: string, search: string) => boolean
}

const links: NavItem[] = [
  { to: '/', label: 'Home', end: true },
  { to: '/browse?type=movie', label: 'Movies', match: (path: string, search: string) => path === '/browse' && !search.includes('type=tv') },
  { to: '/browse?type=tv', label: 'TV shows', match: (path: string, search: string) => path === '/browse' && search.includes('type=tv') },
  { to: '/tonight', label: 'Tonight' },
  { to: '/play', label: 'Play' },
]

export function AppShell() {
  const location = useLocation()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const [scrolled, setScrolled] = useState(false)
  const [query, setQuery] = useState(params.get('q') ?? '')
  const [searchOpen, setSearchOpen] = useState(false)
  const onHome = location.pathname === '/'
  const { session } = useAuth()
  // administrators get one more section in the header
  const navLinks = session?.role === 'ADMIN' ? [...links, { to: '/admin/users', label: 'Admin' }] : links

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 24)
    onScroll()
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [])

  // Scroll to the top whenever the page changes (a new title opens at its top, not mid-way).
  useEffect(() => {
    window.scrollTo({ top: 0 })
  }, [location.pathname])

  function submit(e: FormEvent) {
    e.preventDefault()
    const text = query.trim()
    if (text) {
      navigate(`/search?q=${encodeURIComponent(text)}`)
      setSearchOpen(false)
    }
  }

  const solid = scrolled || !onHome

  return (
    <div className="flex min-h-screen flex-col">
      <header
        className={`sticky top-0 z-50 h-16 transition-colors duration-300 ${
          solid ? 'border-b border-line bg-bg/85 backdrop-blur-xl' : 'border-b border-transparent bg-linear-to-b from-black/60 to-transparent'
        }`}
      >
        <div className="mx-auto flex h-full max-w-7xl items-center gap-4 px-4 sm:gap-8 sm:px-8">
          <Link to="/" aria-label="CineScope home" className="shrink-0">
            <Brand />
          </Link>

          <nav aria-label="Main" className="hidden items-center gap-1 md:flex">
            {navLinks.map((link) => (
              <NavLink
                key={link.to}
                to={link.to}
                end={link.end}
                className={({ isActive }) =>
                  `rounded-full px-4 py-2 text-sm font-semibold transition-colors ${
                    (link.match ? link.match(location.pathname, location.search) : isActive) ? 'bg-ink/10 text-ink' : 'text-muted hover:text-ink'
                  }`
                }
              >
                {link.label}
              </NavLink>
            ))}
          </nav>

          <div className="ml-auto flex items-center gap-1 sm:gap-2">
            <form onSubmit={submit} role="search" className={`${searchOpen ? 'flex' : 'hidden'} items-center md:flex`}>
              <label className="sr-only" htmlFor="site-search">
                Search movies, shows and people
              </label>
              <div className="relative">
                <Icon name="search" className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-faint" />
                <input
                  id="site-search"
                  type="search"
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  placeholder="Search titles, people"
                  autoComplete="off"
                  className="h-10 w-48 rounded-full border border-line bg-surface/80 pl-10 pr-4 text-sm text-ink placeholder:text-faint backdrop-blur transition-all focus:w-64 focus:border-ember-400 focus:outline-none sm:w-64 sm:focus:w-80"
                />
              </div>
            </form>
            <button
              type="button"
              onClick={() => setSearchOpen((o) => !o)}
              aria-label="Toggle search"
              className="grid size-10 place-items-center rounded-full text-muted hover:bg-ink/10 hover:text-ink md:hidden"
            >
              <Icon name={searchOpen ? 'close' : 'search'} className="size-5" />
            </button>
            <ThemeToggle />
            <UserMenu />
          </div>
        </div>

        {/* On phones the three main sections live in a slim second row, because there is no room for them above. */}
        <nav aria-label="Sections" className="flex items-center gap-1 border-b border-line bg-bg/90 px-3 py-1.5 backdrop-blur-xl md:hidden">
          {navLinks.map((link) => (
            <NavLink
              key={link.to}
              to={link.to}
              end={link.end}
              className={({ isActive }) =>
                `rounded-full px-3.5 py-1.5 text-sm font-semibold ${(link.match ? link.match(location.pathname, location.search) : isActive) ? 'bg-ink/10 text-ink' : 'text-muted'}`
              }
            >
              {link.label}
            </NavLink>
          ))}
        </nav>
      </header>

      <main className="flex-1">
        <Outlet />
      </main>

      <footer className="mt-16 border-t border-line bg-surface-2">
        <div className="mx-auto flex max-w-7xl flex-col gap-6 px-4 py-10 sm:flex-row sm:items-center sm:justify-between sm:px-8">
          <div className="flex items-center gap-4">
            <a href="https://www.themoviedb.org" target="_blank" rel="noreferrer" aria-label="The Movie Database (TMDB)">
              <img src="/tmdb-logo.svg" alt="TMDB" className="h-9 w-auto" />
            </a>
            <p className="max-w-md text-xs leading-relaxed text-faint">
              This product uses the TMDB API but is not endorsed or certified by TMDB. Movie and TV data and images are provided by TMDB.
            </p>
          </div>
          <p className="text-xs text-faint">CineScope · a training project</p>
        </div>
      </footer>
    </div>
  )
}
