import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { getMyReview, getProduct, getReviews, writeReview } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'
import { useToast } from '../components/Toast'
import type { ProductDetail, RatingSummary, Review, Variant } from '../api/types'
import { HeartButton, ProductRail } from '../components/ProductCard'
import { Button, Card, ErrorState, PageSpinner, PriceLine, RatingBadge } from '../components/ui'
import { errorMessage, useAddToCart } from '../hooks'
import { formatDate } from '../lib/format'

const OPTION_NAME: Record<string, string> = { SIZE: 'Size', STORAGE: 'Storage', CONFIGURATION: 'Configuration' }

function Gallery({ images, title }: { images: string[]; title: string }) {
  const [selected, setSelected] = useState(0)
  return (
    <div className="flex flex-col-reverse gap-3 sm:flex-row">
      {images.length > 1 && (
        <div className="no-scrollbar flex gap-2 overflow-x-auto sm:flex-col">
          {images.map((src, i) => (
            <button key={src} onClick={() => setSelected(i)} onMouseEnter={() => setSelected(i)} aria-label={`Image ${i + 1}`}
              aria-current={i === selected}
              className={`h-16 w-16 shrink-0 rounded-md border-2 bg-white p-1 ${i === selected ? 'border-brand-600' : 'border-slate-200'}`}>
              <img src={src} alt="" className="h-full w-full object-contain" />
            </button>
          ))}
        </div>
      )}
      <div className="flex aspect-square flex-1 items-center justify-center rounded-lg bg-slate-50 p-6">
        <img src={images[selected]} alt={title} className="max-h-full max-w-full object-contain" />
      </div>
    </div>
  )
}

function StockNote({ variant }: { variant: Variant }) {
  if (variant.stock === 0) return <p className="text-sm font-semibold text-red-600">Out of stock</p>
  if (variant.stock <= 5) return <p className="text-sm font-semibold text-amber-600">Hurry, only {variant.stock} left!</p>
  return <p className="text-sm font-medium text-green-700">In stock</p>
}

function RatingBars({ summary }: { summary: RatingSummary }) {
  return (
    <div className="flex items-center gap-6">
      <div className="text-center">
        <p className="text-4xl font-semibold text-slate-900">{summary.count ? Number(summary.average).toFixed(1) : '–'}<span className="text-2xl">★</span></p>
        <p className="text-xs text-slate-500">{summary.count} {summary.count === 1 ? 'rating' : 'ratings'}</p>
      </div>
      <div className="flex-1 space-y-1">
        {[5, 4, 3, 2, 1].map((star) => {
          const n = summary.distribution[star] ?? 0
          const width = summary.count ? (n / summary.count) * 100 : 0
          return (
            <div key={star} className="flex items-center gap-2 text-xs text-slate-600">
              <span className="w-5">{star}★</span>
              <div className="h-1.5 flex-1 overflow-hidden rounded bg-slate-100">
                <div className={`h-full rounded ${star >= 3 ? 'bg-green-600' : star === 2 ? 'bg-amber-500' : 'bg-red-500'}`} style={{ width: `${width}%` }} />
              </div>
              <span className="w-6 text-right">{n}</span>
            </div>
          )
        })}
      </div>
    </div>
  )
}

function ReviewItem({ review }: { review: Review }) {
  return (
    <li className="border-t border-slate-100 py-4">
      <div className="flex items-center gap-2">
        <RatingBadge rating={review.rating} single />
        <span className="text-sm font-medium text-slate-800">{review.reviewerName}</span>
        <span className="text-xs text-slate-400">{formatDate(review.createdAt)}</span>
      </div>
      <p className="mt-2 text-sm text-slate-700">{review.comment}</p>
    </li>
  )
}

