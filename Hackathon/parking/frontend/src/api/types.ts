export type Role = 'EMPLOYEE' | 'PARKING_ADMIN'

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

export type SlotStatus = 'AVAILABLE' | 'RESERVED' | 'MAINTENANCE'
export type RequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'

export interface Slot {
  id: number
  code: string
  floor: number
  status: SlotStatus
  occupied: boolean
  occupantName: string | null
  occupantVehicle: string | null
}

export interface SlotInput {
  code: string
  floor: number | ''
  status: string
}

export interface ParkingRequest {
  id: number
  employeeUsername: string
  employeeName: string
  vehicleNumber: string
  preferredFloor: number | null
  note: string | null
  status: RequestStatus
  queuePosition: number | null
  createdAt: string
  decidedBy: string | null
  decidedAt: string | null
  decisionReason: string | null
  slotCode: string | null
}

export interface Allocation {
  id: number
  slotId: number
  slotCode: string
  floor: number
  employeeUsername: string
  employeeName: string
  vehicleNumber: string
  allocatedAt: string
  allocatedBy: string
  releasedAt: string | null
  releasedBy: string | null
  active: boolean
}

export interface FloorStats {
  floor: number
  slots: number
  occupied: number
  free: number
}

export interface Dashboard {
  slots: number
  occupied: number
  free: number
  reserved: number
  maintenance: number
  occupancyPercent: number
  pendingRequests: number
  byFloor: FloorStats[]
}
