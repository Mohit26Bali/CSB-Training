import { Link } from 'react-router-dom'
import type { ProductCard as Product } from '../api/types'
import { useToggleWishlist } from '../hooks'
import { PriceLine, RatingBadge } from './ui'

export function HeartButton({ productId, className = '' }: { productId: number; className?: string }) {
  const { isSaved, toggle } = useToggleWishlist()
  const saved = isSaved(productId)
  return (
    <button
      type="button"
      onClick={(e) => {
        e.preventDefault()
        toggle(productId)
      }}
      aria-pressed={saved}
      aria-label={saved ? 'Remove from wishlist' : 'Save to wishlist'}
      className={`flex h-9 w-9 items-center justify-center rounded-full bg-white/90 shadow ring-1 ring-slate-200 hover:scale-105 ${className}`}
    >
      <svg viewBox="0 0 24 24" className={`h-5 w-5 ${saved ? 'fill-red-500 text-red-500' : 'fill-none text-slate-500'}`} stroke="currentColor" strokeWidth="1.8" aria-hidden>
        <path d="M12 20s-7-4.4-9.2-8.6C1.2 8.3 3 4.8 6.5 4.8c2 0 3.5 1.1 4.5 2.6 1-1.5 2.5-2.6 4.5-2.6 3.5 0 5.3 3.5 3.7 6.6C19 15.6 12 20 12 20Z" strokeLinejoin="round" />
      </svg>
    </button>
  )
}

export function ProductCard({ product, compact = false }: { product: Product; compact?: boolean }) {
  return (
    <Link
      to={`/p/${product.slug}`}
      className="group relative flex h-full flex-col rounded-lg bg-white p-3 shadow-sm ring-1 ring-slate-100 transition hover:shadow-md"
    >
      <div className={`relative flex items-center justify-center overflow-hidden rounded-md bg-slate-50 ${compact ? 'h-36' : 'h-48'}`}>
        <img
          src={product.thumbnailUrl}
          alt={product.title}
          loading="lazy"
          className={`h-full w-full object-contain p-2 transition group-hover:scale-105 ${product.inStock ? '' : 'opacity-50 grayscale'}`}
        />
        {!product.inStock && (
          <span className="absolute bottom-2 left-2 rounded bg-slate-800/80 px-2 py-0.5 text-xs font-medium text-white">Out of stock</span>
        )}
        <HeartButton productId={product.id} className="absolute right-2 top-2" />
      </div>
      <div className="mt-3 flex flex-1 flex-col gap-1">
        {product.brand && <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">{product.brand}</p>}
        <p className="line-clamp-2 text-sm text-slate-800 group-hover:text-brand-700">{product.title}</p>
        <RatingBadge rating={product.rating} count={product.ratingCount} />
        <div className="mt-auto pt-1">
          <PriceLine price={product.price} mrp={product.mrp} discount={product.discountPercent} />
        </div>
      </div>
    </Link>
  )
}

export function ProductRail({ title, products, viewAll }: { title: string; products: Product[]; viewAll?: string }) {
  if (products.length === 0) return null
  return (
    <section className="rounded-lg bg-white p-4 shadow-sm">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-lg font-semibold text-slate-900">{title}</h2>
        {viewAll && (
          <Link to={viewAll} className="rounded-full bg-brand-600 px-4 py-1.5 text-xs font-semibold text-white hover:bg-brand-700">
            View all
          </Link>
        )}
      </div>
      <div className="no-scrollbar -mx-1 flex snap-x gap-3 overflow-x-auto px-1 pb-1">
        {products.map((p) => (
          <div key={p.id} className="w-44 shrink-0 snap-start sm:w-48">
            <ProductCard product={p} compact />
          </div>
        ))}
      </div>
    </section>
  )
}
