/**
 * TypeScript mirror of the backend DTOs (backend/src/main/java/com/csb/cinescope/dto). Keep in sync by hand: when a
 * record in dto/ changes, change the matching interface here. Image fields are TMDB image PATHS (for example
 * "/abc.jpg"); turn them into URLs with lib/images.ts.
 */

export type MediaType = 'movie' | 'tv'

export interface TitleSummary {
  id: number
  type: MediaType
  title: string | null
  overview: string | null
  posterPath: string | null
  backdropPath: string | null
  year: number | null
  rating: number
}

export interface PageResult<T> {
  items: T[]
  page: number
  totalPages: number
  totalResults: number
}

export interface HomeRow {
  key: string
  title: string
  category: string
  /** "all" for the trending row, otherwise movie or tv. */
  mediaType: string
  items: TitleSummary[]
}

export interface HomeResponse {
  hero: TitleSummary | null
  /** The titles that rotate in the home banner (the first is {@code hero}). */
  heroes: TitleSummary[]
  rows: HomeRow[]
}

export interface Genre {
  id: number
  name: string
}

export interface CastMember {
  id: number
  name: string
  character: string | null
  profilePath: string | null
}

export interface CrewMember {
  id: number
  name: string
  job: string
}

export interface Video {
  key: string
  name: string | null
  type: string
  official: boolean
}

export interface TitleDetail {
  id: number
  type: MediaType
  title: string | null
  tagline: string | null
  overview: string | null
  posterPath: string | null
  backdropPath: string | null
  year: number | null
  releaseDate: string | null
  runtime: number | null
  rating: number
  voteCount: number
  status: string | null
  language: string | null
  certification: string | null
  /** True only when the age rating means suitable for everyone (backend mapper/AgeRating); an unrated title is not. */
  familyFriendly: boolean
  genres: Genre[]
  directors: CrewMember[]
  cast: CastMember[]
  videos: Video[]
  similar: TitleSummary[]
  budget: number | null
  revenue: number | null
  seasons: number | null
  episodes: number | null
  homepage: string | null
}

export interface SearchItem {
  id: number
  type: 'movie' | 'tv' | 'person'
  title: string | null
  imagePath: string | null
  year: number | null
  rating: number
  overview: string | null
  knownFor: string | null
}

export type SortOption = 'popularity' | 'rating' | 'newest'

export interface DiscoverFilters {
  type: MediaType
  genre?: number
  year?: number
  minRating?: number
  sort: SortOption
}

// ---- what to watch tonight (backend dto/Recommendation, Suggestion) ----

export interface Suggestion {
  id: number
  type: string
  title: string | null
  overview: string | null
  posterPath: string | null
  backdropPath: string | null
  year: number | null
  rating: number
  onWatchlist: boolean
}

export interface Recommendation {
  pick: Suggestion | null
  details: TitleDetail | null
  providers: WatchProviders | null
  alternatives: Suggestion[]
  reasons: string[]
  considered: number
  seed: number
}

// ---- my profile and the administrator's account list (backend dto/UserResponse, AdminUserView ...) ----

export interface UserProfile {
  username: string
  email: string
  displayName: string
  region: string
  role: Role
}

export interface UpdateProfilePayload {
  displayName: string
  region: string
}

export interface AdminUser {
  id: number
  username: string
  email: string
  displayName: string
  region: string
  role: Role
  enabled: boolean
  createdAt: string
  watchlistCount: number
}

export interface AdminUserQuery {
  q: string
  role: Role | null
  enabled: boolean | null
  page: number
}

export interface AdminCreatePayload {
  username: string
  email: string
  displayName: string
  password: string
  region: string
  role: Role
}

export interface AdminUpdatePayload {
  displayName: string
  region: string
  role: Role
  enabled: boolean
}

// ---- reviews and discussion (backend dto/CommentView, DiscussionSummary ...) ----

export type Sentiment = 'POSITIVE' | 'NEUTRAL' | 'NEGATIVE'
export type CommentStatus = 'PUBLISHED' | 'PENDING' | 'REJECTED'
export type CommentSort = 'helpful' | 'newest' | 'positive' | 'critical'

/** One stage of the comment-processing pipeline (backend service/CommentProcessor). */
export interface ProcessingStep {
  name: string
  outcome: 'PASSED' | 'CHANGED' | 'FLAGGED' | 'HELD' | string
  detail: string
}

export interface CommentView {
  id: number
  parentId: number | null
  tmdbId: number
  type: MediaType
  authorUsername: string
  authorName: string
  body: string
  rating: number | null
  sentiment: Sentiment
  sentimentScore: number
  status: CommentStatus
  helpfulCount: number
  helpfulByMe: boolean
  mine: boolean
  createdAt: string
  steps: ProcessingStep[]
  replies: CommentView[]
}

export interface CommentPayload {
  body: string
  rating?: number | null
}

export interface HelpfulResult {
  helpful: boolean
  helpfulCount: number
}

export interface DiscussionSummary {
  commentCount: number
  replyCount: number
  ratingCount: number
  averageRating: number | null
  histogram: number[]
  tmdbRating: number | null
  communityScore: number | null
  sentiment: { positive: number; neutral: number; negative: number; positivePercent: number; neutralPercent: number; negativePercent: number }
  topics: { word: string; count: number }[]
}

