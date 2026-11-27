import { api } from './client'
import type { CategoryNode, OrderDetail, OrderStatus, Paged, PaymentMethod, PaymentStatus, VariantType } from './types'

export interface AdminOrderRow {
  orderNumber: string
  placedAt: string
  status: OrderStatus
  paymentMethod: PaymentMethod
  paymentStatus: PaymentStatus
  grandTotal: number
  itemCount: number
  customerName: string
  customerEmail: string
  city: string
}

export interface Dashboard {
  revenue: number
  orders: number
  averageOrderValue: number
  ordersToday: number
  revenueToday: number
  customers: number
  activeProducts: number
  ordersByStatus: Record<OrderStatus, number>
  salesLast14Days: { date: string; orders: number; revenue: number }[]
  topProducts: { productId: number; slug: string; title: string; thumbnailUrl: string; unitsSold: number; revenue: number }[]
  lowStock: { variantId: number; productId: number; productSlug: string; title: string; label: string; sku: string; stock: number }[]
  recentOrders: AdminOrderRow[]
}

export interface AdminProductRow {
  id: number
  slug: string
  title: string
  brand: string | null
  category: string
  thumbnailUrl: string
  price: number
  mrp: number
  discountPercent: number
  totalStock: number
  variantCount: number
  active: boolean
  rating: number
  ratingCount: number
  updatedAt: string
}

export interface AdminVariant {
  id: number
  sku: string
  label: string
  price: number
  mrp: number
  stock: number
}

export interface AdminProduct {
  id: number
  slug: string
  title: string
  description: string
  categoryId: number
  categoryName: string
  brandId: number | null
  brandName: string | null
  variantType: VariantType
  images: string[]
  variants: AdminVariant[]
  specs: { name: string; value: string }[]
  tags: string[]
  warranty: string | null
  shippingInfo: string | null
  returnPolicy: string | null
  active: boolean
  price: number
  mrp: number
  discountPercent: number
}

export interface ProductInput {
  title: string
  description: string
  categoryId: number | null
  brandId: number | null
  variantType: VariantType
  images: string[]
  variants: { id?: number; sku: string; label: string; price: number | null; mrp: number | null; stock: number | null }[]
  specs: { name: string; value: string }[]
  tags: string[]
  warranty: string
  shippingInfo: string
  returnPolicy: string
  active: boolean
}

export interface Brand {
  id: number
  name: string
  slug: string
  productCount: number
}

export interface AdminCoupon {
  id: number
  code: string
  description: string
  discountType: 'PERCENT' | 'FLAT'
  discountValue: number
  maxDiscount: number | null
  minOrderAmount: number
  validFrom: string
  validUntil: string
  usageLimit: number | null
  perUserLimit: number
  usedCount: number
  active: boolean
  state: 'ACTIVE' | 'SCHEDULED' | 'EXPIRED' | 'USED_UP' | 'DISABLED'
}

export type CouponInput = Omit<AdminCoupon, 'id' | 'usedCount' | 'state'>

export interface AdminCustomer {
  id: number
  fullName: string
  email: string
  phone: string | null
  enabled: boolean
  joinedAt: string
  orderCount: number
  totalSpent: number
}

const qs = (params: Record<string, string | number | boolean | null | undefined>) =>
  new URLSearchParams(Object.entries(params).filter(([, v]) => v !== null && v !== undefined && v !== '').map(([k, v]) => [k, String(v)])).toString()

export const getDashboard = () => api<Dashboard>('/admin/dashboard')

export const listOrders = (p: { status?: string; q?: string; page: number }) => api<Paged<AdminOrderRow>>(`/admin/orders?${qs({ ...p, size: 20 })}`)
export const getAdminOrder = (number: string) => api<OrderDetail>(`/admin/orders/${encodeURIComponent(number)}`)
export const moveOrder = (number: string, status: OrderStatus, note: string) =>
  api<OrderDetail>(`/admin/orders/${encodeURIComponent(number)}/status`, { method: 'PUT', body: { status, note } })
export const adminCancelOrder = (number: string, reason: string) =>
  api<OrderDetail>(`/admin/orders/${encodeURIComponent(number)}/cancel`, { method: 'POST', body: { reason } })

export const listProducts = (p: { q?: string; category?: string; active?: string; page: number }) =>
  api<Paged<AdminProductRow>>(`/admin/products?${qs({ ...p, size: 20 })}`)
export const getAdminProduct = (id: number) => api<AdminProduct>(`/admin/products/${id}`)
export const createProduct = (body: ProductInput) => api<AdminProduct>('/admin/products', { method: 'POST', body })
export const updateProduct = (id: number, body: ProductInput) => api<AdminProduct>(`/admin/products/${id}`, { method: 'PUT', body })
export const setProductActive = (id: number, active: boolean) =>
  api<AdminProduct>(`/admin/products/${id}/active`, { method: 'PATCH', body: { active } })
export const setVariantStock = (variantId: number, stock: number) =>
  api<AdminVariant>(`/admin/variants/${variantId}/stock`, { method: 'PATCH', body: { stock } })

export const getAdminCategories = () => api<CategoryNode[]>('/admin/categories')
export const createCategory = (body: { name: string; parentId: number | null }) => api<unknown>('/admin/categories', { method: 'POST', body })
export const renameCategory = (id: number, name: string) => api<unknown>(`/admin/categories/${id}`, { method: 'PUT', body: { name } })
export const deleteCategory = (id: number) => api<void>(`/admin/categories/${id}`, { method: 'DELETE' })

export const getBrands = () => api<Brand[]>('/admin/brands')
export const createBrand = (name: string) => api<Brand>('/admin/brands', { method: 'POST', body: { name } })
export const renameBrand = (id: number, name: string) => api<Brand>(`/admin/brands/${id}`, { method: 'PUT', body: { name } })
export const deleteBrand = (id: number) => api<void>(`/admin/brands/${id}`, { method: 'DELETE' })

export const getAdminCoupons = () => api<AdminCoupon[]>('/admin/coupons')
export const createCoupon = (body: CouponInput) => api<AdminCoupon>('/admin/coupons', { method: 'POST', body })
export const updateCoupon = (id: number, body: CouponInput) => api<AdminCoupon>(`/admin/coupons/${id}`, { method: 'PUT', body })
export const setCouponActive = (id: number, active: boolean) =>
  api<AdminCoupon>(`/admin/coupons/${id}/active`, { method: 'PATCH', body: { active } })

export const listCustomers = (p: { q?: string; page: number }) => api<Paged<AdminCustomer>>(`/admin/customers?${qs({ ...p, size: 20 })}`)
export const setCustomerEnabled = (id: number, enabled: boolean) =>
  api<void>(`/admin/customers/${id}/enabled`, { method: 'PATCH', body: { enabled } })
