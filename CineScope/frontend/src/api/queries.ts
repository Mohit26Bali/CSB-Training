import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { rememberQuoteIds, shownQuoteIds } from '../lib/shownQuotes'
import { adminApi, catalogApi, commentApi, franchiseApi, moderationApi, profileApi, quizApi, watchlistApi } from './endpoints'
import type { TonightWishes } from '../lib/tonight'
import type { AdminUpdatePayload, AdminUserQuery, CommentPayload, CommentSort, DiscoverFilters, MediaType, PageResult, QuizCategory, WatchlistEntryPayload, WatchlistQuery } from './types'

/**
 * React Query hooks: the only way pages read server data. Lists use infinite queries, so a page can offer "Load more"
 * by fetching the next TMDB page and appending it. The backend caches TMDB answers; staleTime here (set in main.tsx)
 * avoids refetching what the browser already has.
 */

/** Next page number for an infinite query, or undefined when the last page was reached. */
function nextPage<T>(last: PageResult<T>): number | undefined {
  return last.page < last.totalPages ? last.page + 1 : undefined
}

export const useHome = () => useQuery({ queryKey: ['home'], queryFn: catalogApi.home })

export const useList = (category: string, type: string) =>
  useInfiniteQuery({
    queryKey: ['list', category, type],
    queryFn: ({ pageParam }) => catalogApi.list(category, type, pageParam),
    initialPageParam: 1,
    getNextPageParam: nextPage,
  })

export const useDiscover = (filters: DiscoverFilters) =>
  useInfiniteQuery({
    queryKey: ['discover', filters],
    queryFn: ({ pageParam }) => catalogApi.discover(filters, pageParam),
    initialPageParam: 1,
    getNextPageParam: nextPage,
  })

export const useSearch = (query: string) =>
  useInfiniteQuery({
    queryKey: ['search', query],
    queryFn: ({ pageParam }) => catalogApi.search(query, pageParam),
    initialPageParam: 1,
    getNextPageParam: nextPage,
    enabled: query.trim().length > 0,
  })

export const useGenres = (type: MediaType) =>
  useQuery({ queryKey: ['genres', type], queryFn: () => catalogApi.genres(type), staleTime: Infinity })

export const useTitle = (type: MediaType, id: number) =>
  useQuery({ queryKey: ['title', type, id], queryFn: () => catalogApi.title(type, id), enabled: Number.isFinite(id) && id > 0 })

/** Films and famous lines for the sign-in screens: public, so it works before anyone has signed in. */
export const useShowcase = () =>
  useQuery({
    queryKey: ['showcase'],
    queryFn: async () => {
      // ask for lines this browser has not been shown yet, then remember the ones that came back (lib/shownQuotes)
      const slides = await catalogApi.showcase(shownQuoteIds())
      rememberQuoteIds(slides.map((s) => s.quoteId))
      return slides
    },
    staleTime: 60 * 60_000,
    retry: 1,
  })

/**
 * The "what to watch tonight" answer for these wishes and this seed (null seed = nothing requested yet). The same
 * wishes and seed give the same answer, so it is kept for the session; a new seed is a new query.
 */
export const useTonight = (wishes: TonightWishes, seed: number | null) =>
  useQuery({
    queryKey: ['tonight', wishes, seed],
    queryFn: () => catalogApi.tonight(wishes, seed!),
    enabled: seed !== null,
    placeholderData: (previous) => previous,
  })

/** A person with biography and filmography (GET /api/people/{id}). */
export const usePerson = (id: number) =>
  useQuery({ queryKey: ['person', id], queryFn: () => catalogApi.person(id), enabled: Number.isInteger(id) && id > 0 })

/** Where a title can be watched in one country (GET /api/titles/{type}/{id}/providers). */
export const useProviders = (type: MediaType, id: number, region: string) =>
  useQuery({ queryKey: ['providers', type, id, region], queryFn: () => catalogApi.providers(type, id, region), enabled: id > 0 })

