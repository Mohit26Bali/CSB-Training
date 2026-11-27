import { http } from './client'
import type { AuthResponse, Booking, BookingInput, BookingStatus, Calendar, Dashboard, RegisterInput, Room, RoomInput } from './types'

export const authApi = {
  login: (username: string, password: string) =>
    http.post<AuthResponse>('/auth/login', { username, password }).then((r) => r.data),
  register: (input: RegisterInput) => http.post<AuthResponse>('/auth/register', input).then((r) => r.data),
}

export const guestApi = {
  available: (checkIn: string, checkOut: string, type?: string, guests?: number) =>
    http.get<Room[]>('/rooms/available', { params: { checkIn, checkOut, type: type || undefined, guests } }).then((r) => r.data),
  types: () => http.get<string[]>('/rooms/types').then((r) => r.data),
  book: (input: BookingInput) => http.post<Booking>('/bookings', { ...input, specialRequests: input.specialRequests || null }).then((r) => r.data),
  mine: (status?: BookingStatus) => http.get<Booking[]>('/bookings/my', { params: { status } }).then((r) => r.data),
  cancel: (id: number, reason?: string) => http.delete<Booking>(`/bookings/${id}`, { params: { reason: reason || undefined } }).then((r) => r.data),
}

const roomBody = (input: RoomInput) => ({
  ...input,
  floor: input.floor === '' ? null : Number(input.floor),
  pricePerNight: input.pricePerNight === '' ? null : Number(input.pricePerNight),
  capacity: input.capacity === '' ? null : Number(input.capacity),
})

export const adminApi = {
  rooms: (type?: string) => http.get<Room[]>('/admin/rooms', { params: { type: type || undefined } }).then((r) => r.data),
  createRoom: (input: RoomInput) => http.post<Room>('/admin/rooms', roomBody(input)).then((r) => r.data),
  updateRoom: (id: number, input: RoomInput) => http.put<Room>(`/admin/rooms/${id}`, roomBody(input)).then((r) => r.data),
  bookings: (status?: BookingStatus, roomId?: number, search?: string) =>
    http.get<Booking[]>('/admin/bookings', { params: { status, roomId, search: search || undefined } }).then((r) => r.data),
  confirm: (id: number) => http.put<Booking>(`/admin/bookings/${id}/confirm`).then((r) => r.data),
  cancel: (id: number, reason: string) => http.put<Booking>(`/admin/bookings/${id}/cancel`, { reason }).then((r) => r.data),
  calendar: (month: string) => http.get<Calendar>('/admin/calendar', { params: { month } }).then((r) => r.data),
  dashboard: () => http.get<Dashboard>('/admin/dashboard').then((r) => r.data),
}
