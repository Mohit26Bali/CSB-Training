import { render, screen } from '@testing-library/react'
import ProductCard from './ProductCard.jsx'
import ProductCatalogApp from './ProductCatalogApp.jsx'
import { products } from './products.js'

describe('Product Catalog', () => {
  it('shows whatever the props say (nothing is hard-coded in the card)', () => {
    render(<ProductCard name="Test Mug" category="Kitchen" price={350} rating={4.2} available image="/x.svg" />)

    expect(screen.getByRole('heading', { name: 'Test Mug' })).toBeInTheDocument()
    expect(screen.getByText('Kitchen')).toBeInTheDocument()
    expect(screen.getByText('Price: ₹350')).toBeInTheDocument()
    expect(screen.getByText('Rating: 4.2 / 5')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'Test Mug' })).toHaveAttribute('src', '/x.svg')
  })

  it('shows Available or Out of Stock from the available prop', () => {
    const { rerender } = render(<ProductCard name="A" category="C" price={1} rating={1} available image="" />)
    expect(screen.getByText('Available')).toBeInTheDocument()

    rerender(<ProductCard name="A" category="C" price={1} rating={1} available={false} image="" />)
    expect(screen.getByText('Out of Stock')).toBeInTheDocument()
  })

  it('lists at least five products, with the right stock labels', () => {
    render(<ProductCatalogApp />)

    expect(screen.getAllByRole('article')).toHaveLength(products.length)
    expect(products.length).toBeGreaterThanOrEqual(5)
    expect(screen.getAllByText('Out of Stock')).toHaveLength(products.filter((p) => !p.available).length)
  })
})
