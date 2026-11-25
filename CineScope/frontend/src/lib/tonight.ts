/**
 * The choices offered on the "what to watch tonight" page (pages/TonightPage). The mood keys must match the ones the
 * backend accepts (service/RecommendationService, MOODS); the labels here are what people see.
 */
export const MOODS = [
  { key: 'laugh', label: 'Make me laugh' },
  { key: 'thrill', label: 'Edge of my seat' },
  { key: 'mind', label: 'Mind-bending' },
  { key: 'romance', label: 'Romantic' },
  { key: 'adventure', label: 'Big adventure' },
  { key: 'family', label: 'Family night' },
  { key: 'scary', label: 'Spooky' },
] as const

/** Longest runtime in minutes (per episode for TV); the backend accepts 30 to 300. */
export const TIMES = [
  { value: 90, label: 'Under 90 min' },
  { value: 120, label: 'Under 2 hours' },
  { value: 150, label: 'Under 2½ hours' },
] as const

export const LANGUAGES = [
  { code: 'en', label: 'English' },
  { code: 'hi', label: 'Hindi' },
  { code: 'ta', label: 'Tamil' },
  { code: 'te', label: 'Telugu' },
  { code: 'ml', label: 'Malayalam' },
  { code: 'kn', label: 'Kannada' },
  { code: 'ko', label: 'Korean' },
  { code: 'ja', label: 'Japanese' },
  { code: 'es', label: 'Spanish' },
  { code: 'fr', label: 'French' },
] as const

/** The wishes as kept in the page address, so a result can be reloaded, bookmarked or shared. */
export interface TonightWishes {
  type: 'movie' | 'tv'
  mood: string | null
  maxMinutes: number | null
  language: string | null
  streamingOnly: boolean
  /** Only titles whose age rating means suitable for everyone (U, G, PG, TV-Y ...); unrated ones are left out. */
  familySafe: boolean
}

/** A fresh number for "find / show me another": the backend draws the same pick for the same seed. */
export function newSeed(): number {
  return Math.floor(Math.random() * 1_000_000_000)
}
