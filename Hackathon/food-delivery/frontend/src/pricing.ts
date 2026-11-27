/** The same price rules the server applies, so the cart can show the total before the order is placed. The server is the one that decides. */
export const TAX_RATE = 0.05
export const MIN_ORDER = 100
export const FREE_DELIVERY_FROM = 500
export const DELIVERY_FEE = 40

export interface Totals {
  subtotal: number
  tax: number
  deliveryFee: number
  total: number
}

export function totals(subtotal: number): Totals {
  const tax = Math.round(subtotal * TAX_RATE * 100) / 100
  const deliveryFee = subtotal === 0 || subtotal >= FREE_DELIVERY_FROM ? 0 : DELIVERY_FEE
  return { subtotal, tax, deliveryFee, total: Math.round((subtotal + tax + deliveryFee) * 100) / 100 }
}
