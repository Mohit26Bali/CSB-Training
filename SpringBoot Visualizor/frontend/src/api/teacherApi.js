import client from './client'

export async function listQuestions({ page = 0, size = 10, topicId, difficulty, active, mine, q }) {
  const params = { page, size }
  if (topicId) params.topicId = topicId
  if (difficulty) params.difficulty = difficulty
  if (active !== '' && active !== undefined) params.active = active
  if (mine) params.mine = true
  if (q) params.q = q
  const { data } = await client.get('/api/teacher/questions', { params })
  return data
}

export async function getQuestion(id) {
  const { data } = await client.get(`/api/teacher/questions/${id}`)
  return data
}

/** The exact student-facing representation (no correct flags, no explanation). */
export async function previewQuestion(id) {
  const { data } = await client.get(`/api/teacher/questions/${id}/preview`)
  return data
}

export async function createQuestion(body) {
  const { data } = await client.post('/api/teacher/questions', body)
  return data
}

export async function updateQuestion(id, body) {
  const { data } = await client.put(`/api/teacher/questions/${id}`, body)
  return data
}

export async function setQuestionActive(id, active) {
  const { data } = await client.patch(`/api/teacher/questions/${id}/active`, { active })
  return data
}
