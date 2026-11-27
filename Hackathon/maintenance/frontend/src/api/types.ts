export type Role = 'RESIDENT' | 'FACILITY_MANAGER'

export interface AuthResponse {
  token: string
  expiresIn: number
  username: string
  fullname: string
  role: Role
}

export interface RegisterInput {
  username: string
  password: string
  fullname: string
}

export type RequestStatus = 'OPEN' | 'ASSIGNED' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED'
export type PriorityName = 'LOW' | 'MEDIUM' | 'HIGH' | 'EMERGENCY'
export type Aging = 'FRESH' | 'AGING' | 'STALE'

export interface MaintenanceRequest {
  id: number
  number: string
  category: string
  description: string
  location: string
  priority: PriorityName
  status: RequestStatus
  residentUsername: string
  residentName: string
  technicianId: number | null
  technicianName: string | null
  createdAt: string
  assignedAt: string | null
  startedAt: string | null
  resolvedAt: string | null
  closedAt: string | null
  resolutionNotes: string | null
  ageDays: number
  aging: Aging | null
  allowedNext: RequestStatus[]
}

export interface RequestInput {
  category: string
  description: string
  location: string
  priority: string
}

export interface Meta {
  categories: string[]
  priorities: PriorityName[]
}

export interface Technician {
  id: number
  name: string
  trade: string
  phone: string | null
  active: boolean
  activeJobs: number
}

export interface TechnicianInput {
  name: string
  trade: string
  phone: string
}

export interface CategoryStats {
  category: string
  total: number
  active: number
  resolved: number
}

export interface Dashboard {
  open: number
  assigned: number
  inProgress: number
  resolved: number
  closed: number
  fresh: number
  aging: number
  stale: number
  emergencies: number
  averageResolutionHours: number
  byCategory: CategoryStats[]
  technicianLoad: Technician[]
}

export interface QueueFilter {
  status?: RequestStatus
  priority?: PriorityName
  category?: string
  technicianId?: number
  aging?: Aging
  search?: string
}
