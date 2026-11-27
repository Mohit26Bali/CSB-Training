import { http } from './client'
import type { AuthResponse, Booking, BookingInput, BookingStatus, DayLoad, Dashboard, RegisterInput, ServiceInput, ServiceType, Vehicle, VehicleInput } from './types'

export const authApi = {
  login: (username: string, password: string) =>
    http.post<AuthResponse>('/auth/login', { username, password }).then((r) => r.data),
  register: (input: RegisterInput) => http.post<AuthResponse>('/auth/register', input).then((r) => r.data),
}

export const customerApi = {
  services: (vehicleType?: string) => http.get<ServiceType[]>('/catalog/services', { params: { vehicleType: vehicleType || undefined } }).then((r) => r.data),
  availability: (from: string, days: number) => http.get<DayLoad[]>('/catalog/availability', { params: { from, days } }).then((r) => r.data),
  vehicles: () => http.get<Vehicle[]>('/vehicles').then((r) => r.data),
  addVehicle: (input: VehicleInput) =>
    http.post<Vehicle>('/vehicles', { ...input, type: input.type || null, year: input.year === '' ? null : Number(input.year) }).then((r) => r.data),
  removeVehicle: (id: number) => http.delete(`/vehicles/${id}`).then(() => undefined),
  history: (id: number) => http.get<Booking[]>(`/vehicles/${id}/history`).then((r) => r.data),
  book: (input: BookingInput) =>
    http
      .post<Booking>('/bookings', { vehicleId: input.vehicleId || null, serviceTypeId: input.serviceTypeId || null, serviceDate: input.serviceDate || null, notes: input.notes || null })
      .then((r) => r.data),
  mine: (scope?: string) => http.get<Booking[]>('/bookings/my', { params: { scope } }).then((r) => r.data),
  cancel: (id: number, reason?: string) => http.delete<Booking>(`/bookings/${id}`, { params: { reason: reason || undefined } }).then((r) => r.data),
}

const serviceBody = (input: ServiceInput) => ({
  ...input,
  description: input.description || null,
  durationDays: input.durationDays === '' ? null : Number(input.durationDays),
  basePrice: input.basePrice === '' ? null : Number(input.basePrice),
})

export const adminApi = {
  bookings: (status?: BookingStatus, date?: string, search?: string) =>
    http.get<Booking[]>('/admin/bookings', { params: { status, date: date || undefined, search: search || undefined } }).then((r) => r.data),
  start: (id: number) => http.put<Booking>(`/admin/bookings/${id}/start`).then((r) => r.data),
  complete: (id: number, finalCost: string, workNotes: string) =>
    http.put<Booking>(`/admin/bookings/${id}/complete`, { finalCost: finalCost === '' ? null : Number(finalCost), workNotes: workNotes || null }).then((r) => r.data),
  cancel: (id: number, reason: string) => http.put<Booking>(`/admin/bookings/${id}/cancel`, { reason }).then((r) => r.data),
  vehicles: (search?: string) => http.get<Vehicle[]>('/admin/vehicles', { params: { search: search || undefined } }).then((r) => r.data),
  history: (id: number) => http.get<Booking[]>(`/admin/vehicles/${id}/history`).then((r) => r.data),
  services: () => http.get<ServiceType[]>('/admin/services').then((r) => r.data),
  createService: (input: ServiceInput) => http.post<ServiceType>('/admin/services', serviceBody(input)).then((r) => r.data),
  updateService: (id: number, input: ServiceInput) => http.put<ServiceType>(`/admin/services/${id}`, serviceBody(input)).then((r) => r.data),
  setBays: (bays: string) => http.put<{ bays: number }>('/admin/bays', { bays: bays === '' ? null : Number(bays) }).then((r) => r.data),
  dashboard: () => http.get<Dashboard>('/admin/dashboard').then((r) => r.data),
}
