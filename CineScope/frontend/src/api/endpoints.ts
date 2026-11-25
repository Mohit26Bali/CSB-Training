import { http } from './client'
import type { TonightWishes } from '../lib/tonight'
import type {
  AdminCreatePayload,
  AdminUpdatePayload,
  AdminUser,
  AdminUserQuery,
  AuthResponse,
  CommentPayload,
  CommentSort,
  CommentView,
  DiscoverFilters,
  DiscussionSummary,
  Genre,
  HelpfulResult,
  HomeResponse,
  MediaType,
  PageResult,
  PersonDetail,
  Recommendation,
  RegisterPayload,
  SearchItem,
  EpisodeHeatmap,
  Franchise,
  FranchiseAddResult,
  QuizAnswerResult,
  QuizCategory,
  QuizLeaderboard,
  QuizRound,
  ShowcaseSlide,
  TitleDetail,
  TitleSummary,
  UpdateProfilePayload,
  UserProfile,
  WatchProviders,
  WatchlistEntry,
  WatchlistEntryPayload,
  WatchlistQuery,
  WatchlistSummary,
} from './types'

/**
 * One function per backend endpoint (backend controller/CatalogController). Pure data access with no React; pages use
 * these through the hooks in api/queries.ts.
 */
export const catalogApi = {
  home: () => http.get<HomeResponse>('/home').then((r) => r.data),

  list: (category: string, type: string, page: number) =>
    http.get<PageResult<TitleSummary>>('/titles', { params: { category, type, page } }).then((r) => r.data),

  discover: (filters: DiscoverFilters, page: number) =>
    http
      .get<PageResult<TitleSummary>>('/discover', {
        params: { type: filters.type, genre: filters.genre, year: filters.year, minRating: filters.minRating, sort: filters.sort, page },
      })
      .then((r) => r.data),

  search: (query: string, page: number) =>
    http.get<PageResult<SearchItem>>('/search', { params: { query, page } }).then((r) => r.data),

  genres: (type: MediaType) => http.get<Genre[]>('/genres', { params: { type } }).then((r) => r.data),

  episodes: (showId: number) => http.get<EpisodeHeatmap>(`/titles/tv/${showId}/episodes`).then((r) => r.data),

  title: (type: MediaType, id: number) => http.get<TitleDetail>(`/titles/${type}/${id}`).then((r) => r.data),

  showcase: (exclude: number[] = []) =>
    http.get<ShowcaseSlide[]>('/showcase', { params: exclude.length ? { exclude: exclude.join(',') } : undefined }).then((r) => r.data),

  tonight: (w: TonightWishes, seed: number) =>
    http
      .get<Recommendation>('/tonight', {
        params: { type: w.type, mood: w.mood, maxMinutes: w.maxMinutes, language: w.language, streamingOnly: w.streamingOnly || undefined, familySafe: w.familySafe || undefined, seed },
      })
      .then((r) => r.data),

  person: (id: number) => http.get<PersonDetail>(`/people/${id}`).then((r) => r.data),

  providers: (type: MediaType, id: number, region: string) =>
    http.get<WatchProviders>(`/titles/${type}/${id}/providers`, { params: { region } }).then((r) => r.data),
}

/** Accounts (backend controller/AuthController). The token in the answer is stored by auth/AuthContext. */
export const authApi = {
  login: (username: string, password: string) => http.post<AuthResponse>('/auth/login', { username, password }).then((r) => r.data),
  register: (payload: RegisterPayload) => http.post<AuthResponse>('/auth/register', payload).then((r) => r.data),
}

/** The signed-in user's watchlist (backend controller/WatchlistController); every call needs the bearer token. */
export const watchlistApi = {
  list: (q: WatchlistQuery) =>
    http.get<PageResult<WatchlistEntry>>('/watchlist', { params: { status: q.status, sort: q.sort, page: q.page, size: 20 } }).then((r) => r.data),

  summary: () => http.get<WatchlistSummary>('/watchlist/summary').then((r) => r.data),

  /** The entry for one title, or null when it is not on the list (the backend answers 404). */
  get: (type: MediaType, id: number) =>
    http
      .get<WatchlistEntry>(`/watchlist/${type}/${id}`)
      .then((r) => r.data)
      .catch((e) => (e?.status === 404 ? null : Promise.reject(e))),

  save: (type: MediaType, id: number, payload: WatchlistEntryPayload) =>
    http.put<WatchlistEntry>(`/watchlist/${type}/${id}`, payload).then((r) => r.data),

  remove: (type: MediaType, id: number) => http.delete(`/watchlist/${type}/${id}`).then(() => undefined),
}

