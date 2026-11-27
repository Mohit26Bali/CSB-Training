import { http } from './client'
import type {
  Application,
  ApplicationFilter,
  ApplicationInput,
  AuthResponse,
  Program,
  ProgramInput,
  RegisterInput,
  Statistics,
} from './types'

export const authApi = {
  login: (username: string, password: string) =>
    http.post<AuthResponse>('/auth/login', { username, password }).then((r) => r.data),
  register: (input: RegisterInput) => http.post<AuthResponse>('/auth/register', input).then((r) => r.data),
}

export const programApi = {
  list: () => http.get<Program[]>('/programs').then((r) => r.data),
}

export const applicationApi = {
  apply: (input: ApplicationInput) => http.post<Application>('/applications', input).then((r) => r.data),
  mine: () => http.get<Application[]>('/applications/my').then((r) => r.data),
}

export const adminApi = {
  programs: () => http.get<Program[]>('/admin/programs').then((r) => r.data),
  createProgram: (input: ProgramInput) => http.post<Program>('/admin/programs', input).then((r) => r.data),
  updateProgram: (id: number, input: ProgramInput) => http.put<Program>(`/admin/programs/${id}`, input).then((r) => r.data),
  applications: (filter: ApplicationFilter) =>
    http.get<Application[]>('/admin/applications', { params: filter }).then((r) => r.data),
  approve: (id: number, reason: string) =>
    http.put<Application>(`/admin/applications/${id}/approve`, { reason }).then((r) => r.data),
  reject: (id: number, reason: string) =>
    http.put<Application>(`/admin/applications/${id}/reject`, { reason }).then((r) => r.data),
  statistics: () => http.get<Statistics>('/admin/statistics').then((r) => r.data),
}
