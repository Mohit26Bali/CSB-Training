import { http } from './client'
import type { Agent, AuthResponse, Dashboard, Meta, QueueFilter, RegisterInput, Ticket, TicketComment, TicketDetail, TicketInput, TicketStatus } from './types'

export const authApi = {
  login: (username: string, password: string) =>
    http.post<AuthResponse>('/auth/login', { username, password }).then((r) => r.data),
  register: (input: RegisterInput) => http.post<AuthResponse>('/auth/register', input).then((r) => r.data),
}

export const ticketApi = {
  create: (input: TicketInput) => http.post<Ticket>('/tickets', input).then((r) => r.data),
  mine: (status?: TicketStatus, search?: string) => http.get<Ticket[]>('/tickets/my', { params: { status, search } }).then((r) => r.data),
  meta: () => http.get<Meta>('/tickets/meta').then((r) => r.data),
  detail: (id: number) => http.get<TicketDetail>(`/tickets/${id}`).then((r) => r.data),
  comment: (id: number, body: string, internal: boolean) =>
    http.post<TicketComment>(`/tickets/${id}/comments`, { body, internal }).then((r) => r.data),
}

export const agentApi = {
  queue: (filter: QueueFilter) => http.get<Ticket[]>('/agent/tickets', { params: filter }).then((r) => r.data),
  assign: (id: number, assignee: string) => http.put<Ticket>(`/agent/tickets/${id}/assign`, { assignee }).then((r) => r.data),
  setStatus: (id: number, status: TicketStatus, note?: string) =>
    http.put<Ticket>(`/agent/tickets/${id}/status`, { status, note }).then((r) => r.data),
  agents: () => http.get<Agent[]>('/agent/agents').then((r) => r.data),
  dashboard: () => http.get<Dashboard>('/agent/dashboard').then((r) => r.data),
}