// ---- sign-in screens (backend dto/ShowcaseSlide) ----

export interface ShowcaseSlide {
  quoteId: number
  id: number
  type: MediaType
  category: 'HOLLYWOOD' | 'TV' | 'BOLLYWOOD' | 'ANIME'
  title: string
  year: number | null
  backdropPath: string
  posterPath: string | null
  quote: string
  speaker: string
}

// ---- people (backend dto/PersonDetail, PersonCredit) ----

export interface PersonCredit {
  id: number
  type: MediaType
  title: string
  posterPath: string | null
  year: number | null
  rating: number
  role: string | null
}

export interface PersonDetail {
  id: number
  name: string
  biography: string | null
  birthday: string | null
  deathday: string | null
  placeOfBirth: string | null
  knownForDepartment: string | null
  profilePath: string | null
  homepage: string | null
  knownFor: PersonCredit[]
  acting: PersonCredit[]
  crew: PersonCredit[]
}

// ---- where to watch (backend dto/WatchProviders) ----

export interface WatchProvider {
  id: number
  name: string
  logoPath: string | null
}

export interface WatchProviders {
  region: string
  link: string | null
  stream: WatchProvider[]
  free: WatchProvider[]
  rent: WatchProvider[]
  buy: WatchProvider[]
}

// ---- accounts and watchlist (backend dto/AuthResponse, WatchlistEntry, WatchlistSummary) ----

export type Role = 'USER' | 'ADMIN'

export interface AuthResponse {
  token: string
  expiresInSeconds: number
  username: string
  displayName: string
  region: string
  role: Role
}

export interface RegisterPayload {
  username: string
  email: string
  password: string
  displayName: string
}

export type WatchStatus = 'WANT' | 'WATCHING' | 'WATCHED'

export interface WatchlistEntry {
  tmdbId: number
  type: MediaType
  title: string | null
  posterPath: string | null
  year: number | null
  status: WatchStatus
  rating: number | null
  note: string | null
  addedAt: string
  updatedAt: string
}

export interface WatchlistEntryPayload {
  status: WatchStatus
  rating?: number | null
  note?: string | null
}

export interface WatchlistSummary {
  total: number
  want: number
  watching: number
  watched: number
}

export type WatchlistSort = 'added' | 'updated' | 'title' | 'rating'

export interface WatchlistQuery {
  status?: WatchStatus
  sort: WatchlistSort
  page: number
}

// ---- the famous-lines quiz (backend dto/Quiz*) ----

/** The famous-lines categories, ALL for a mix, and the two picture-and-cast games CAST and POSTER. */
export type QuizCategory = 'ALL' | 'HOLLYWOOD' | 'TV' | 'BOLLYWOOD' | 'ANIME' | 'CAST' | 'POSTER'

export interface QuizQuestion {
  seq: number
  /** What the clue is: a quote (TITLE, SPEAKER), three actors (CAST) or a poster picture (POSTER). */
  kind: 'TITLE' | 'SPEAKER' | 'CAST' | 'POSTER'
  prompt: string
  line: string | null
  actors: string[] | null
  posterPath: string | null
  options: string[]
}

export interface QuizRound {
  id: number
  category: QuizCategory
  total: number
  questions: QuizQuestion[]
}

export interface QuizAnswerResult {
  correct: boolean
  correctIndex: number
  points: number
  /** Of the points, the bonus for a quick right answer in the poster game (otherwise 0). */
  speedBonus: number
  score: number
  streak: number
  bestStreak: number
  correctCount: number
  answered: number
  total: number
  finished: boolean
  reveal: { title: string; year: number; speaker: string | null; tmdbId: number; mediaType: MediaType; category: string }
}

export interface QuizBoardRow {
  rank: number
  username: string
  displayName: string
  score: number
  you: boolean
}

export interface QuizLeaderboard {
  category: QuizCategory
  entries: QuizBoardRow[]
  you: QuizBoardRow | null
}

// ---- film series (backend dto/FranchiseView, FranchiseAddResult) ----

export interface FranchisePart {
  id: number
  title: string
  year: number | null
  releaseDate: string | null
  posterPath: string | null
  released: boolean
  status: 'WANT' | 'WATCHING' | 'WATCHED' | null
}

export interface Franchise {
  id: number
  name: string
  posterPath: string | null
  total: number
  released: number
  watched: number
  missing: number
  parts: FranchisePart[]
}

export interface FranchiseAddResult {
  added: number
  alreadyOnList: number
  notReleased: number
}

// ---- episode ratings (backend dto/EpisodeHeatmap) ----

export interface EpisodeRating {
  season: number
  number: number
  name: string
  airDate: string | null
  /** TMDB's average vote out of 10; null when nobody has rated the episode yet (never zero). */
  rating: number | null
  votes: number
  aired: boolean
}

export interface EpisodeSeason {
  number: number
  average: number | null
  unavailable: boolean
  episodes: EpisodeRating[]
}

export interface EpisodeHeatmap {
  id: number
  name: string
  seasonCount: number
  episodeCount: number
  ratedCount: number
  average: number | null
  best: EpisodeRating | null
  worst: EpisodeRating | null
  truncated: boolean
  seasons: EpisodeSeason[]
}
