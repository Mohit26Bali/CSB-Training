import client from './client'

export async function listTopics() {
  const { data } = await client.get('/api/topics')
  return data // [{ id, name, slug, description, activeQuestionCount }]
}

export async function startChallenge({ topic, difficulty, count }) {
  const body = {}
  if (topic) body.topic = topic
  if (difficulty) body.difficulty = difficulty
  if (count) body.count = count
  const { data } = await client.post('/api/challenges', body)
  return data // attempt view: questions WITHOUT correct answers
}

export async function getChallenge(id) {
  const { data } = await client.get(`/api/challenges/${id}`)
  return data
}

export async function submitChallenge(id, answers) {
  const { data } = await client.post(`/api/challenges/${id}/submit`, { answers })
  return data
}

export async function getChallengeResult(id) {
  const { data } = await client.get(`/api/challenges/${id}/result`)
  return data
}

export async function listMyChallenges({ page = 0, size = 5 } = {}) {
  const { data } = await client.get('/api/challenges/me', { params: { page, size } })
  return data
}

export async function getMyProgress() {
  const { data } = await client.get('/api/progress/me')
  return data
}

export async function getLeaderboard({ topic, limit = 10 } = {}) {
  const params = { limit }
  if (topic) params.topic = topic
  const { data } = await client.get('/api/leaderboard', { params })
  return data
}
