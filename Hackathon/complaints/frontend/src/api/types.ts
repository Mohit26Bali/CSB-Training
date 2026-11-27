export type Role = 'CUSTOMER' | 'SUPPORT_AGENT'

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

export type ComplaintStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED'
export type PriorityName = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'

export interface Complaint {
  id: number
  number: string
  subject: string
  description: string
  category: string
  priority: PriorityName
  status: ComplaintStatus
  customerUsername: string
  customerName: string
  assigneeUsername: string | null
  assigneeName: string | null
  createdAt: string
  updatedAt: string | null
  resolvedAt: string | null
  closedAt: string | null
  resolution: string | null
  rating: number | null
  feedback: string | null
  ageDays: number
  allowedNext: ComplaintStatus[]
  canRate: boolean
}

export interface TimelineEvent {
  id: number
  type: 'FILED' | 'ASSIGNED' | 'STATUS_CHANGED' | 'FEEDBACK'
  status: ComplaintStatus | null
  note: string | null
  actorName: string
  actorRole: Role
  createdAt: string
}

export interface ComplaintDetail {
  complaint: Complaint
  timeline: TimelineEvent[]
}

export interface ComplaintInput {
  subject: string
  description: string
  category: string
  priority: string
}

export interface Meta {
  categories: string[]
  priorities: PriorityName[]
}

export interface Agent {
  username: string
  fullname: string
  activeComplaints: number
}

export interface Count {
  name: string
  count: number
}

export interface DayCount {
  date: string
  filed: number
  resolved: number
}

export interface Statistics {
  total: number
  open: number
  inProgress: number
  resolved: number
  closed: number
  unassigned: number
  mine: number
  waitingOverThreeDays: number
  resolutionRatePercent: number
  averageResolutionHours: number
  averageRating: number
  ratings: number
  byCategory: Count[]
  activeByPriority: Count[]
  lastSevenDays: DayCount[]
}

export interface QueueFilter {
  status?: ComplaintStatus
  priority?: PriorityName
  category?: string
  assignee?: string
  search?: string
}
