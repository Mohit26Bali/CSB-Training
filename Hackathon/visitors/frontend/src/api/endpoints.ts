import { http } from './client'
import type { AuthResponse, DailyStats, RegisterInput, Visit, VisitInput, VisitStatus } from './types'

export const authApi = {
  login: (username: string, password: string) =>
    http.post<AuthResponse>('/auth/login', { username, password }).then((r) => r.data),
  register: (input: RegisterInput) => http.post<AuthResponse>('/auth/register', input).then((r) => r.data),
}

const body = (input: VisitInput) => ({ ...input, company: input.company || null, expectedTime: input.expectedTime || null })

export const visitorApi = {
  invite: (input: VisitInput) => http.post<Visit>('/visitors', body(input)).then((r) => r.data),
  mine: (status?: VisitStatus, search?: string) => http.get<Visit[]>('/visitors/my', { params: { status, search } }).then((r) => r.data),
  update: (id: number, input: VisitInput) => http.put<Visit>(`/visitors/${id}`, body(input)).then((r) => r.data),
  cancel: (id: number) => http.delete<Visit>(`/visitors/${id}`).then((r) => r.data),
}

export const securityApi = {
  list: (date?: string, status?: VisitStatus, search?: string) => http.get<Visit[]>('/security/visitors', { params: { date, status, search } }).then((r) => r.data),
  byPass: (code: string) => http.get<Visit>(`/security/pass/${encodeURIComponent(code)}`).then((r) => r.data),
  checkIn: (id: number) => http.put<Visit>(`/security/visitors/${id}/check-in`).then((r) => r.data),
  checkOut: (id: number) => http.put<Visit>(`/security/visitors/${id}/check-out`).then((r) => r.data),
  stats: () => http.get<DailyStats>('/security/stats').then((r) => r.data),
}
