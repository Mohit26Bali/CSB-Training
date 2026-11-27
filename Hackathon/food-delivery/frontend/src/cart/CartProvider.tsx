import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useAuth } from '../auth/AuthContext'
import { CartContext, MAX_PER_DISH, type CartApi, type CartLine } from './cart'

/** The cart lives in the browser (one per signed-in user), so it survives a reload. The server re-checks every price when the order is placed. */
export function CartProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  const key = user ? `cart:${user.username}` : null
  const [lines, setLines] = useState<CartLine[]>([])

  useEffect(() => {
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
      if (!key) return
      try {
        localStorage.setItem(key, JSON.stringify(next))
      } catch {
        // storage is unavailable: the cart still works for this visit
      }
    },
    [key],
  )

  const api = useMemo<CartApi>(
    () => ({
      lines,
      count: lines.reduce((n, l) => n + l.quantity, 0),
      subtotal: lines.reduce((sum, l) => sum + l.price * l.quantity, 0),
      add: (item) => {
        const existing = lines.find((l) => l.id === item.id)
        if (existing) {
          save(lines.map((l) => (l.id === item.id ? { ...l, quantity: Math.min(MAX_PER_DISH, l.quantity + 1) } : l)))
        } else {
          save([...lines, { id: item.id, name: item.name, price: item.price, imageKey: item.imageKey, vegetarian: item.vegetarian, quantity: 1 }])
        }
      },
      setQuantity: (id, quantity) => save(quantity <= 0 ? lines.filter((l) => l.id !== id) : lines.map((l) => (l.id === id ? { ...l, quantity: Math.min(MAX_PER_DISH, quantity) } : l))),
      remove: (id) => save(lines.filter((l) => l.id !== id)),
      clear: () => save([]),
    }),
    [lines, save],
  )

  return <CartContext.Provider value={api}>{children}</CartContext.Provider>
}
