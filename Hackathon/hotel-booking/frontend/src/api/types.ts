export type Role = 'CUSTOMER' | 'HOTEL_ADMIN'

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

export type RoomType = 'STANDARD' | 'DELUXE' | 'SUITE' | 'FAMILY'
export type BookingStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED'

export interface Room {
  id: number
  number: string
  type: RoomType
  floor: number
  pricePerNight: number
  capacity: number
  description: string | null
  amenities: string[]
  active: boolean
  nights: number | null
  totalPrice: number | null
}

export interface RoomInput {
  number: string
  type: string
  floor: number | ''
  pricePerNight: number | ''
  capacity: number | ''
  description: string
  amenities: string
  active: boolean
}

export interface Booking {
  id: number
  number: string
  roomId: number
  roomNumber: string
  roomType: RoomType
  checkIn: string
  checkOut: string
  nights: number
  guests: number
  totalPrice: number
  specialRequests: string | null
  status: BookingStatus
  phase: 'UPCOMING' | 'IN_STAY' | 'PAST' | null
  customerUsername: string
  customerName: string
  createdAt: string
  confirmedAt: string | null
  confirmedBy: string | null
  cancelledAt: string | null
  cancelledBy: string | null
  cancelReason: string | null
  canCancel: boolean
  autoCancelled: number
}

export interface BookingInput {
  roomId: number
  checkIn: string
  checkOut: string
  guests: number
  specialRequests: string
}

export interface Span {
  bookingId: number
  number: string
  status: BookingStatus
  checkIn: string
  checkOut: string
  customerName: string
}

export interface RoomCalendar {
  roomId: number
  number: string
  type: RoomType
  active: boolean
  bookings: Span[]
}

export interface Calendar {
  month: string
  first: string
  days: number
  rooms: RoomCalendar[]
}

export interface TypeStats {
  type: RoomType
  rooms: number
  occupiedTonight: number
  averagePrice: number
}

export interface Dashboard {
  rooms: number
  activeRooms: number
  occupiedTonight: number
  occupancyPercent: number
  arrivalsToday: number
  departuresToday: number
  pendingBookings: number
  revenueThisMonth: number
  byType: TypeStats[]
  nextArrivals: Booking[]
}
