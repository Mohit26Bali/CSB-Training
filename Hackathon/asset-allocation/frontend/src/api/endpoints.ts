import { http } from './client'
import type { Asset, AssetInput, AssetStatus, AuthResponse, Assignment, Dashboard, Employee, RegisterInput } from './types'

export const authApi = {
  login: (username: string, password: string) =>
    http.post<AuthResponse>('/auth/login', { username, password }).then((r) => r.data),
  register: (input: RegisterInput) => http.post<AuthResponse>('/auth/register', input).then((r) => r.data),
}

export const metaApi = {
  meta: () => http.get<{ categories: string[]; conditions: string[] }>('/assets/meta').then((r) => r.data),
}

export const employeeApi = {
  mine: () => http.get<Assignment[]>('/my/assets').then((r) => r.data),
  history: () => http.get<Assignment[]>('/my/history').then((r) => r.data),
  requestReturn: (id: number, note: string) => http.post<Assignment>(`/my/assignments/${id}/return`, { note: note || null }).then((r) => r.data),
}

const assetBody = (input: AssetInput) => ({
  ...input,
  category: input.category || null,
  condition: input.condition || null,
  brand: input.brand || null,
  model: input.model || null,
  notes: input.notes || null,
  purchaseDate: input.purchaseDate || null,
  purchasePrice: input.purchasePrice === '' ? null : Number(input.purchasePrice),
})

export const adminApi = {
  assets: (status?: string, category?: string, search?: string) =>
    http.get<Asset[]>('/admin/assets', { params: { status: status || undefined, category: category || undefined, search: search || undefined } }).then((r) => r.data),
  createAsset: (input: AssetInput) => http.post<Asset>('/admin/assets', assetBody(input)).then((r) => r.data),
  updateAsset: (id: number, input: AssetInput) => http.put<Asset>(`/admin/assets/${id}`, assetBody(input)).then((r) => r.data),
  setStatus: (id: number, status: AssetStatus) => http.put<Asset>(`/admin/assets/${id}/status`, { status }).then((r) => r.data),
  assign: (id: number, employeeUsername: string, dueDate: string, note: string) =>
    http.post<Assignment>(`/admin/assets/${id}/assign`, { employeeUsername: employeeUsername || null, dueDate: dueDate || null, note: note || null }).then((r) => r.data),
  history: (id: number) => http.get<Assignment[]>(`/admin/assets/${id}/history`).then((r) => r.data),
  assignments: (status?: string) => http.get<Assignment[]>('/admin/assignments', { params: { status } }).then((r) => r.data),
  accept: (id: number, condition: string, note: string) =>
    http.put<Assignment>(`/admin/assignments/${id}/accept`, { condition: condition || null, note: note || null }).then((r) => r.data),
  employees: (search?: string) => http.get<Employee[]>('/admin/employees', { params: { search: search || undefined } }).then((r) => r.data),
  dashboard: () => http.get<Dashboard>('/admin/dashboard').then((r) => r.data),
}
