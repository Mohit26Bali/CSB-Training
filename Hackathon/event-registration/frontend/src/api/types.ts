export type Role = 'EMPLOYEE' | 'EVENT_ADMIN'

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

export type EventStatus = 'DRAFT' | 'PUBLISHED' | 'CANCELLED'
export type RegistrationStatus = 'CONFIRMED' | 'WAITLISTED' | 'CANCELLED'

export interface EventItem {
  id: number
  title: string
  category: string
  venue: string
  description: string
  startsAt: string
  endsAt: string
  capacity: number
  status: EventStatus
  confirmed: number
  waitlisted: number
  spotsLeft: number
  past: boolean
  myStatus: RegistrationStatus | null
  myRegistrationId: number | null
}

export interface EventInput {
  title: string
  category: string
  venue: string
  description: string
  startsAt: string
  endsAt: string
  capacity: number | ''
}

export interface Registration {
  id: number
  eventId: number
  eventTitle: string
  eventCategory: string
  venue: string
  startsAt: string
  eventStatus: EventStatus
  username: string
  fullname: string
  status: RegistrationStatus
  waitlistPosition: number | null
  registeredAt: string
}

export interface EventStats {
  id: number
  title: string
  category: string
  startsAt: string
  status: EventStatus
  capacity: number
  confirmed: number
  waitlisted: number
  fillPercent: number
}

export interface Dashboard {
  totalEvents: number
  upcomingPublished: number
  confirmedSeats: number
  waitlisted: number
  events: EventStats[]
}
