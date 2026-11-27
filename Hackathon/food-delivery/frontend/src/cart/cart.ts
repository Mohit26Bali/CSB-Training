import { createContext, useContext } from 'react'
import type { MenuItem } from '../api/types'
import type { CartChange } from './reconcile'

export interface CartLine {
  id: number
  name: string
  price: number
  imageKey: string
  vegetarian: boolean
  quantity: number
  /** set when the dish is sold out or off the menu: it stays visible but cannot be ordered */
  unavailable?: boolean
}

export interface CartApi {
  lines: CartLine[]
  /** items the customer can order now (sold-out lines are not counted) */
  count: number
  /** the total of the items that can be ordered, at today's menu prices */
  subtotal: number
  /** true when a line is sold out: checkout is blocked until it is removed */
  hasUnavailable: boolean
  /** what changed since the customer last looked (price changes, dishes sold out or back) */
  notices: CartChange[]
  dismissNotices: () => void
  /** asks the server for the menu right now and updates the cart; resolves with what changed */
  refresh: () => Promise<CartChange[]>
  add: (item: MenuItem) => void
  setQuantity: (id: number, quantity: number) => void
  remove: (id: number) => void
  clear: () => void
}

export const CartContext = createContext<CartApi | null>(null)

export const MAX_PER_DISH = 20

export function useCart(): CartApi {
  const context = useContext(CartContext)
  if (!context) throw new Error('useCart must be used inside CartProvider')
  return context
}
