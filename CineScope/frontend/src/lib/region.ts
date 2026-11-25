/**
 * The country whose streaming services the "Where to watch" section shows (components/WhereToWatch). The backend
 * defaults to India; a signed-in user starts with their own account region, and any visitor can pick another country,
 * which is remembered in this browser. Storage can fail (private windows), so every access is guarded.
 */
export const REGIONS = ['IN', 'US', 'GB', 'CA', 'AU', 'AE', 'SG', 'DE', 'FR', 'JP'] as const

const KEY = 'cinescope.region'

export function loadRegion(fallback = 'IN'): string {
  try {
    const saved = localStorage.getItem(KEY)
    if (saved && (REGIONS as readonly string[]).includes(saved)) return saved
  } catch {
    /* ignore: use the fallback */
  }
  return fallback
}

export function saveRegion(region: string) {
  try {
    localStorage.setItem(KEY, region)
  } catch {
    /* choice lasts for this page view only */
  }
}

/** "India", "United States" ... in the browser's language. */
export function regionName(code: string): string {
  try {
    return new Intl.DisplayNames(['en'], { type: 'region' }).of(code) ?? code
  } catch {
    return code
  }
}
