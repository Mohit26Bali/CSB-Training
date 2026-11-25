import { api } from './client'
import type {
  Address,
  AddressInput,
  AuthResponse,
  Cart,
  CategoryDetail,
  CategoryNode,
  CheckoutPreview,
  Coupon,
  HomeResponse,
  OrderDetail,
  OrderSummary,
  Paged,
  PaymentMethod,
  ProductDetail,
  ProductPage,
  Review,
  ReviewPage,
  Suggestions,
  User,
  WishlistItem,
} from './types'

// ---- catalogue (public) ----
export const getHome = () => api<HomeResponse>('/home')
export const getCategories = () => api<CategoryNode[]>('/categories')
export const getCategory = (slug: string) => api<CategoryDetail>(`/categories/${encodeURIComponent(slug)}`)
export const searchProducts = (query: string) => api<ProductPage>(`/products?${query}`)
export const getProduct = (slug: string) => api<ProductDetail>(`/products/${encodeURIComponent(slug)}`)
export const getReviews = (slug: string, page: number, sort: string) =>
  api<ReviewPage>(`/products/${encodeURIComponent(slug)}/reviews?page=${page}&size=10&sort=${sort}`)
export const getSuggestions = (q: string) => api<Suggestions>(`/search/suggest?q=${encodeURIComponent(q)}`)
export const getMyReview = (slug: string) =>
  api<{ canReview: boolean; reason: string | null; review: Review | null }>(`/products/${encodeURIComponent(slug)}/reviews/mine`)
export const writeReview = (slug: string, body: { rating: number; comment: string }) =>
  api<Review>(`/products/${encodeURIComponent(slug)}/reviews`, { method: 'POST', body })

// ---- account ----
export const login = (email: string, password: string) =>
  api<AuthResponse>('/auth/login', { method: 'POST', body: { email, password } })
export const register = (body: { fullName: string; email: string; password: string; phone?: string }) =>
  api<AuthResponse>('/auth/register', { method: 'POST', body })
export const getMe = () => api<User>('/auth/me')
export const updateProfile = (body: { fullName: string; phone: string | null }) =>
  api<User>('/account/profile', { method: 'PUT', body })
export const changePassword = (body: { currentPassword: string; newPassword: string }) =>
  api<void>('/account/password', { method: 'PUT', body })

// ---- addresses ----
export const getAddresses = () => api<Address[]>('/addresses')
export const getStates = () => api<string[]>('/addresses/states')
export const createAddress = (body: AddressInput) => api<Address>('/addresses', { method: 'POST', body })
export const updateAddress = (id: number, body: AddressInput) => api<Address>(`/addresses/${id}`, { method: 'PUT', body })
export const makeDefaultAddress = (id: number) => api<Address>(`/addresses/${id}/default`, { method: 'PUT' })
export const deleteAddress = (id: number) => api<void>(`/addresses/${id}`, { method: 'DELETE' })

// ---- cart and wishlist ----
export const getCart = () => api<Cart>('/cart')
export const addToCart = (variantId: number, quantity = 1) =>
  api<Cart>('/cart/items', { method: 'POST', body: { variantId, quantity } })
export const setCartQuantity = (itemId: number, quantity: number) =>
  api<Cart>(`/cart/items/${itemId}`, { method: 'PUT', body: { quantity } })
export const removeCartItem = (itemId: number) => api<Cart>(`/cart/items/${itemId}`, { method: 'DELETE' })
export const saveForLater = (itemId: number) => api<Cart>(`/cart/items/${itemId}/move-to-wishlist`, { method: 'POST' })
export const getWishlist = () => api<WishlistItem[]>('/wishlist')
export const addToWishlist = (productId: number) => api<WishlistItem[]>('/wishlist', { method: 'POST', body: { productId } })
export const removeFromWishlist = (productId: number) => api<WishlistItem[]>(`/wishlist/${productId}`, { method: 'DELETE' })

// ---- checkout and orders ----
export const getCoupons = () => api<Coupon[]>('/coupons')
export const previewCheckout = (body: { addressId?: number; couponCode?: string }) =>
  api<CheckoutPreview>('/checkout/preview', { method: 'POST', body })
export const placeOrder = (body: { addressId: number; paymentMethod: PaymentMethod; couponCode?: string; upiId?: string }) =>
  api<OrderDetail>('/orders', { method: 'POST', body })
export const getOrders = (page: number) => api<Paged<OrderSummary>>(`/orders?page=${page}&size=10`)
export const getOrder = (number: string) => api<OrderDetail>(`/orders/${encodeURIComponent(number)}`)
export const cancelOrder = (number: string, reason: string) =>
  api<OrderDetail>(`/orders/${encodeURIComponent(number)}/cancel`, { method: 'POST', body: { reason } })
