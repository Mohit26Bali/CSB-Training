import { http } from './client'
import type {
  AuthResponse,
  CategoryOption,
  Dashboard,
  Purchase,
  PurchaseInput,
  RegisterInput,
  RequestFilter,
  StatusTotal,
} from './types'

export const authApi = {
  login: (username: string, password: string) =>
    http.post<AuthResponse>('/auth/login', { username, password }).then((r) => r.data),
  register: (input: RegisterInput) => http.post<AuthResponse>('/auth/register', input).then((r) => r.data),
}

export const requestApi = {
  categories: () => http.get<CategoryOption[]>('/requests/categories').then((r) => r.data),
  submit: (input: PurchaseInput) => http.post<Purchase>('/requests', input).then((r) => r.data),
  mine: (filter: RequestFilter) => http.get<Purchase[]>('/requests/my', { params: filter }).then((r) => r.data),
  summary: () => http.get<StatusTotal[]>('/requests/my/summary').then((r) => r.data),
}

export const managerApi = {
  list: (filter: RequestFilter) => http.get<Purchase[]>('/manager/requests', { params: filter }).then((r) => r.data),
  approve: (id: number) => http.put<Purchase>(`/manager/requests/${id}/approve`).then((r) => r.data),
  reject: (id: number, reason: string) =>
    http.put<Purchase>(`/manager/requests/${id}/reject`, { reason }).then((r) => r.data),
  order: (id: number, orderReference?: string) =>
    http.put<Purchase>(`/manager/requests/${id}/order`, { orderReference }).then((r) => r.data),
  deliver: (id: number) => http.put<Purchase>(`/manager/requests/${id}/deliver`).then((r) => r.data),
  dashboard: () => http.get<Dashboard>('/manager/dashboard').then((r) => r.data),
}