/** "Write a review" for customers who received the product; their own review once written. */
function WriteReview({ slug }: { slug: string }) {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const toast = useToast()
  const mine = useQuery({ queryKey: ['my-review', slug], queryFn: () => getMyReview(slug), enabled: user?.role === 'CUSTOMER' })
  const [rating, setRating] = useState(0)
  const [comment, setComment] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const save = useMutation({
    mutationFn: () => writeReview(slug, { rating, comment }),
    onSuccess: () => {
      toast('Thank you for your review!')
      queryClient.invalidateQueries({ queryKey: ['my-review', slug] })
      queryClient.invalidateQueries({ queryKey: ['product', slug] })
      queryClient.invalidateQueries({ queryKey: ['reviews', slug] })
    },
    onError: (e) => (e instanceof ApiError && Object.keys(e.fieldErrors).length ? setErrors(e.fieldErrors) : toast(errorMessage(e), 'error')),
  })

  if (!mine.data) return null
  if (mine.data.review) {
    return (
      <p className="mt-4 rounded-md bg-green-50 px-4 py-3 text-sm text-green-800">
        You rated this product {mine.data.review.rating}★. Thank you for your review!
      </p>
    )
  }
  if (!mine.data.canReview) return null
  return (
    <form id="write-review" className="mt-4 scroll-mt-40 space-y-3 rounded-lg border border-slate-200 p-4"
      onSubmit={(e) => { e.preventDefault(); setErrors({}); save.mutate() }} noValidate>
      <p className="font-semibold text-slate-900">Rate this product</p>
      <div role="radiogroup" aria-label="Rating" className="flex gap-1">
        {[1, 2, 3, 4, 5].map((star) => (
          <button key={star} type="button" role="radio" aria-checked={rating === star} aria-label={`${star} star${star > 1 ? 's' : ''}`}
            onClick={() => setRating(star)} className={`text-3xl leading-none ${star <= rating ? 'text-amber-400' : 'text-slate-300 hover:text-amber-300'}`}>
            ★
          </button>
        ))}
      </div>
      {errors.rating && <p className="text-xs text-red-600">{errors.rating}</p>}
      <div>
        <label htmlFor="review-comment" className="mb-1 block text-sm font-medium text-slate-700">Your review</label>
        <textarea id="review-comment" rows={3} maxLength={1000} value={comment} onChange={(e) => setComment(e.target.value)}
          placeholder="What did you like or dislike?" className={`w-full rounded-md border px-3 py-2 text-sm ${errors.comment ? 'border-red-400' : 'border-slate-300'}`} />
        {errors.comment && <p className="text-xs text-red-600">{errors.comment}</p>}
      </div>
      <Button type="submit" busy={save.isPending} disabled={rating === 0}>Submit review</Button>
    </form>
  )
}

function Reviews({ product }: { product: ProductDetail }) {
  const [sort, setSort] = useState('newest')
  const [showAll, setShowAll] = useState(false)
  const reviews = useInfiniteQuery({
    queryKey: ['reviews', product.slug, sort],
    queryFn: ({ pageParam }) => getReviews(product.slug, pageParam, sort),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
    enabled: showAll,
  })
  const items = showAll ? reviews.data?.pages.flatMap((p) => p.items) ?? [] : product.topReviews

  return (
    <Card>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-lg font-semibold text-slate-900">Ratings & reviews</h2>
        {showAll && (
          <select aria-label="Sort reviews" value={sort} onChange={(e) => setSort(e.target.value)}
            className="rounded border border-slate-300 px-2 py-1 text-sm">
            <option value="newest">Newest</option>
            <option value="highest">Highest rated</option>
            <option value="lowest">Lowest rated</option>
          </select>
        )}
      </div>
      <div className="mt-4 max-w-md">
        <RatingBars summary={product.ratingSummary} />
      </div>
      <WriteReview slug={product.slug} />
      {items.length === 0 ? (
        <p className="mt-4 text-sm text-slate-500">No reviews yet.</p>
      ) : (
        <ul className="mt-4">
          {items.map((r) => <ReviewItem key={r.id} review={r} />)}
        </ul>
      )}
      {!showAll && product.ratingSummary.count > product.topReviews.length && (
        <Button variant="ghost" onClick={() => setShowAll(true)}>All {product.ratingSummary.count} reviews</Button>
      )}
      {showAll && reviews.hasNextPage && (
        <Button variant="ghost" busy={reviews.isFetchingNextPage} onClick={() => reviews.fetchNextPage()}>Load more reviews</Button>
      )}
    </Card>
  )
}

/** Keyed by slug, so moving to another product starts fresh (size, quantity, gallery image). */
export function ProductPage() {
  const { slug = '' } = useParams()
  return <ProductView key={slug} slug={slug} />
}

