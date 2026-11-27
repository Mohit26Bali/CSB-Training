import client from './client'

export async function registerRequest({ username, email, password }) {
  const { data } = await client.post('/api/auth/register', { username, email, password })
  return data
}

export async function loginRequest({ username, password }) {
  const { data } = await client.post('/api/auth/login', { username, password })
  return data // { accessToken, tokenType, expiresIn }
}

export async function fetchCurrentUser() {
  const { data } = await client.get('/api/auth/me')
  return data // { id, username, email, role }
}
