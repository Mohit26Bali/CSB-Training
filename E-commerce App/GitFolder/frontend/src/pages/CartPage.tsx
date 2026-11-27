import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router-dom'
import { removeCartItem, saveForLater, setCartQuantity } from '../api/endpoints'
import type { Cart, CartItem } from '../api/types'
import { useToast } from '../components/Toast'
import { Button, Card, EmptyState, ErrorState, PageSpinner } from '../components/ui'
import { errorMessage, useCart } from '../hooks'
import { formatPrice, variantText } from '../lib/format'

export function PriceDetails({ rows, total, saving }: { rows: [string, number, string?][]; total: number; saving: number }) {
  return (
    <Card className="lg:sticky lg:top-36">
      <h2 className="border-b border-slate-100 pb-3 text-sm font-semibold uppercase tracking-wider text-slate-500">Price details</h2>
      <dl className="space-y-3 py-4 text-sm">
        {rows.map(([label, amount, note]) => (
          <div key={label} className="flex justify-between">
            <dt className="text-slate-700">{label}</dt>
            <dd className={amount < 0 ? 'text-green-700' : 'text-slate-900'}>
              {note ?? (amount < 0 ? `− ${formatPrice(-amount)}` : formatPrice(amount))}
            </dd>
          </div>
        ))}
      </dl>
      <div className="flex justify-between border-t border-dashed border-slate-200 pt-3 text-base font-semibold">
        <span>Total amount</span>
        <span>{formatPrice(total)}</span>
      </div>
      {saving > 0 && <p className="mt-3 text-sm font-semibold text-green-700">You will save {formatPrice(saving)} on this order</p>}
    </Card>
  )
}

function CartLine({ item }: { item: CartItem }) {
  const queryClient = useQueryClient()
  const toast = useToast()

  const mutation = useMutation({
    mutationFn: (fn: () => Promise<Cart>) => fn(),
    onSuccess: (cart) => queryClient.setQueryData(['cart'], cart),
    onError: (e) => toast(errorMessage(e), 'error'),
  })

  return (
    <li className="flex gap-4 border-b border-slate-100 py-5 last:border-0">
      <Link to={`/p/${item.productSlug}`} className="h-24 w-24 shrink-0 rounded-md bg-slate-50 p-2">
        <img src={item.thumbnailUrl} alt={item.title} className="h-full w-full object-contain" />
      </Link>
      <div className="min-w-0 flex-1">
        <Link to={`/p/${item.productSlug}`} className="line-clamp-2 text-sm font-medium text-slate-900 hover:text-brand-700">{item.title}</Link>
        <p className="mt-0.5 text-xs text-slate-500">
          {[item.brand, variantText(item.variantLabel) && `${item.variantType === 'SIZE' ? 'Size' : 'Option'}: ${item.variantLabel}`].filter(Boolean).join(' · ')}
        </p>
        <p className="mt-2 flex flex-wrap items-baseline gap-2">
          <span className="font-semibold">{formatPrice(item.price)}</span>
          {item.mrp > item.price && <span className="text-xs text-slate-400 line-through">{formatPrice(item.mrp)}</span>}
        </p>
        {item.problem && <p role="alert" className="mt-1 text-sm font-medium text-red-600">{item.problem}</p>}
        <div className="mt-3 flex flex-wrap items-center gap-4 text-sm">
          {item.maxQuantity > 0 && (
            <label className="flex items-center gap-2">
              Qty
              <select value={item.quantity} disabled={mutation.isPending}
                onChange={(e) => mutation.mutate(() => setCartQuantity(item.id, Number(e.target.value)))}
                className="rounded border border-slate-300 px-2 py-1">
                {Array.from({ length: Math.max(item.maxQuantity, item.quantity) }, (_, i) => i + 1).map((n) => (
                  <option key={n} disabled={n > item.maxQuantity}>{n}</option>
                ))}
              </select>
            </label>
          )}
          <button className="font-semibold text-slate-700 hover:text-brand-700" disabled={mutation.isPending}
            onClick={() => mutation.mutate(() => saveForLater(item.id).then((c) => {
              queryClient.invalidateQueries({ queryKey: ['wishlist'] })
              return c
            }))}>
            Save for later
          </button>
          <button className="font-semibold text-slate-700 hover:text-red-600" disabled={mutation.isPending}
            onClick={() => mutation.mutate(() => removeCartItem(item.id))}>
            Remove
          </button>
        </div>
      </div>
      <p className="hidden font-semibold text-slate-900 sm:block">{formatPrice(item.lineTotal)}</p>
    </li>
  )
}

export function CartPage() {
  const cart = useCart()
  const navigate = useNavigate()
  if (cart.isPending) return <PageSpinner />
  if (cart.isError) return <ErrorState message={errorMessage(cart.error)} onRetry={() => cart.refetch()} />
  const { items, summary, checkoutReady } = cart.data
  if (items.length === 0) {
    return <EmptyState title="Your cart is empty" text="Add items to it now." action={<Link to="/"><Button>Shop now</Button></Link>} />
  }
  const toFree = summary.freeDeliveryThreshold - summary.subtotal

  return (
    <div className="grid gap-4 lg:grid-cols-[1fr_340px]">
      <Card>
        <h1 className="text-lg font-semibold text-slate-900">My cart ({summary.itemCount})</h1>
        {summary.deliveryFee > 0 && toFree > 0 && (
          <p className="mt-2 rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-800">
            Add {formatPrice(toFree)} more for free delivery.
          </p>
        )}
        <ul>{items.map((item) => <CartLine key={item.id} item={item} />)}</ul>
        <div className="flex justify-end border-t border-slate-100 pt-4">
          <Button variant="accent" className="px-10 py-3" disabled={!checkoutReady} onClick={() => navigate('/checkout')}>
            Place order
          </Button>
        </div>
        {!checkoutReady && <p className="mt-2 text-right text-sm text-red-600">Fix the items marked in red before placing the order.</p>}
      </Card>
      <div>
        <PriceDetails
          rows={[
            [`Price (${summary.itemCount} ${summary.itemCount === 1 ? 'item' : 'items'})`, summary.totalMrp],
            ['Discount', -summary.discount],
            ['Delivery charges', summary.deliveryFee, summary.deliveryFee === 0 ? 'Free' : undefined],
          ]}
          total={summary.total}
          saving={summary.discount}
        />
      </div>
    </div>
  )
}
