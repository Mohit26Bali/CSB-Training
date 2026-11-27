export type Role = 'EMPLOYEE' | 'MANAGER'
export type LeaveStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'

export interface Leave {
  id: number
  employeeUsername: string
  employeeName: string
  leaveType: string
  leaveTypeLabel: string
  startDate: string
  endDate: string
  days: number
  reason: string
  status: LeaveStatus
  submittedAt: string
  decidedBy: string | null
  decidedAt: string | null
  decisionNote: string | null
}

export interface LeaveInput {
  leaveType: string
  startDate: string
  endDate: string
  reason: string
}

export interface TypeOption {
  value: string
  label: string
  tracked: boolean
}

export interface Balance {
  leaveType: string
  label: string
  tracked: boolean
  allocated: number
  used: number
  pending: number
  available: number
}

export interface LeaveFilter {
  status?: LeaveStatus
  type?: string
  search?: string
}

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
