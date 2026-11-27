import { http } from './client'
import type { Allocation, AuthResponse, Dashboard, RegisterInput, RequestStatus, Room, RoomInput, RoomRequest } from './types'

export const authApi = {
  login: (username: string, password: string) =>
    http.post<AuthResponse>('/auth/login', { username, password }).then((r) => r.data),
  register: (input: RegisterInput) => http.post<AuthResponse>('/auth/register', input).then((r) => r.data),
}

export const studentApi = {
  rooms: (type?: string) => http.get<Room[]>('/rooms', { params: { type } }).then((r) => r.data),
  types: () => http.get<string[]>('/rooms/types').then((r) => r.data),
  ask: (preferredType: string, note: string) =>
    http.post<RoomRequest>('/requests', { preferredType: preferredType || null, note }).then((r) => r.data),
  myRequests: () => http.get<RoomRequest[]>('/requests/my').then((r) => r.data),
  cancel: (id: number) => http.delete<RoomRequest>(`/requests/${id}`).then((r) => r.data),
  /** 204 (no room yet) comes back as an empty string */
  allocation: () => http.get<Allocation | ''>('/allocation/my').then((r) => (r.data === '' ? null : r.data)),
}

const roomBody = (input: RoomInput) => ({
  ...input,
  floor: input.floor === '' ? null : Number(input.floor),
  capacity: input.capacity === '' ? null : Number(input.capacity),
})

export const adminApi = {
  rooms: (type?: string) => http.get<Room[]>('/admin/rooms', { params: { type } }).then((r) => r.data),
  createRoom: (input: RoomInput) => http.post<Room>('/admin/rooms', roomBody(input)).then((r) => r.data),
  updateRoom: (id: number, input: RoomInput) => http.put<Room>(`/admin/rooms/${id}`, roomBody(input)).then((r) => r.data),
  requests: (status?: RequestStatus) => http.get<RoomRequest[]>('/admin/requests', { params: { status } }).then((r) => r.data),
  approve: (id: number, roomId: number) => http.put<RoomRequest>(`/admin/requests/${id}/approve`, { roomId }).then((r) => r.data),
  reject: (id: number, reason: string) => http.put<RoomRequest>(`/admin/requests/${id}/reject`, { reason }).then((r) => r.data),
  allocations: (activeOnly: boolean) => http.get<Allocation[]>('/admin/allocations', { params: { activeOnly } }).then((r) => r.data),
  vacate: (id: number) => http.put<Allocation>(`/admin/allocations/${id}/vacate`).then((r) => r.data),
  dashboard: () => http.get<Dashboard>('/admin/dashboard').then((r) => r.data),
}