/** My own account (backend controller/ProfileController). Changing the password answers with a new login token. */
export const profileApi = {
  get: () => http.get<UserProfile>('/profile').then((r) => r.data),
  update: (payload: UpdateProfilePayload) => http.put<UserProfile>('/profile', payload).then((r) => r.data),
  changePassword: (payload: { currentPassword: string; newPassword: string }) => http.put<AuthResponse>('/profile/password', payload).then((r) => r.data),
  remove: (password: string) => http.delete('/profile', { data: { password } }).then(() => undefined),
}

/** Account management, administrators only (backend controller/AdminUserController). */
export const adminApi = {
  list: (q: AdminUserQuery) =>
    http
      .get<PageResult<AdminUser>>('/admin/users', { params: { q: q.q || undefined, role: q.role ?? undefined, enabled: q.enabled ?? undefined, page: q.page, size: 10 } })
      .then((r) => r.data),
  create: (payload: AdminCreatePayload) => http.post<AdminUser>('/admin/users', payload).then((r) => r.data),
  update: (id: number, changes: AdminUpdatePayload) => http.put<AdminUser>(`/admin/users/${id}`, changes).then((r) => r.data),
  remove: (id: number) => http.delete(`/admin/users/${id}`).then(() => undefined),
}

/** Reviews and discussion on titles (backend controller/CommentController). */
export const commentApi = {
  list: (type: MediaType, id: number, sort: CommentSort, page: number) =>
    http.get<PageResult<CommentView>>(`/titles/${type}/${id}/comments`, { params: { sort, page, size: 5 } }).then((r) => r.data),
  summary: (type: MediaType, id: number) => http.get<DiscussionSummary>(`/titles/${type}/${id}/discussion`).then((r) => r.data),
  post: (type: MediaType, id: number, payload: CommentPayload) => http.post<CommentView>(`/titles/${type}/${id}/comments`, payload).then((r) => r.data),
  reply: (parentId: number, payload: CommentPayload) => http.post<CommentView>(`/comments/${parentId}/replies`, payload).then((r) => r.data),
  edit: (id: number, payload: CommentPayload) => http.put<CommentView>(`/comments/${id}`, payload).then((r) => r.data),
  remove: (id: number) => http.delete(`/comments/${id}`).then(() => undefined),
  helpful: (id: number) => http.post<HelpfulResult>(`/comments/${id}/helpful`).then((r) => r.data),
}

/** Film series and the person's progress through them (backend controller/FranchiseController). */
export const franchiseApi = {
  /** The series a film belongs to, or null when it is in none (the server answers 204). */
  ofFilm: (id: number) => http.get<Franchise>(`/titles/movie/${id}/franchise`).then((r) => (r.status === 204 ? null : r.data)),
  addRest: (collectionId: number) => http.post<FranchiseAddResult>(`/collections/${collectionId}/watchlist`).then((r) => r.data),
}

/** The famous-lines quiz (backend controller/QuizController). */
export const quizApi = {
  start: (category: QuizCategory) => http.post<QuizRound>('/quiz/rounds', { category }).then((r) => r.data),
  /** Tells the server a question is on screen: the poster game's speed bonus is measured from here, on the server. */
  shown: (roundId: number, seq: number) => http.post(`/quiz/rounds/${roundId}/questions/${seq}/shown`).then(() => undefined),
  answer: (roundId: number, seq: number, choice: number) => http.post<QuizAnswerResult>(`/quiz/rounds/${roundId}/answers`, { seq, choice }).then((r) => r.data),
  leaderboard: (category: QuizCategory) => http.get<QuizLeaderboard>('/quiz/leaderboard', { params: { category } }).then((r) => r.data),
}

/** The administrator's review of comments the pipeline held back (backend controller/AdminCommentController). */
export const moderationApi = {
  queue: (status: string, page: number) =>
    http.get<PageResult<CommentView>>('/admin/comments', { params: { status, page, size: 10 } }).then((r) => r.data),
  decide: (id: number, action: 'APPROVE' | 'REJECT') => http.put<CommentView>(`/admin/comments/${id}/moderation`, { action }).then((r) => r.data),
}
