import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { ApiError } from './api/client'
import * as api from './api/endpoints'
import type { Cart, WishlistItem } from './api/types'
import { useAuth } from './auth/AuthContext'
import { useToast } from './components/Toast'

/** Cart and wishlist exist for customers only; for visitors and admins these queries stay idle. */
function useIsCustomer() {
  const { user } = useAuth()
  return user?.role === 'CUSTOMER'
}

export function useCart() {
  return useQuery({ queryKey: ['cart'], queryFn: api.getCart, enabled: useIsCustomer() })
}

export function useWishlist() {
  return useQuery({ queryKey: ['wishlist'], queryFn: api.getWishlist, enabled: useIsCustomer() })
}

export const errorMessage = (error: unknown) =>
  error instanceof ApiError || error instanceof Error ? error.message : 'Something went wrong'

/** Sends visitors to the login page (coming back afterwards); returns false when the action must stop. */
function useRequireCustomer() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const toast = useToast()
  return () => {
    if (!user) {
      navigate(`/login?next=${encodeURIComponent(window.location.pathname + window.location.search)}`)
      return false
    }
    if (user.role !== 'CUSTOMER') {
      toast('Shopping is for customer accounts. Log in as a customer to buy.', 'error')
      return false
    }
    return true
  }
}

export function useAddToCart() {
  const queryClient = useQueryClient()
  const toast = useToast()
  const requireCustomer = useRequireCustomer()
  const mutation = useMutation({
    mutationFn: ({ variantId, quantity }: { variantId: number; quantity: number }) => api.addToCart(variantId, quantity),
    onSuccess: (cart: Cart) => {
      queryClient.setQueryData(['cart'], cart)
      toast('Added to your cart')
    },
    onError: (error) => toast(errorMessage(error), 'error'),
  })
  return {
    ...mutation,
    add: (variantId: number, quantity = 1, options?: { onSuccess?: () => void }) => {
      if (requireCustomer()) mutation.mutate({ variantId, quantity }, options)
    },
  }
}

/** The heart button: adds or removes, and shows the result immediately. */
export function useToggleWishlist() {
  const queryClient = useQueryClient()
  const toast = useToast()
  const requireCustomer = useRequireCustomer()
  const wishlist = useWishlist()
  const mutation = useMutation({
    mutationFn: ({ productId, add }: { productId: number; add: boolean }) =>
      add ? api.addToWishlist(productId) : api.removeFromWishlist(productId),
    onSuccess: (items: WishlistItem[], { add }) => {
      queryClient.setQueryData(['wishlist'], items)
      toast(add ? 'Saved to your wishlist' : 'Removed from your wishlist')
    },
    onError: (error) => toast(errorMessage(error), 'error'),
  })
  const saved = new Set((wishlist.data ?? []).map((w) => w.product.id))
  return {
    isSaved: (productId: number) => saved.has(productId),
    toggle: (productId: number) => {
      if (requireCustomer()) mutation.mutate({ productId, add: !saved.has(productId) })
    },
    pending: mutation.isPending,
  }
}
