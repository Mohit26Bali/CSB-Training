export type Role = 'EMPLOYEE' | 'SECURITY_ADMIN'

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

export type VisitStatus = 'EXPECTED' | 'CHECKED_IN' | 'CHECKED_OUT' | 'CANCELLED'

export interface Visit {
  id: number
  passCode: string
  visitorName: string
  phone: string
  company: string | null
  purpose: string
  expectedDate: string
  expectedTime: string | null
  status: VisitStatus
  hostUsername: string
  hostName: string
  createdAt: string
  checkedInAt: string | null
  checkedOutAt: string | null
  checkedInBy: string | null
  checkedOutBy: string | null
  lapsed: boolean
  dueToday: boolean
  canCheckIn: boolean
  canCheckOut: boolean
  canCancel: boolean
  minutesInside: number | null
}

export interface VisitInput {
  visitorName: string
  phone: string
  company: string
  purpose: string
  expectedDate: string
  expectedTime: string
}

export interface DayCount {
  date: string
  expected: number
  arrived: number
}

export interface HostCount {
  host: string
  visitors: number
}

export interface DailyStats {
  date: string
  expectedToday: number
  notYetArrived: number
  insideNow: number
  checkedOutToday: number
  cancelledToday: number
  upcomingNextSevenDays: number
  noShowsYesterday: number
  averageVisitMinutes: number
  lastSevenDays: DayCount[]
  busiestHostsToday: HostCount[]
}
