import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import ShoppingCartApp from './ShoppingCartApp.jsx'
import { PRODUCTS, priceCart } from './pricing.js'

const line = (price, quantity) => ({ price, quantity })

describe('priceCart()', () => {
  it('derives subtotal, tax and total', () => {
    expect(priceCart([line(1000, 2), line(500, 1)], '')).toEqual(
      { itemCount: 3, subtotal: 2500, discount: 0, tax: 450, total: 2950, codeError: '' })
  })

  it('SAVE10 takes 10% off before tax', () => {
    const price = priceCart([line(1000, 1)], 'SAVE10')
    expect(price.discount).toBe(100)
    expect(price.total).toBe(1062)                    // (1000 - 100) × 1.18
  })

  it('FLAT500 needs at least ₹2,000 (boundary)', () => {
    expect(priceCart([line(1999, 1)], 'FLAT500')).toMatchObject({ discount: 0, codeError: 'FLAT500 needs a subtotal of at least ₹2,000' })
    expect(priceCart([line(2000, 1)], 'FLAT500')).toMatchObject({ discount: 500, codeError: '' })
  })

  it('an empty cart is all zero', () => {
    expect(priceCart([], '')).toMatchObject({ itemCount: 0, subtotal: 0, tax: 0, total: 0 })
  })
})

describe('Shopping cart', () => {
  it('has six products and starts empty', () => {
    render(<ShoppingCartApp />)
    expect(PRODUCTS).toHaveLength(6)
    expect(screen.getByText('Your cart is empty.')).toBeInTheDocument()
  })

  it('adds, increases, decreases and removes products', async () => {
    const user = userEvent.setup()
    render(<ShoppingCartApp />)
    await user.click(screen.getByRole('button', { name: 'Add Notebook Set' }))
    await user.click(screen.getByRole('button', { name: 'Increase Notebook Set' }))
    await user.click(screen.getByRole('button', { name: 'Add Wireless Mouse' }))
    expect(screen.getByLabelText('Quantity of Notebook Set')).toHaveTextContent('2')
    expect(screen.getByRole('heading', { name: 'Cart (3 items)' })).toBeInTheDocument()
    expect(screen.getByTestId('grand-total')).toHaveTextContent('₹1,766.46')       // (598 + 899) × 1.18

    await user.click(screen.getByRole('button', { name: 'Decrease Notebook Set' }))
    await user.click(screen.getByRole('button', { name: 'Decrease Notebook Set' }))
    expect(screen.queryByLabelText('Quantity of Notebook Set')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Remove Wireless Mouse' }))
    expect(screen.getByText('Your cart is empty.')).toBeInTheDocument()
  })

  it('applies a valid discount code and rejects an unknown one', async () => {
    const user = userEvent.setup()
    render(<ShoppingCartApp />)
    await user.click(screen.getByRole('button', { name: 'Add Mechanical Keyboard' }))

    await user.type(screen.getByLabelText('Discount code'), 'WELCOME')
    await user.click(screen.getByRole('button', { name: 'Apply' }))
    expect(screen.getByRole('alert')).toHaveTextContent('"WELCOME" is not a valid code')

    await user.clear(screen.getByLabelText('Discount code'))
    await user.type(screen.getByLabelText('Discount code'), 'save10')
    await user.click(screen.getByRole('button', { name: 'Apply' }))
    expect(screen.getByText('Discount (SAVE10)')).toBeInTheDocument()
    expect(screen.getByTestId('grand-total')).toHaveTextContent('₹3,715.94')       // 3499 × 0.9 × 1.18
  })
})
