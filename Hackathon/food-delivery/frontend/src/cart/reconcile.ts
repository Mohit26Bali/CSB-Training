import type { MenuItem } from '../api/types'
import type { CartLine } from './cart'

/** What changed in the cart when it was compared with the menu as it is now. */
export type CartChange =
  | { kind: 'price'; id: number; name: string; from: number; to: number }
  | { kind: 'unavailable'; id: number; name: string }
  | { kind: 'available'; id: number; name: string }

export interface Reconciled {
  lines: CartLine[]
  changes: CartChange[]
}

/**
 * Compares the cart with the menu as it is now (only dishes that are on the menu are listed, so a dish missing from `menu` is sold out
 * or gone). A changed price is updated in the line so the total shown is the one the server will charge; a dish that is no longer
 * orderable is flagged, not silently dropped, so the customer sees what happened; a flagged dish that is back on the menu is unflagged.
 * Pure: it returns new lines and the list of changes, and leaves the input alone.
 */
export function reconcile(lines: CartLine[], menu: MenuItem[]): Reconciled {
  const byId = new Map(menu.map((m) => [m.id, m]))
  const changes: CartChange[] = []
  const next = lines.map((line) => {
    const dish = byId.get(line.id)
    if (!dish || !dish.active) {
      if (!line.unavailable) changes.push({ kind: 'unavailable', id: line.id, name: line.name })
      return line.unavailable ? line : { ...line, unavailable: true }
    }
    let updated = line
    if (line.unavailable) {
      changes.push({ kind: 'available', id: line.id, name: line.name })
      const { unavailable: _unused, ...rest } = line
      void _unused
      updated = rest
    }
    if (updated.price !== dish.price) {
      changes.push({ kind: 'price', id: line.id, name: line.name, from: updated.price, to: dish.price })
      updated = { ...updated, price: dish.price }
    }
    return updated
  })
  return { lines: next, changes }
}

/** One short sentence per change, for the notice above the cart. */
export function describeChange(change: CartChange, money: (amount: number) => string): string {
  switch (change.kind) {
    case 'price':
      return `${change.name} is now ${money(change.to)} (was ${money(change.from)})`
    case 'unavailable':
      return `${change.name} is sold out right now`
    case 'available':
      return `${change.name} is back on the menu`
  }
}

/** Notices keep the first "from" and the latest "to" of a price, and drop a price notice that ended where it started. */
export function mergeNotices(existing: CartChange[], incoming: CartChange[]): CartChange[] {
  const merged = new Map<string, CartChange>(existing.map((c) => [`${c.kind}:${c.id}`, c]))
  for (const change of incoming) {
    const key = `${change.kind}:${change.id}`
    const before = merged.get(key)
    if (change.kind === 'price' && before?.kind === 'price') {
      merged.set(key, { ...change, from: before.from })
    } else {
      merged.set(key, change)
    }
    // sold out then back (or the reverse) cancels out
    if (change.kind === 'available') merged.delete(`unavailable:${change.id}`)
    if (change.kind === 'unavailable') merged.delete(`available:${change.id}`)
  }
  return [...merged.values()].filter((c) => c.kind !== 'price' || c.from !== c.to)
}
