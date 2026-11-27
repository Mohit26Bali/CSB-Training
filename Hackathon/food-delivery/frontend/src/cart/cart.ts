import { createContext, useContext } from 'react'
import type { MenuItem } from '../api/types'

export interface CartLine {
  id: number
  name: string
  price: number
  imageKey: string
  vegetarian: boolean
  quantity: number
}

export interface CartApi {
  lines: CartLine[]
  count: number
  subtotal: number
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
