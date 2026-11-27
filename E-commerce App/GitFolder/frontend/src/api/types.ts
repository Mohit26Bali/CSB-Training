export type Role = 'CUSTOMER' | 'ADMIN'
export type VariantType = 'NONE' | 'SIZE' | 'STORAGE' | 'CONFIGURATION'
export type AddressType = 'HOME' | 'WORK' | 'OTHER'
export type PaymentMethod = 'COD' | 'UPI' | 'CARD'
export type PaymentStatus = 'PENDING' | 'PAID' | 'REFUNDED' | 'CANCELLED'
export type OrderStatus = 'PLACED' | 'CONFIRMED' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED'

export interface ApiErrorBody {
  status: number
  error: string
  message: string
  path: string
  fieldErrors: Record<string, string>
}

export interface CategoryRef {
  name: string
  slug: string
}

export interface CategoryNode {
  id: number
  name: string
  slug: string
  imageUrl: string | null
  productCount: number
  children: CategoryNode[]
}

export interface CategoryDetail extends Omit<CategoryNode, 'children'> {
  breadcrumb: CategoryRef[]
  children: CategoryNode[]
}

export interface ProductCard {
  id: number
  slug: string
  title: string
  brand: string | null
  thumbnailUrl: string
  price: number
  mrp: number
  discountPercent: number
  rating: number
  ratingCount: number
  inStock: boolean
}

export interface FacetOption {
  value: string
  label: string
  count: number
}

export interface SearchFacets {
  categories: FacetOption[]
  brands: FacetOption[]
  price: { min: number; max: number } | null
  sizes: FacetOption[]
  ratings: FacetOption[]
  discounts: FacetOption[]
}

export interface ProductPage {
  items: ProductCard[]
  page: number
  size: number
  totalItems: number
  totalPages: number
  sort: string
  facets: SearchFacets
}

export interface Variant {
  id: number
  sku: string
  label: string
  price: number
  mrp: number
  discountPercent: number
  stock: number
}

export interface RatingSummary {
  average: number
  count: number
  distribution: Record<string, number>
}

export interface Review {
  id: number
  reviewerName: string
  rating: number
  comment: string
  createdAt: string
}

export interface ReviewPage {
  items: Review[]
  page: number
  size: number
  totalItems: number
  totalPages: number
  summary: RatingSummary
}

export interface ProductDetail {
  id: number
  slug: string
  title: string
  description: string
  brand: CategoryRef | null
  breadcrumb: CategoryRef[]
  variantType: VariantType
  price: number
  mrp: number
  discountPercent: number
  inStock: boolean
  images: string[]
  variants: Variant[]
  specs: { name: string; value: string }[]
  warranty: string | null
  shippingInfo: string | null
  returnPolicy: string | null
  tags: string[]
  ratingSummary: RatingSummary
  topReviews: Review[]
  similar: ProductCard[]
}

export interface HomeSection {
  key: string
  title: string
  viewAll: string
  products: ProductCard[]
}

export interface HomeResponse {
  categories: CategoryNode[]
  sections: HomeSection[]
}

export interface Suggestions {
  products: ProductCard[]
  categories: CategoryRef[]
  brands: CategoryRef[]
}

export interface User {
  id: number
  fullName: string
  email: string
  phone: string | null
  role: Role
}

export interface AuthResponse {
  token: string
  expiresAt: string
  user: User
}

export interface Address {
  id: number
  fullName: string
  phone: string
  line1: string
  line2: string | null
  landmark: string | null
  city: string
  state: string
  pincode: string
  type: AddressType
  isDefault: boolean
}

export type AddressInput = Omit<Address, 'id' | 'isDefault'> & { makeDefault?: boolean }

export interface CartItem {
  id: number
  productId: number
  productSlug: string
  title: string
  brand: string | null
  thumbnailUrl: string
  variantId: number
  variantLabel: string
  variantType: VariantType
  price: number
  mrp: number
  quantity: number
  maxQuantity: number
  lineTotal: number
  problem: string | null
}

export interface Cart {
  items: CartItem[]
  summary: {
    itemCount: number
    totalMrp: number
    discount: number
    subtotal: number
    deliveryFee: number
    total: number
    freeDeliveryThreshold: number
  }
  checkoutReady: boolean
}

export interface WishlistItem {
  product: ProductCard
  addedAt: string
}

export interface Coupon {
  code: string
  description: string
  discountType: 'PERCENT' | 'FLAT'
  discountValue: number
  maxDiscount: number | null
  minOrderAmount: number
  validUntil: string
}

export interface CheckoutPreview {
  items: CartItem[]
  itemCount: number
  totalMrp: number
  productDiscount: number
  itemTotal: number
  couponCode: string | null
  couponDiscount: number
  couponMessage: string | null
  deliveryFee: number
  grandTotal: number
  codAvailable: boolean
  address: Address | null
  checkoutReady: boolean
}

export interface OrderSummary {
  orderNumber: string
  status: OrderStatus
  placedAt: string
  grandTotal: number
  itemCount: number
  thumbnails: string[]
  paymentMethod: PaymentMethod
}

export interface OrderDetail {
  orderNumber: string
  status: OrderStatus
  placedAt: string
  customerName: string
  customerEmail: string
  items: {
    productSlug: string
    title: string
    variantLabel: string
    thumbnailUrl: string
    unitPrice: number
    unitMrp: number
    quantity: number
    lineTotal: number
  }[]
  shippingAddress: Omit<Address, 'id' | 'type' | 'isDefault'>
  paymentMethod: PaymentMethod
  paymentStatus: PaymentStatus
  paymentReference: string | null
  totalMrp: number
  productDiscount: number
  itemTotal: number
  couponCode: string | null
  couponDiscount: number
  deliveryFee: number
  grandTotal: number
  timeline: { status: OrderStatus; note: string | null; at: string }[]
  cancellable: boolean
  cancelReason: string | null
}

export interface Paged<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}