/** Fetches a title's details on demand (the hero's "Watch trailer" button needs the videos, which rows do not carry). */
export function useFetchTitle() {
  const queryClient = useQueryClient()
  return (type: MediaType, id: number) =>
    queryClient.fetchQuery({ queryKey: ['title', type, id], queryFn: () => catalogApi.title(type, id) })
}

// ---- watchlist (needs a signed-in user; pass enabled=false while signed out) ----

export const useWatchlist = (q: WatchlistQuery) =>
  useQuery({ queryKey: ['watchlist', 'list', q], queryFn: () => watchlistApi.list(q), placeholderData: (previous) => previous })

export const useWatchlistSummary = (enabled = true) =>
  useQuery({ queryKey: ['watchlist', 'summary'], queryFn: watchlistApi.summary, enabled })

export const useWatchlistEntry = (type: MediaType, id: number, enabled: boolean) =>
  useQuery({ queryKey: ['watchlist', 'entry', type, id], queryFn: () => watchlistApi.get(type, id), enabled })

/** Saving or removing changes the entry, the lists and the tab counts, so lists and summary are refetched. */
export function useSaveWatchlist(type: MediaType, id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: WatchlistEntryPayload) => watchlistApi.save(type, id, payload),
    onSuccess: (entry) => {
      queryClient.setQueryData(['watchlist', 'entry', type, id], entry)
      queryClient.invalidateQueries({ queryKey: ['watchlist'], predicate: (q) => q.queryKey[1] !== 'entry' })
    },
  })
}

/** Remove from the list page, where one hook serves many rows (the title page uses useRemoveWatchlist). */
export function useRemoveWatchlistEntry() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ type, id }: { type: MediaType; id: number }) => watchlistApi.remove(type, id),
    onSuccess: (_, { type, id }) => {
      queryClient.setQueryData(['watchlist', 'entry', type, id], null)
      queryClient.invalidateQueries({ queryKey: ['watchlist'], predicate: (q) => q.queryKey[1] !== 'entry' })
    },
  })
}

export function useRemoveWatchlist(type: MediaType, id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => watchlistApi.remove(type, id),
    onSuccess: () => {
      queryClient.setQueryData(['watchlist', 'entry', type, id], null)
      queryClient.invalidateQueries({ queryKey: ['watchlist'], predicate: (q) => q.queryKey[1] !== 'entry' })
    },
  })
}

// ---- my profile ----

export const useProfile = () => useQuery({ queryKey: ['profile'], queryFn: profileApi.get })

export function useUpdateProfile() {
  const queryClient = useQueryClient()
  return useMutation({ mutationFn: profileApi.update, onSuccess: (profile) => queryClient.setQueryData(['profile'], profile) })
}

export const useChangePassword = () => useMutation({ mutationFn: profileApi.changePassword })

export const useDeleteAccount = () => useMutation({ mutationFn: ({ password }: { password: string }) => profileApi.remove(password) })

// ---- the administrator's account list; any change makes the list reload ----

export const useAdminUsers = (query: AdminUserQuery) =>
  useQuery({ queryKey: ['admin', 'users', query], queryFn: () => adminApi.list(query), placeholderData: (previous) => previous })

function useAccountChange<TVariables, TResult>(run: (variables: TVariables) => Promise<TResult>) {
  const queryClient = useQueryClient()
  return useMutation({ mutationFn: run, onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'users'] }) })
}

export const useCreateAdminUser = () => useAccountChange(adminApi.create)
export const useUpdateAdminUser = () => useAccountChange(({ id, changes }: { id: number; changes: AdminUpdatePayload }) => adminApi.update(id, changes))
export const useDeleteAdminUser = () => useAccountChange((id: number) => adminApi.remove(id))

// ---- reviews and discussion of one title; any change reloads the summary and the list ----

export const useDiscussionSummary = (type: MediaType, id: number) =>
  useQuery({ queryKey: ['discussion', type, id, 'summary'], queryFn: () => commentApi.summary(type, id), enabled: id > 0 })

