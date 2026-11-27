import { http } from './client'
import type { AuthResponse, CheckoutInput, Dashboard, MenuInput, MenuItem, Order, OrderStatus, RegisterInput } from './types'

export const authApi = {
  login: (username: string, password: string) =>
    http.post<AuthResponse>('/auth/login', { username, password }).then((r) => r.data),
  register: (input: RegisterInput) => http.post<AuthResponse>('/auth/register', input).then((r) => r.data),
}

/** Page size for the "load more" lists (the server allows 1 to 100). */
export const PAGE_SIZE = 20

export const customerApi = {
  menu: (category?: string, vegetarian?: boolean, search?: string) =>
    http.get<MenuItem[]>('/menu', { params: { category: category || undefined, vegetarian: vegetarian || undefined, search: search || undefined } }).then((r) => r.data),
  categories: () => http.get<string[]>('/menu/categories').then((r) => r.data),
  /**
   * The idempotency key identifies this checkout: if the request is sent again (a timeout, a double click) the server returns the order it
   * already made instead of making a second one.
   */
  place: (lines: { menuItemId: number; quantity: number }[], checkout: CheckoutInput, idempotencyKey: string) =>
    http
      .post<Order>('/orders', { items: lines, address: checkout.address, phone: checkout.phone, notes: checkout.notes || null }, { headers: { 'Idempotency-Key': idempotencyKey } })
      .then((r) => r.data),
  /** scope: active or past. With a page, the server returns that page of `size` orders (newest first). */
  mine: (scope: 'active' | 'past', page?: number, size?: number) =>
    http.get<Order[]>('/orders/my', { params: { scope, page, size } }).then((r) => r.data),
  cancel: (id: number, reason?: string) => http.post<Order>(`/orders/${id}/cancel`, { reason: reason || null }).then((r) => r.data),
}

const menuBody = (input: MenuInput) => ({
  ...input,
  description: input.description || null,
  category: input.category || null,
  price: input.price === '' ? null : Number(input.price),
})

export interface BoardQuery {
  status?: OrderStatus
  /** "open" keeps only orders that are not delivered or cancelled */
  scope?: 'open'
  search?: string
  page?: number
  size?: number
}

export const adminApi = {
  orders: ({ status, scope, search, page, size }: BoardQuery = {}) =>
    http.get<Order[]>('/admin/orders', { params: { status, scope, search: search || undefined, page, size } }).then((r) => r.data),
  setStatus: (id: number, status: OrderStatus) => http.put<Order>(`/admin/orders/${id}/status`, { status }).then((r) => r.data),
  cancel: (id: number, reason: string) => http.put<Order>(`/admin/orders/${id}/cancel`, { reason }).then((r) => r.data),
  menu: (category?: string, search?: string) =>
    http.get<MenuItem[]>('/admin/menu', { params: { category: category || undefined, search: search || undefined } }).then((r) => r.data),
  createDish: (input: MenuInput) => http.post<MenuItem>('/admin/menu', menuBody(input)).then((r) => r.data),
  updateDish: (id: number, input: MenuInput) => http.put<MenuItem>(`/admin/menu/${id}`, menuBody(input)).then((r) => r.data),
  dashboard: () => http.get<Dashboard>('/admin/dashboard').then((r) => r.data),
}
