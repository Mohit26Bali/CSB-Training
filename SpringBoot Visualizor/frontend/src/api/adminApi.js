import client from './client'

export async function listUsers({ page = 0, size = 10, role, q, sort = 'username,asc' }) {
  const params = { page, size, sort }
  if (role) params.role = role
  if (q) params.q = q
  const { data } = await client.get('/api/admin/users', { params })
  return data // { content, page, size, totalElements, totalPages }
}

export async function updateUserRole(id, role) {
  const { data } = await client.patch(`/api/admin/users/${id}/role`, { role })
  return data
}

export async function updateUserEnabled(id, enabled) {
  const { data } = await client.patch(`/api/admin/users/${id}/enabled`, { enabled })
  return data
}
