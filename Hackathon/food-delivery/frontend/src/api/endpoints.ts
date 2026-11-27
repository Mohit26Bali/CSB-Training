import { http } from './client'
import type { AuthResponse, CheckoutInput, Dashboard, MenuInput, MenuItem, Order, OrderStatus, RegisterInput } from './types'

export const authApi = {
  login: (username: string, password: string) =>
    http.post<AuthResponse>('/auth/login', { username, password }).then((r) => r.data),
  register: (input: RegisterInput) => http.post<AuthResponse>('/auth/register', input).then((r) => r.data),
}

export const customerApi = {
  menu: (category?: string, vegetarian?: boolean, search?: string) =>
    http.get<MenuItem[]>('/menu', { params: { category: category || undefined, vegetarian: vegetarian || undefined, search: search || undefined } }).then((r) => r.data),
  categories: () => http.get<string[]>('/menu/categories').then((r) => r.data),
  place: (lines: { menuItemId: number; quantity: number }[], checkout: CheckoutInput) =>
    http.post<Order>('/orders', { items: lines, address: checkout.address, phone: checkout.phone, notes: checkout.notes || null }).then((r) => r.data),
  mine: (scope?: string) => http.get<Order[]>('/orders/my', { params: { scope } }).then((r) => r.data),
  cancel: (id: number, reason?: string) => http.delete<Order>(`/orders/${id}`, { params: { reason: reason || undefined } }).then((r) => r.data),
}

const menuBody = (input: MenuInput) => ({
  ...input,
  description: input.description || null,
  category: input.category || null,
  price: input.price === '' ? null : Number(input.price),
})

export const adminApi = {
  orders: (status?: OrderStatus, search?: string) =>
    http.get<Order[]>('/admin/orders', { params: { status, search: search || undefined } }).then((r) => r.data),
  setStatus: (id: number, status: OrderStatus) => http.put<Order>(`/admin/orders/${id}/status`, { status }).then((r) => r.data),
  cancel: (id: number, reason: string) => http.put<Order>(`/admin/orders/${id}/cancel`, { reason }).then((r) => r.data),
  menu: (category?: string, search?: string) =>
    http.get<MenuItem[]>('/admin/menu', { params: { category: category || undefined, search: search || undefined } }).then((r) => r.data),
  createDish: (input: MenuInput) => http.post<MenuItem>('/admin/menu', menuBody(input)).then((r) => r.data),
  updateDish: (id: number, input: MenuInput) => http.put<MenuItem>(`/admin/menu/${id}`, menuBody(input)).then((r) => r.data),
  dashboard: () => http.get<Dashboard>('/admin/dashboard').then((r) => r.data),
}
