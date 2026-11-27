export type Role = 'CUSTOMER' | 'SERVICE_ADMIN'

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

export type VehicleType = 'BIKE' | 'CAR' | 'SUV' | 'VAN'
export type BookingStatus = 'BOOKED' | 'IN_SERVICE' | 'COMPLETED' | 'CANCELLED'

export interface Vehicle {
  id: number
  number: string
  type: VehicleType
  make: string
  model: string
  year: number
  ownerName: string
  ownerUsername: string
  completedServices: number
  nextService: string | null
}

export interface VehicleInput {
  number: string
  type: string
  make: string
  model: string
  year: string | number
}

export interface ServiceType {
  id: number
  name: string
  description: string | null
  durationDays: number
  basePrice: number
  estimatedCost: number | null
  active: boolean
}

export interface ServiceInput {
  name: string
  description: string
  durationDays: string | number
  basePrice: string | number
  active: boolean
}

export interface DayLoad {
  date: string
  booked: number
  bays: number
  free: number
}

export interface Booking {
  id: number
  number: string
  vehicleId: number
  vehicleNumber: string
  vehicleLabel: string
  vehicleType: VehicleType
  customerName: string
  customerUsername: string
  serviceTypeId: number
  serviceName: string
  startDate: string
  endDate: string
  days: number
  status: BookingStatus
  estimatedCost: number
  finalCost: number | null
  notes: string | null
  workNotes: string | null
  createdAt: string
  startedAt: string | null
  startedBy: string | null
  completedAt: string | null
  completedBy: string | null
  cancelledAt: string | null
  cancelledBy: string | null
  cancelReason: string | null
  canCancel: boolean
  canStart: boolean
}

export interface BookingInput {
  vehicleId: number | ''
  serviceTypeId: number | ''
  serviceDate: string
  notes: string
}

export interface Dashboard {
  bays: number
  baysUsedToday: number
  arrivalsToday: number
  inServiceNow: number
  upcomingBooked: number
  completedThisMonth: number
  revenueThisMonth: number
  topServices: { service: string; count: number }[]
  load: DayLoad[]
}
