export type Role = 'STUDENT' | 'HOSTEL_ADMIN'

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

export type RoomType = 'SINGLE' | 'DOUBLE' | 'TRIPLE' | 'DORMITORY'
export type RequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'

export interface Room {
  id: number
  number: string
  floor: number
  type: RoomType
  capacity: number
  occupied: number
  bedsLeft: number
  active: boolean
  occupants: string[]
}

export interface RoomInput {
  number: string
  floor: number | ''
  type: string
  capacity: number | ''
  active: boolean
}

export interface RoomRequest {
  id: number
  studentUsername: string
  studentName: string
  preferredType: RoomType | null
  note: string | null
  status: RequestStatus
  queuePosition: number | null
  createdAt: string
  decidedBy: string | null
  decidedAt: string | null
  decisionReason: string | null
  roomNumber: string | null
}

export interface Allocation {
  id: number
  roomId: number
  roomNumber: string
  floor: number
  roomType: RoomType
  studentUsername: string
  studentName: string
  allocatedAt: string
  allocatedBy: string
  vacatedAt: string | null
  active: boolean
}

export interface TypeOccupancy {
  type: RoomType
  rooms: number
  beds: number
  occupied: number
}

export interface Dashboard {
  rooms: number
  beds: number
  occupied: number
  free: number
  occupancyPercent: number
  pendingRequests: number
  fullRooms: number
  byType: TypeOccupancy[]
}
