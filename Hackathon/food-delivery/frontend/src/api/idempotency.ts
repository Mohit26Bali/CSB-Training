/**
 * A fresh id for one checkout. The server accepts 8 to 64 letters, digits, '-' and '_'. crypto.randomUUID needs a secure context (https or
 * localhost), so a browser that opens the app over plain http on a LAN address gets the fallback instead of an error at checkout.
 */
export function newIdempotencyKey(): string {
  const c = globalThis.crypto
  if (c && typeof c.randomUUID === 'function') return c.randomUUID()
  const bytes = new Uint8Array(16)
  if (c && typeof c.getRandomValues === 'function') c.getRandomValues(bytes)
  else for (let i = 0; i < bytes.length; i++) bytes[i] = Math.floor(Math.random() * 256)
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('')
}

/**
 * One key per distinct checkout: the same cart and details keep the same key (so a retry is recognised by the server), and any change
 * gets a new key (so an edited order is not mistaken for the old one).
 */
export function keyFor(previous: { key: string; signature: string } | null, signature: string): { key: string; signature: string } {
  return previous && previous.signature === signature ? previous : { key: newIdempotencyKey(), signature }
}
