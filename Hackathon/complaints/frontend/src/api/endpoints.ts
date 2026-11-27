import { http } from './client'
import type { Agent, AuthResponse, Complaint, ComplaintDetail, ComplaintInput, ComplaintStatus, Meta, QueueFilter, RegisterInput, Statistics } from './types'

export const authApi = {
  login: (username: string, password: string) =>
    http.post<AuthResponse>('/auth/login', { username, password }).then((r) => r.data),
  register: (input: RegisterInput) => http.post<AuthResponse>('/auth/register', input).then((r) => r.data),
}

export const complaintApi = {
  file: (input: ComplaintInput) => http.post<Complaint>('/complaints', input).then((r) => r.data),
  mine: (status?: ComplaintStatus, search?: string) => http.get<Complaint[]>('/complaints/my', { params: { status, search } }).then((r) => r.data),
  meta: () => http.get<Meta>('/complaints/meta').then((r) => r.data),
  detail: (id: number) => http.get<ComplaintDetail>(`/complaints/${id}`).then((r) => r.data),
  feedback: (id: number, rating: number, comment: string) => http.post<Complaint>(`/complaints/${id}/feedback`, { rating, comment }).then((r) => r.data),
}

export const agentApi = {
  queue: (filter: QueueFilter) => http.get<Complaint[]>('/agent/complaints', { params: filter }).then((r) => r.data),
  assign: (id: number, assignee: string) => http.put<Complaint>(`/agent/complaints/${id}/assign`, { assignee }).then((r) => r.data),
  setStatus: (id: number, status: ComplaintStatus, note?: string) =>
    http.put<Complaint>(`/agent/complaints/${id}/status`, { status, note }).then((r) => r.data),
  agents: () => http.get<Agent[]>('/agent/agents').then((r) => r.data),
  statistics: () => http.get<Statistics>('/agent/statistics').then((r) => r.data),
}
