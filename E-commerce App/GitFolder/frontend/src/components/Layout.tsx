import { useEffect } from 'react'
import { Link, Outlet, useLocation } from 'react-router-dom'
import { Header } from './Header'

function Footer() {
  const columns = [
    { title: 'Shop', links: [['Electronics', '/c/electronics'], ["Men's Fashion", '/c/men'], ["Women's Fashion", '/c/women'], ['Grocery', '/c/grocery']] },
    { title: 'Your account', links: [['Orders', '/orders'], ['Wishlist', '/wishlist'], ['Cart', '/cart'], ['Profile', '/account']] },
    { title: 'Offers', links: [['Deals of the Day', '/search?sort=discount&minDiscount=15'], ['Top rated', '/search?minRating=4&sort=rating'], ['New arrivals', '/search?sort=newest']] },
  ]
  return (
    <footer className="mt-12 bg-slate-900 text-slate-300">
      <div className="mx-auto grid max-w-7xl gap-8 px-4 py-10 sm:grid-cols-2 lg:grid-cols-4">
        <div>
          <p className="text-lg font-bold text-white">
            Cart<span className="text-accent-400">Nest</span>
          </p>
          <p className="mt-2 text-sm text-slate-400">
            Electronics, fashion, home, grocery and more. Free delivery on orders of ₹499 or more.
          </p>
        </div>
        {columns.map((col) => (
          <div key={col.title}>
            <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">{col.title}</p>
            <ul className="mt-3 space-y-2 text-sm">
              {col.links.map(([label, to]) => (
                <li key={to}>
                  <Link to={to} className="hover:text-white">
                    {label}
                  </Link>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
      <p className="border-t border-slate-800 py-4 text-center text-xs text-slate-500">
        A demo store: payments are simulated and no orders are delivered. Product data and images from DummyJSON.
      </p>
    </footer>
  )
}

export function Layout() {
  const { pathname } = useLocation()
  useEffect(() => {
    window.scrollTo(0, 0)
  }, [pathname])
  return (
    <div className="flex min-h-screen flex-col">
      <Header />
      <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-5">
        <Outlet />
      </main>
      <Footer />
    </div>
  )
}
