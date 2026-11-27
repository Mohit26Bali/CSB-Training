import { http } from './client'
import type {
  AuthResponse,
  CategoryOption,
  Claim,
  ClaimFilter,
  ClaimInput,
  ClaimStatus,
  Product,
  ProductInput,
  RegisterInput,
  StatusCount,
} from './types'

export const authApi = {
  login: (username: string, password: string) =>
    http.post<AuthResponse>('/auth/login', { username, password }).then((r) => r.data),
  register: (input: RegisterInput) => http.post<AuthResponse>('/auth/register', input).then((r) => r.data),
}

export const productApi = {
  categories: () => http.get<CategoryOption[]>('/products/categories').then((r) => r.data),
  register: (input: ProductInput) => http.post<Product>('/products', input).then((r) => r.data),
  mine: (search?: string) => http.get<Product[]>('/products/my', { params: { search } }).then((r) => r.data),
}

export const claimApi = {
  file: (input: ClaimInput) => http.post<Claim>('/claims', input).then((r) => r.data),
  mine: (status?: ClaimStatus) => http.get<Claim[]>('/claims/my', { params: { status } }).then((r) => r.data),
}

export const agentApi = {
  claims: (filter: ClaimFilter) => http.get<Claim[]>('/agent/claims', { params: filter }).then((r) => r.data),
  summary: () => http.get<StatusCount[]>('/agent/summary').then((r) => r.data),
  approve: (id: number) => http.put<Claim>(`/agent/claims/${id}/approve`).then((r) => r.data),
  reject: (id: number, reason: string) => http.put<Claim>(`/agent/claims/${id}/reject`, { reason }).then((r) => r.data),
  startRepair: (id: number) => http.put<Claim>(`/agent/claims/${id}/start-repair`).then((r) => r.data),
  complete: (id: number, resolutionNote?: string) =>
    http.put<Claim>(`/agent/claims/${id}/complete`, { resolutionNote }).then((r) => r.data),
}
