import { http } from './client'
import type { Appointment, AppointmentStatus, AuthResponse, BulkInput, Dashboard, ProfileInput, Provider, RegisterInput, Slot, SlotInput } from './types'

export const authApi = {
  login: (username: string, password: string) =>
    http.post<AuthResponse>('/auth/login', { username, password }).then((r) => r.data),
  register: (input: RegisterInput) => http.post<AuthResponse>('/auth/register', input).then((r) => r.data),
}

export const customerApi = {
  providers: (search?: string) => http.get<Provider[]>('/providers', { params: { search: search || undefined } }).then((r) => r.data),
  slots: (providerId: number, from: string, days: number) =>
    http.get<Slot[]>(`/providers/${providerId}/slots`, { params: { from, days } }).then((r) => r.data),
  book: (slotId: number, reason: string) => http.post<Appointment>('/appointments', { slotId, reason: reason || null }).then((r) => r.data),
  mine: (scope?: string) => http.get<Appointment[]>('/appointments/my', { params: { scope } }).then((r) => r.data),
  cancel: (id: number, reason?: string) => http.delete<Appointment>(`/appointments/${id}`, { params: { reason: reason || undefined } }).then((r) => r.data),
}

export const providerApi = {
  slots: (from: string, days: number) => http.get<Slot[]>('/provider/slots', { params: { from, days } }).then((r) => r.data),
  createSlot: (input: SlotInput) =>
    http.post<Slot>('/provider/slots', { ...input, date: input.date || null, startTime: input.startTime || null, minutes: input.minutes === '' ? null : Number(input.minutes) }).then((r) => r.data),
  createBulk: (input: BulkInput) =>
    http
      .post<Slot[]>('/provider/slots/bulk', {
        date: input.date || null,
        fromTime: input.fromTime || null,
        toTime: input.toTime || null,
        minutes: input.minutes === '' ? null : Number(input.minutes),
      })
      .then((r) => r.data),
  closeSlot: (id: number) => http.delete(`/provider/slots/${id}`).then(() => undefined),
  appointments: (status?: AppointmentStatus, date?: string, search?: string) =>
    http.get<Appointment[]>('/provider/appointments', { params: { status, date: date || undefined, search: search || undefined } }).then((r) => r.data),
  confirm: (id: number) => http.put<Appointment>(`/provider/appointments/${id}/confirm`).then((r) => r.data),
  cancel: (id: number, reason: string) => http.put<Appointment>(`/provider/appointments/${id}/cancel`, { reason }).then((r) => r.data),
  profile: () => http.get<Provider>('/provider/profile').then((r) => r.data),
  updateProfile: (input: ProfileInput) => http.put<Provider>('/provider/profile', input).then((r) => r.data),
  dashboard: () => http.get<Dashboard>('/provider/dashboard').then((r) => r.data),
}
