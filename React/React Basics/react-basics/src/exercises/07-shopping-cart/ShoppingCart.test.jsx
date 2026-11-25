import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import ShoppingCartApp from './ShoppingCartApp.jsx'
import { catalog } from './catalog.js'
import { summarize } from './cartMath.js'

describe('summarize()', () => {
  it('derives count, subtotal, tax and total from the cart', () => {
    const cart = [{ price: 1000, quantity: 2 }, { price: 500, quantity: 1 }]
    expect(summarize(cart)).toEqual({ itemCount: 3, subtotal: 2500, discount: 0, tax: 450, total: 2950 })
  })

  it('gives no discount at exactly ₹5,000 but 10% above it (boundary)', () => {
    expect(summarize([{ price: 5000, quantity: 1 }]).discount).toBe(0)
    const above = summarize([{ price: 5001, quantity: 1 }])
    expect(above.discount).toBe(500.1)
    expect(above.tax).toBe(810.16)                  // 18% of 4500.90
    expect(above.total).toBe(5311.06)
  })

  it('an empty cart is all zero', () => {
    expect(summarize([])).toEqual({ itemCount: 0, subtotal: 0, discount: 0, tax: 0, total: 0 })
  })
})

describe('Shopping Cart', () => {
  it('has at least five products and starts with an empty-cart message', () => {
    render(<ShoppingCartApp />)
    expect(catalog.length).toBeGreaterThanOrEqual(5)
    expect(screen.getByText('Your cart is empty. Add a product to get started.')).toBeInTheDocument()
  })

  it('adds, increases, decreases and removes items, with the total always derived', async () => {
    const user = userEvent.setup()
    render(<ShoppingCartApp />)

    await user.click(screen.getByRole('button', { name: 'Add Wireless Mouse' }))
    await user.click(screen.getByRole('button', { name: 'Add Wireless Mouse' }))     // same product: quantity 2
    await user.click(screen.getByRole('button', { name: 'Add Notebook Pack' }))
    expect(screen.getByLabelText('Quantity of Wireless Mouse')).toHaveTextContent('2')
    expect(screen.getByRole('heading', { name: 'Cart (3 items)' })).toBeInTheDocument()
    expect(screen.getByTestId('cart-total')).toHaveTextContent('₹2,179.46')             // (1598 + 249) × 1.18

    await user.click(screen.getByRole('button', { name: 'Increase Notebook Pack' }))
    await user.click(screen.getByRole('button', { name: 'Decrease Wireless Mouse' }))
    expect(screen.getByRole('heading', { name: 'Cart (3 items)' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Decrease Wireless Mouse' }))  // to 0: removed
    expect(screen.queryByLabelText('Quantity of Wireless Mouse')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Remove Notebook Pack' }))
    expect(screen.getByText('Your cart is empty. Add a product to get started.')).toBeInTheDocument()
  })
})
