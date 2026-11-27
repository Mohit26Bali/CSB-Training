import { http } from './client'
import type { Allocation, AuthResponse, Dashboard, ParkingRequest, RegisterInput, RequestStatus, Slot, SlotInput } from './types'

export const authApi = {
  login: (username: string, password: string) =>
    http.post<AuthResponse>('/auth/login', { username, password }).then((r) => r.data),
  register: (input: RegisterInput) => http.post<AuthResponse>('/auth/register', input).then((r) => r.data),
}

export const employeeApi = {
  freeSlots: (floor?: number) => http.get<Slot[]>('/slots', { params: { floor } }).then((r) => r.data),
  floors: () => http.get<number[]>('/slots/floors').then((r) => r.data),
  ask: (vehicleNumber: string, preferredFloor: number | '', note: string) =>
    http.post<ParkingRequest>('/parking/requests', { vehicleNumber, preferredFloor: preferredFloor === '' ? null : Number(preferredFloor), note }).then((r) => r.data),
  myRequests: () => http.get<ParkingRequest[]>('/parking/requests/my').then((r) => r.data),
  cancel: (id: number) => http.delete<ParkingRequest>(`/parking/requests/${id}`).then((r) => r.data),
  /** 204 (no slot yet) comes back as an empty string */
  allocation: () => http.get<Allocation | ''>('/parking/allocation/my').then((r) => (r.data === '' ? null : r.data)),
}

const slotBody = (input: SlotInput) => ({ ...input, floor: input.floor === '' ? null : Number(input.floor) })

export const adminApi = {
  slots: (floor?: number) => http.get<Slot[]>('/admin/slots', { params: { floor } }).then((r) => r.data),
  createSlot: (input: SlotInput) => http.post<Slot>('/admin/slots', slotBody(input)).then((r) => r.data),
  updateSlot: (id: number, input: SlotInput) => http.put<Slot>(`/admin/slots/${id}`, slotBody(input)).then((r) => r.data),
  requests: (status?: RequestStatus) => http.get<ParkingRequest[]>('/admin/requests', { params: { status } }).then((r) => r.data),
  approve: (id: number, slotId: number) => http.put<ParkingRequest>(`/admin/requests/${id}/approve`, { slotId }).then((r) => r.data),
  reject: (id: number, reason: string) => http.put<ParkingRequest>(`/admin/requests/${id}/reject`, { reason }).then((r) => r.data),
  allocations: (activeOnly: boolean) => http.get<Allocation[]>('/admin/allocations', { params: { activeOnly } }).then((r) => r.data),
  release: (id: number) => http.put<Allocation>(`/admin/allocations/${id}/release`).then((r) => r.data),
  dashboard: () => http.get<Dashboard>('/admin/dashboard').then((r) => r.data),
}
