import client from '../api/client'

/**
 * The coding-practice API (`/api/coding`). Run and Submit are SYNCHRONOUS on the server: the response arrives when judging has finished
 * (normally a few seconds, up to about 10 s longer when the runner was idle, and at most the server's hard timeout plus a margin). The
 * client therefore waits for it; it never polls and never invents a result. The only thing sent for a run is the source code: the problem
 * is the slug in the URL, and the tests are the server's.
 */

/** Longer than the server's own limit (60 s hard timeout + margin), so a slow answer is not mistaken for a failure. */
export const JUDGING_TIMEOUT_MS = 150_000

const slugPath = (slug) => `/api/coding/problems/${encodeURIComponent(slug)}`

export const getCodingTopics = () => client.get('/api/coding/topics').then((r) => r.data)

export const listCodingProblems = () => client.get('/api/coding/problems').then((r) => r.data)

export const getCodingProblem = (slug) => client.get(slugPath(slug)).then((r) => r.data)

/** Sample tests only. Stores nothing and changes no progress. */
export const runCode = (slug, sourceCode, signal) =>
  client.post(`${slugPath(slug)}/run`, { sourceCode }, { timeout: JUDGING_TIMEOUT_MS, signal }).then((r) => r.data)

/** The full test suite, hidden tests included. The server stores the submission and updates progress. */
export const submitCode = (slug, sourceCode, signal) =>
  client.post(`${slugPath(slug)}/submit`, { sourceCode }, { timeout: JUDGING_TIMEOUT_MS, signal }).then((r) => r.data)

export const listSubmissions = (slug) => client.get(`${slugPath(slug)}/submissions`).then((r) => r.data)

export const getSubmission = (id) => client.get(`/api/coding/submissions/${encodeURIComponent(id)}`).then((r) => r.data)

export const getCodingProgress = () => client.get('/api/coding/progress/me').then((r) => r.data)
