/**
 * Builds TMDB image URLs from the image paths the backend sends (for example "/abc.jpg"). TMDB serves each image in
 * several fixed widths from image.tmdb.org; pick the smallest that still looks sharp, as it keeps pages fast.
 */
const BASE = 'https://image.tmdb.org/t/p'

export type PosterSize = 'w185' | 'w342' | 'w500'
export type BackdropSize = 'w780' | 'w1280' | 'original'
export type ProfileSize = 'w185' | 'h632'

export function posterUrl(path: string | null, size: PosterSize = 'w342'): string | null {
  return path ? `${BASE}/${size}${path}` : null
}

export function backdropUrl(path: string | null, size: BackdropSize = 'w1280'): string | null {
  return path ? `${BASE}/${size}${path}` : null
}

export function profileUrl(path: string | null, size: ProfileSize = 'w185'): string | null {
  return path ? `${BASE}/${size}${path}` : null
}

/** A streaming service's logo (Netflix, Prime Video ...), shown as a small rounded square. */
export function logoUrl(path: string | null): string | null {
  return path ? `${BASE}/w92${path}` : null
}

/** The still image YouTube shows for a video, used as the thumbnail of a trailer. */
export function youtubeThumbnail(key: string): string {
  return `https://img.youtube.com/vi/${key}/hqdefault.jpg`
}
