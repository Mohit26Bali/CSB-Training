import { http } from './client'
import type { AuthResponse, Dashboard, EventInput, EventItem, EventStatus, Registration, RegisterInput } from './types'

export const authApi = {
  login: (username: string, password: string) =>
    http.post<AuthResponse>('/auth/login', { username, password }).then((r) => r.data),
  register: (input: RegisterInput) => http.post<AuthResponse>('/auth/register', input).then((r) => r.data),
}

export const eventApi = {
  upcoming: (search?: string, category?: string) =>
    http.get<EventItem[]>('/events', { params: { search, category } }).then((r) => r.data),
  categories: () => http.get<string[]>('/events/categories').then((r) => r.data),
}

export const registrationApi = {
  register: (eventId: number, joinWaitlist: boolean) =>
    http.post<Registration>('/registrations', { eventId, joinWaitlist }).then((r) => r.data),
  mine: () => http.get<Registration[]>('/registrations/my').then((r) => r.data),
  cancel: (id: number) => http.delete<Registration>(`/registrations/${id}`).then((r) => r.data),
}

/** The form keeps the datetime-local text; the server wants seconds-free ISO, which that already is. */
const body = (input: EventInput) => ({ ...input, capacity: input.capacity === '' ? null : Number(input.capacity) })

export const adminApi = {
  events: (status?: EventStatus) => http.get<EventItem[]>('/admin/events', { params: { status } }).then((r) => r.data),
  create: (input: EventInput) => http.post<EventItem>('/admin/events', body(input)).then((r) => r.data),
  update: (id: number, input: EventInput) => http.put<EventItem>(`/admin/events/${id}`, body(input)).then((r) => r.data),
  publish: (id: number) => http.put<EventItem>(`/admin/events/${id}/publish`).then((r) => r.data),
  cancel: (id: number) => http.put<EventItem>(`/admin/events/${id}/cancel`).then((r) => r.data),
  participants: (id: number) => http.get<Registration[]>(`/admin/events/${id}/participants`).then((r) => r.data),
  dashboard: () => http.get<Dashboard>('/admin/dashboard').then((r) => r.data),
}
