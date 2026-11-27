import { Link } from 'react-router-dom'
import { ProductCard } from '../components/ProductCard'
import { Button, EmptyState, ErrorState, PageSpinner } from '../components/ui'
import { errorMessage, useWishlist } from '../hooks'

export function WishlistPage() {
  const wishlist = useWishlist()
  if (wishlist.isPending) return <PageSpinner />
  if (wishlist.isError) return <ErrorState message={errorMessage(wishlist.error)} onRetry={() => wishlist.refetch()} />
  if (wishlist.data.length === 0) {
    return (
      <EmptyState title="Your wishlist is empty" text="Tap the heart on any product to save it here."
        action={<Link to="/"><Button>Explore products</Button></Link>} />
    )
  }
  return (
    <div>
      <h1 className="mb-4 text-lg font-semibold text-slate-900">My wishlist ({wishlist.data.length})</h1>
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
        {wishlist.data.map(({ product }) => (
          <div key={product.id} className="flex flex-col gap-2">
            <ProductCard product={product} />
            <Link to={`/p/${product.slug}`}>
              <Button variant="outline" className="w-full" disabled={!product.inStock}>
                {product.inStock ? 'Move to cart' : 'Out of stock'}
              </Button>
            </Link>
          </div>
        ))}
      </div>
    </div>
  )
}
