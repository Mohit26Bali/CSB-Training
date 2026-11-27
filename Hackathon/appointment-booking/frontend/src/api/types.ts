export type Role = 'CUSTOMER' | 'SERVICE_PROVIDER'

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

export type AppointmentStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED'

export interface Provider {
  id: number
  name: string
  title: string
  description: string | null
  location: string | null
  freeSlots: number
  nextFree: string | null
}

export interface ProfileInput {
  title: string
  description: string
  location: string
}

/** status is AVAILABLE or the status of the live appointment holding the slot. */
export interface Slot {
  id: number
  providerId: number
  providerName: string
  start: string
  end: string
  minutes: number
  status: 'AVAILABLE' | 'PENDING' | 'CONFIRMED'
  appointmentId: number | null
  customerName: string | null
  reason: string | null
}

export interface SlotInput {
  date: string
  startTime: string
  minutes: string | number
}

export interface BulkInput {
  date: string
  fromTime: string
  toTime: string
  minutes: string | number
}

export interface Appointment {
  id: number
  number: string
  slotId: number
  providerId: number
  providerName: string
  providerTitle: string
  location: string | null
  customerName: string
  customerUsername: string
  start: string
  end: string
  minutes: number
  status: AppointmentStatus
  reason: string | null
  createdAt: string
  confirmedAt: string | null
  confirmedBy: string | null
  cancelledAt: string | null
  cancelledBy: string | null
  cancelReason: string | null
  phase: 'UPCOMING' | 'NOW' | 'PAST'
  canCancel: boolean
  canConfirm: boolean
}

export interface Dashboard {
  todayAppointments: number
  pending: number
  upcomingConfirmed: number
  freeSlotsWeek: number
  bookedSlotsWeek: number
  utilisationPercent: number
  days: { date: string; slots: number; booked: number }[]
  next: Appointment[]
}