function ProductView({ slug }: { slug: string }) {
  const navigate = useNavigate()
  const product = useQuery({ queryKey: ['product', slug], queryFn: () => getProduct(slug) })
  const [chosenId, setChosenId] = useState<number | null>(null)
  const [quantity, setQuantity] = useState(1)
  const addToCart = useAddToCart()
  const { hash } = useLocation()
  // "Rate & review" on a delivered order links to #write-review: scroll there once the form has rendered
  useEffect(() => {
    if (hash !== '#write-review' || !product.data) return
    const timer = setTimeout(() => document.getElementById('write-review')?.scrollIntoView({ behavior: 'smooth' }), 600)
    return () => clearTimeout(timer)
  }, [hash, product.data])

  if (product.isPending) return <PageSpinner />
  if (product.isError) {
    return <ErrorState message={errorMessage(product.error)} onRetry={product.error.message.includes('not found') ? undefined : () => product.refetch()} />
  }
  const p = product.data
  // Default choice: the first variant that is in stock (or simply the first)
  const variant = p.variants.find((v) => v.id === chosenId) ?? p.variants.find((v) => v.stock > 0) ?? p.variants[0]
  const maxQuantity = Math.min(10, variant.stock)
  const showOptions = p.variantType !== 'NONE'

  return (
    <div className="space-y-5">
      <nav aria-label="Breadcrumb" className="text-xs text-slate-500">
        <Link to="/" className="hover:text-brand-700">Home</Link>
        {p.breadcrumb.map((b) => (
          <span key={b.slug}>
            {' › '}
            <Link to={`/c/${b.slug}`} className="hover:text-brand-700">{b.name}</Link>
          </span>
        ))}
      </nav>

      <div className="grid gap-5 rounded-lg bg-white p-5 shadow-sm lg:grid-cols-2">
        <div className="relative lg:sticky lg:top-36 lg:self-start">
          <Gallery images={p.images.length ? p.images : ['']} title={p.title} />
          <HeartButton productId={p.id} className="absolute right-3 top-3" />
        </div>

        <div>
          {p.brand && (
            <Link to={`/search?q=${encodeURIComponent(p.brand.name)}&brand=${p.brand.slug}`} className="text-sm font-semibold uppercase tracking-wide text-brand-700 hover:underline">
              {p.brand.name}
            </Link>
          )}
          <h1 className="mt-1 text-2xl font-semibold text-slate-900">{p.title}</h1>
          <div className="mt-2">
            <RatingBadge rating={Number(p.ratingSummary.average)} count={p.ratingSummary.count} />
          </div>

          <div className="mt-4">
            <PriceLine price={variant.price} mrp={variant.mrp} discount={variant.discountPercent} large />
            <p className="mt-1 text-xs text-slate-500">Inclusive of all taxes</p>
          </div>

          {showOptions && (
            <div className="mt-5">
              <p className="mb-2 text-sm font-semibold text-slate-800">
                {OPTION_NAME[p.variantType]}: <span className="font-normal">{variant.label}</span>
              </p>
              <div className="flex flex-wrap gap-2" role="radiogroup" aria-label={OPTION_NAME[p.variantType]}>
                {p.variants.map((v) => (
                  <button key={v.id} role="radio" aria-checked={v.id === variant.id} disabled={v.stock === 0}
                    onClick={() => {
                      setChosenId(v.id)
                      setQuantity(1)
                    }}
                    className={`min-w-14 rounded-md border px-3 py-2 text-sm font-medium transition ${
                      v.id === variant.id ? 'border-brand-600 bg-brand-50 text-brand-800 ring-1 ring-brand-600'
                        : v.stock === 0 ? 'cursor-not-allowed border-dashed border-slate-200 text-slate-300 line-through'
                        : 'border-slate-300 text-slate-700 hover:border-brand-500'}`}>
                    {v.label}
                  </button>
                ))}
              </div>
            </div>
          )}

          <div className="mt-5 flex items-center gap-4">
            <StockNote variant={variant} />
            {variant.stock > 0 && (
              <label className="flex items-center gap-2 text-sm text-slate-700">
                Qty
                <select value={quantity} onChange={(e) => setQuantity(Number(e.target.value))} className="rounded border border-slate-300 px-2 py-1">
                  {Array.from({ length: maxQuantity }, (_, i) => i + 1).map((n) => <option key={n}>{n}</option>)}
                </select>
              </label>
            )}
          </div>

          <div className="mt-5 grid grid-cols-2 gap-3 sm:max-w-md">
            <Button variant="accent" className="py-3" disabled={variant.stock === 0} busy={addToCart.isPending}
              onClick={() => addToCart.add(variant.id, quantity)}>
              Add to cart
            </Button>
            <Button className="py-3" disabled={variant.stock === 0}
              onClick={() => addToCart.add(variant.id, quantity, { onSuccess: () => navigate('/cart') })}>
              Buy now
            </Button>
          </div>

          <ul className="mt-6 space-y-2 rounded-lg bg-slate-50 p-4 text-sm text-slate-700">
            <li>🚚 {p.shippingInfo ?? 'Ships in 3-5 business days'} · Free delivery on orders of ₹499 or more</li>
            {p.returnPolicy && <li>↩️ {p.returnPolicy}</li>}
            {p.warranty && <li>🛡️ {p.warranty}</li>}
            <li>💳 Cash on delivery, UPI and cards accepted</li>
          </ul>

          <div className="mt-6">
            <h2 className="text-base font-semibold text-slate-900">Description</h2>
            <p className="mt-2 text-sm leading-relaxed text-slate-700">{p.description}</p>
          </div>

          {p.specs.length > 0 && (
            <div className="mt-6">
              <h2 className="text-base font-semibold text-slate-900">Specifications</h2>
              <table className="mt-2 w-full text-sm">
                <tbody>
                  {p.brand && (
                    <tr className="border-b border-slate-100"><th className="w-40 py-2 text-left font-normal text-slate-500">Brand</th><td className="py-2">{p.brand.name}</td></tr>
                  )}
                  {p.specs.map((s) => (
                    <tr key={s.name} className="border-b border-slate-100">
                      <th className="w-40 py-2 text-left font-normal text-slate-500">{s.name}</th>
                      <td className="py-2">{s.name === 'SKU' ? variant.sku : s.value}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>

      <Reviews product={p} />
      <ProductRail title="Similar products" products={p.similar} viewAll={`/c/${p.breadcrumb[p.breadcrumb.length - 1]?.slug}`} />
    </div>
  )
}
