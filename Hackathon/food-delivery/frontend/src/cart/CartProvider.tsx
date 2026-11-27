import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { customerApi } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'
import { CartContext, MAX_PER_DISH, type CartApi, type CartLine } from './cart'
import { mergeNotices, reconcile, type CartChange } from './reconcile'

const MENU_KEY = ['menu', 'all'] as const

/**
 * The cart lives in the browser (one per signed-in user), so it survives a reload. The server re-checks every price when the order is
 * placed, so the cart also compares itself with the live menu (on load, every minute, and right before checkout): a changed price is
 * updated and announced, a sold-out dish is flagged, so the total on screen is the total that will be charged.
 */
export function CartProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const key = user ? `cart:${user.username}` : null
  const [lines, setLines] = useState<CartLine[]>([])
  const [notices, setNotices] = useState<CartChange[]>([])
  const linesRef = useRef<CartLine[]>([])
  linesRef.current = lines

  useEffect(() => {
    setNotices([])
    if (!key) {
      setLines([])
      return
    }
    try {
      const saved = localStorage.getItem(key)
      setLines(saved ? (JSON.parse(saved) as CartLine[]) : [])
    } catch {
      setLines([])
    }
  }, [key])

  const save = useCallback(
    (next: CartLine[]) => {
      setLines(next)
      linesRef.current = next
      if (!key) return
      try {
        localStorage.setItem(key, JSON.stringify(next))
      } catch {
        // storage is unavailable: the cart still works for this visit
      }
    },
    [key],
  )

  const apply = useCallback(
    (menu: Parameters<typeof reconcile>[1]): CartChange[] => {
      const result = reconcile(linesRef.current, menu)
      if (result.changes.length > 0) {
        save(result.lines)
        setNotices((previous) => mergeNotices(previous, result.changes))
      }
      return result.changes
    },
    [save],
  )

  const isCustomer = user?.role === 'CUSTOMER'
  const menu = useQuery({
    queryKey: MENU_KEY,
    queryFn: () => customerApi.menu(),
    enabled: isCustomer && lines.length > 0,
    staleTime: 30_000,
    refetchInterval: 60_000,
  })
  useEffect(() => {
    if (menu.data) apply(menu.data)
  }, [menu.data, apply])

  const refresh = useCallback(async () => {
    const fresh = await queryClient.fetchQuery({ queryKey: MENU_KEY, queryFn: () => customerApi.menu(), staleTime: 0 })
    return apply(fresh)
  }, [queryClient, apply])

  const api = useMemo<CartApi>(() => {
    const orderable = lines.filter((l) => !l.unavailable)
    return {
      lines,
      count: orderable.reduce((n, l) => n + l.quantity, 0),
      subtotal: orderable.reduce((sum, l) => sum + l.price * l.quantity, 0),
      hasUnavailable: lines.some((l) => l.unavailable),
      notices,
      dismissNotices: () => setNotices([]),
      refresh,
      add: (item) => {
        const existing = lines.find((l) => l.id === item.id)
        if (existing) {
          save(lines.map((l) => (l.id === item.id ? { ...l, price: item.price, quantity: Math.min(MAX_PER_DISH, l.quantity + 1) } : l)))
        } else {
          save([...lines, { id: item.id, name: item.name, price: item.price, imageKey: item.imageKey, vegetarian: item.vegetarian, quantity: 1 }])
        }
      },
      setQuantity: (id, quantity) => save(quantity <= 0 ? lines.filter((l) => l.id !== id) : lines.map((l) => (l.id === id ? { ...l, quantity: Math.min(MAX_PER_DISH, quantity) } : l))),
      remove: (id) => save(lines.filter((l) => l.id !== id)),
      clear: () => {
        save([])
        setNotices([])
      },
    }
  }, [lines, notices, refresh, save])

  return <CartContext.Provider value={api}>{children}</CartContext.Provider>
}
