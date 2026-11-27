import { http } from './client'
import type { AuthResponse, Balance, Leave, LeaveFilter, LeaveInput, RegisterInput, TypeOption } from './types'

export const authApi = {
  login: (username: string, password: string) =>
    http.post<AuthResponse>('/auth/login', { username, password }).then((r) => r.data),
  register: (input: RegisterInput) => http.post<AuthResponse>('/auth/register', input).then((r) => r.data),
}

export const leaveApi = {
  types: () => http.get<TypeOption[]>('/leave/types').then((r) => r.data),
  balance: () => http.get<Balance[]>('/leave/balance').then((r) => r.data),
  submit: (input: LeaveInput) => http.post<Leave>('/leave', input).then((r) => r.data),
  mine: (filter: LeaveFilter) => http.get<Leave[]>('/leave/my', { params: filter }).then((r) => r.data),
  cancel: (id: number) => http.delete<Leave>(`/leave/${id}`).then((r) => r.data),
}

export const managerApi = {
  list: (filter: LeaveFilter) => http.get<Leave[]>('/manager/leave', { params: filter }).then((r) => r.data),
  approve: (id: number, note?: string) => http.put<Leave>(`/manager/leave/${id}/approve`, { note }).then((r) => r.data),
  reject: (id: number, note?: string) => http.put<Leave>(`/manager/leave/${id}/reject`, { note }).then((r) => r.data),
  calendar: (month: string) => http.get<Leave[]>('/manager/leave/calendar', { params: { month } }).then((r) => r.data),
}