export const useComments = (type: MediaType, id: number, sort: CommentSort) =>
  useInfiniteQuery({
    queryKey: ['discussion', type, id, 'comments', sort],
    queryFn: ({ pageParam }) => commentApi.list(type, id, sort, pageParam),
    initialPageParam: 1,
    getNextPageParam: nextPage,
    enabled: id > 0,
  })

/** A write on the discussion of a title (post, reply, edit, delete, helpful): afterwards both the list and the summary are fresh. */
function useDiscussionChange<TVariables, TResult>(type: MediaType, id: number, run: (variables: TVariables) => Promise<TResult>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: run,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['discussion', type, id] })
      queryClient.invalidateQueries({ queryKey: ['moderation'] })
    },
  })
}

export const usePostComment = (type: MediaType, id: number) => useDiscussionChange(type, id, (payload: CommentPayload) => commentApi.post(type, id, payload))
export const useReplyToComment = (type: MediaType, id: number) =>
  useDiscussionChange(type, id, ({ parentId, payload }: { parentId: number; payload: CommentPayload }) => commentApi.reply(parentId, payload))
export const useEditComment = (type: MediaType, id: number) =>
  useDiscussionChange(type, id, ({ commentId, payload }: { commentId: number; payload: CommentPayload }) => commentApi.edit(commentId, payload))
export const useDeleteComment = (type: MediaType, id: number) => useDiscussionChange(type, id, (commentId: number) => commentApi.remove(commentId))
export const useToggleHelpful = (type: MediaType, id: number) => useDiscussionChange(type, id, (commentId: number) => commentApi.helpful(commentId))

// ---- the administrator's moderation queue ----

export const useModerationQueue = (status: string, page: number) =>
  useQuery({ queryKey: ['moderation', status, page], queryFn: () => moderationApi.queue(status, page), placeholderData: (previous) => previous })

export function useModerate() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, action }: { id: number; action: 'APPROVE' | 'REJECT' }) => moderationApi.decide(id, action),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['moderation'] })
      queryClient.invalidateQueries({ queryKey: ['discussion'] })
    },
  })
}

// ---- the famous-lines quiz ----

export const useStartQuiz = () => useMutation({ mutationFn: (category: QuizCategory) => quizApi.start(category) })

/** Fire-and-forget: a failed report only means no speed bonus for that question, so nothing is shown. */
export const useQuestionShown = () => useMutation({ mutationFn: ({ roundId, seq }: { roundId: number; seq: number }) => quizApi.shown(roundId, seq) })

export function useAnswerQuiz() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ roundId, seq, choice }: { roundId: number; seq: number; choice: number }) => quizApi.answer(roundId, seq, choice),
    onSuccess: (result) => {
      // a finished round may change the boards
      if (result.finished) queryClient.invalidateQueries({ queryKey: ['quiz', 'leaderboard'] })
    },
  })
}

export const useLeaderboard = (category: QuizCategory) =>
  useQuery({ queryKey: ['quiz', 'leaderboard', category], queryFn: () => quizApi.leaderboard(category), staleTime: 30_000 })

// ---- film series ----

/** The series a film belongs to (null when it is in none). Movies only: TMDB groups films, not shows, into collections. */
export const useFranchise = (filmId: number, enabled: boolean) =>
  useQuery({ queryKey: ['franchise', filmId], queryFn: () => franchiseApi.ofFilm(filmId), enabled: enabled && Number.isFinite(filmId) && filmId > 0 })

export function useAddFranchise() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (collectionId: number) => franchiseApi.addRest(collectionId),
    onSuccess: () => {
      // the list, the progress shown here and every title page's own "on my list" state have all changed
      queryClient.invalidateQueries({ queryKey: ['watchlist'] })
      queryClient.invalidateQueries({ queryKey: ['franchise'] })
    },
  })
}

// ---- episode ratings ----

/** Every episode of a show with its rating, season by season (TV only); the answer is cached for an hour on the server. */
export const useEpisodes = (showId: number) =>
  useQuery({ queryKey: ['episodes', showId], queryFn: () => catalogApi.episodes(showId), staleTime: 30 * 60_000, retry: 1 })
