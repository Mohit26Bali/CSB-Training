export type Role = 'EMPLOYEE' | 'SUPPORT_AGENT'

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

export type TicketStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED'
export type PriorityName = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type SlaState = 'ON_TRACK' | 'AT_RISK' | 'BREACHED' | 'MET' | 'MISSED'

export interface Ticket {
  id: number
  number: string
  title: string
  description: string
  category: string
  priority: PriorityName
  status: TicketStatus
  requesterUsername: string
  requesterName: string
  assigneeUsername: string | null
  assigneeName: string | null
  createdAt: string
  updatedAt: string | null
  dueAt: string
  resolvedAt: string | null
  closedAt: string | null
  resolution: string | null
  slaState: SlaState
  hoursLeft: number
  allowedNext: TicketStatus[]
  commentCount: number
}

export interface TicketComment {
  id: number
  authorUsername: string
  authorName: string
  authorRole: Role
  body: string
  internal: boolean
  createdAt: string
}

export interface TicketDetail {
  ticket: Ticket
  comments: TicketComment[]
}

export interface TicketInput {
  title: string
  description: string
  category: string
  priority: string
}

export interface Meta {
  categories: string[]
  priorities: { name: PriorityName; slaHours: number }[]
}

export interface Agent {
  username: string
  fullname: string
  activeTickets: number
}

export interface Count {
  name: string
  count: number
}

export interface Dashboard {
  open: number
  inProgress: number
  resolved: number
  closed: number
  unassigned: number
  mine: number
  breached: number
  atRisk: number
  averageResolutionHours: number
  activeByPriority: Count[]
  byCategory: Count[]
}

export interface QueueFilter {
  status?: TicketStatus
  priority?: PriorityName
  assignee?: string
  search?: string
}
