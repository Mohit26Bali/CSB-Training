import { http } from './client'
import type { AuthResponse, Dashboard, MaintenanceRequest, Meta, QueueFilter, RegisterInput, RequestInput, RequestStatus, Technician, TechnicianInput } from './types'

export const authApi = {
  login: (username: string, password: string) =>
    http.post<AuthResponse>('/auth/login', { username, password }).then((r) => r.data),
  register: (input: RegisterInput) => http.post<AuthResponse>('/auth/register', input).then((r) => r.data),
}

export const requestApi = {
  raise: (input: RequestInput) => http.post<MaintenanceRequest>('/requests', input).then((r) => r.data),
  mine: (status?: RequestStatus, search?: string) => http.get<MaintenanceRequest[]>('/requests/my', { params: { status, search } }).then((r) => r.data),
  meta: () => http.get<Meta>('/requests/meta').then((r) => r.data),
}

export const managerApi = {
  queue: (filter: QueueFilter) => http.get<MaintenanceRequest[]>('/manager/requests', { params: filter }).then((r) => r.data),
  assign: (id: number, technicianId: number) => http.put<MaintenanceRequest>(`/manager/requests/${id}/assign`, { technicianId }).then((r) => r.data),
  setStatus: (id: number, status: RequestStatus, notes?: string) =>
    http.put<MaintenanceRequest>(`/manager/requests/${id}/status`, { status, notes }).then((r) => r.data),
  technicians: () => http.get<Technician[]>('/manager/technicians').then((r) => r.data),
  addTechnician: (input: TechnicianInput) => http.post<Technician>('/manager/technicians', input).then((r) => r.data),
  setActive: (id: number, value: boolean) => http.put<Technician>(`/manager/technicians/${id}/active`, null, { params: { value } }).then((r) => r.data),
  dashboard: () => http.get<Dashboard>('/manager/dashboard').then((r) => r.data),
}
