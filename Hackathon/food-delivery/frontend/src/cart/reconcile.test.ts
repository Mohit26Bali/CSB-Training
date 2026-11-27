import { describe, expect, it } from 'vitest'
import type { MenuItem } from '../api/types'
import { keyFor, newIdempotencyKey } from '../api/idempotency'
import { totals } from '../pricing'
import type { CartLine } from './cart'
import { describeChange, mergeNotices, reconcile, type CartChange } from './reconcile'

const dish = (id: number, price: number, active = true): MenuItem => ({
  id, name: `Dish ${id}`, description: null, category: 'MAINS', price, vegetarian: true, active, imageKey: `dish-${id}`,
})
const line = (id: number, price: number, quantity = 1, extra: Partial<CartLine> = {}): CartLine => ({
  id, name: `Dish ${id}`, price, imageKey: `dish-${id}`, vegetarian: true, quantity, ...extra,
})
const money = (n: number) => `₹${n}`

describe('reconcile: the cart against the menu as it is now', () => {
  it('changes nothing when the cart already matches the menu', () => {
    const result = reconcile([line(1, 280, 2)], [dish(1, 280)])
    expect(result.changes).toEqual([])
    expect(result.lines).toEqual([line(1, 280, 2)])
  })

  it('updates a changed price so the total shown is the total that will be charged', () => {
    const result = reconcile([line(1, 280, 2)], [dish(1, 300)])
    expect(result.lines[0].price).toBe(300)
    expect(result.changes).toEqual([{ kind: 'price', id: 1, name: 'Dish 1', from: 280, to: 300 }])
    expect(totals(result.lines[0].price * result.lines[0].quantity).subtotal).toBe(600)
  })

  it('flags a dish that left the menu or was switched off, and keeps it visible with its quantity', () => {
    const gone = reconcile([line(1, 280, 3)], [dish(2, 100)])
    expect(gone.lines[0]).toMatchObject({ id: 1, quantity: 3, unavailable: true })
    expect(gone.changes).toEqual([{ kind: 'unavailable', id: 1, name: 'Dish 1' }])
    const off = reconcile([line(1, 280)], [dish(1, 280, false)])
    expect(off.lines[0].unavailable).toBe(true)
  })

  it('announces a dish only once, not on every refresh', () => {
    const first = reconcile([line(1, 280)], [])
    const second = reconcile(first.lines, [])
    expect(first.changes).toHaveLength(1)
    expect(second.changes).toEqual([])
  })

  it('unflags a dish that is back, and reports a price change in the same pass', () => {
    const result = reconcile([line(1, 280, 1, { unavailable: true })], [dish(1, 290)])
    expect(result.lines[0].unavailable).toBeUndefined()
    expect(result.lines[0].price).toBe(290)
    expect(result.changes.map((c) => c.kind)).toEqual(['available', 'price'])
  })

  it('does not change the lines it was given', () => {
    const original = [line(1, 280)]
    const copy = JSON.parse(JSON.stringify(original))
    reconcile(original, [dish(1, 999, false)])
    expect(original).toEqual(copy)
  })
})

describe('notices', () => {
  it('keep the first price the customer saw and the latest one', () => {
    const merged = mergeNotices([{ kind: 'price', id: 1, name: 'Dish 1', from: 280, to: 300 }], [{ kind: 'price', id: 1, name: 'Dish 1', from: 300, to: 320 }])
    expect(merged).toEqual([{ kind: 'price', id: 1, name: 'Dish 1', from: 280, to: 320 }])
  })

  it('drop a price notice that ended where it began, and sold-out-then-back cancels out', () => {
    expect(mergeNotices([{ kind: 'price', id: 1, name: 'Dish 1', from: 280, to: 300 }], [{ kind: 'price', id: 1, name: 'Dish 1', from: 300, to: 280 }])).toEqual([])
    const gone: CartChange = { kind: 'unavailable', id: 2, name: 'Dish 2' }
    expect(mergeNotices([gone], [{ kind: 'available', id: 2, name: 'Dish 2' }]).some((c) => c.kind === 'unavailable')).toBe(false)
  })

  it('read as plain sentences', () => {
    expect(describeChange({ kind: 'price', id: 1, name: 'Paneer Tikka', from: 280, to: 300 }, money)).toBe('Paneer Tikka is now ₹300 (was ₹280)')
    expect(describeChange({ kind: 'unavailable', id: 1, name: 'Paneer Tikka' }, money)).toBe('Paneer Tikka is sold out right now')
  })
})

describe('pricing mirrors the server', () => {
  it('adds 5% tax and 40 delivery below 500, and no delivery from 500', () => {
    expect(totals(230)).toEqual({ subtotal: 230, tax: 11.5, deliveryFee: 40, total: 281.5 })
    expect(totals(500)).toEqual({ subtotal: 500, tax: 25, deliveryFee: 0, total: 525 })
    expect(totals(0).total).toBe(0)
  })
})

describe('idempotency keys', () => {
  it('are in the shape the server accepts', () => {
    expect(newIdempotencyKey()).toMatch(/^[A-Za-z0-9_-]{8,64}$/)
    expect(newIdempotencyKey()).not.toBe(newIdempotencyKey())
  })

  it('stay the same for the same checkout and change when the checkout changes', () => {
    const first = keyFor(null, 'cart-a')
    expect(keyFor(first, 'cart-a').key).toBe(first.key)
    expect(keyFor(first, 'cart-b').key).not.toBe(first.key)
  })
})
