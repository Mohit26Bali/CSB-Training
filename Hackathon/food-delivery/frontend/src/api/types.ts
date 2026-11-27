export type Role = 'CUSTOMER' | 'RESTAURANT_ADMIN'

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

export type MenuCategory = 'STARTERS' | 'MAINS' | 'BREADS_AND_RICE' | 'DESSERTS' | 'DRINKS'
export type OrderStatus = 'PLACED' | 'ACCEPTED' | 'PREPARING' | 'READY' | 'DELIVERED' | 'CANCELLED'

export interface MenuItem {
  id: number
  name: string
  description: string | null
  category: MenuCategory
  price: number
  vegetarian: boolean
  active: boolean
  imageKey: string
}

export interface MenuInput {
  name: string
  description: string
  category: string
  price: string | number
  vegetarian: boolean
  active: boolean
}

export interface OrderLine {
  menuItemId: number
  name: string
  imageKey: string
  unitPrice: number
  quantity: number
  lineTotal: number
}

export interface Order {
  id: number
  number: string
  status: OrderStatus
  nextStatus: OrderStatus | null
  lines: OrderLine[]
  itemCount: number
  subtotal: number
  tax: number
  deliveryFee: number
  total: number
  customerName: string
  customerUsername: string
  address: string
  phone: string
  notes: string | null
  createdAt: string
  acceptedAt: string | null
  acceptedBy: string | null
  preparingAt: string | null
  readyAt: string | null
  deliveredAt: string | null
  cancelledAt: string | null
  cancelledBy: string | null
  cancelReason: string | null
  canCancel: boolean
}

export interface CheckoutInput {
  address: string
  phone: string
  notes: string
}

export interface Dashboard {
  ordersToday: number
  revenueToday: number
  openOrders: number
  averageOrder: number
  deliveredThisWeek: number
  revenueThisWeek: number
  byStatus: { status: string; count: number }[]
  bestSellers: { name: string; quantity: number }[]
  lastSevenDays: { date: string; orders: number; revenue: number }[]
}
