const rupee = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 })

/** ₹1,02,999 (Indian digit grouping, no paise). */
export const formatPrice = (amount: number) => rupee.format(amount)

export const formatDate = (iso: string) =>
  new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })

export const formatDateTime = (iso: string) =>
  new Date(iso).toLocaleString('en-IN', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })

/** "Standard" is the label of products without options; it is not worth showing. */
export const variantText = (label: string) => (label === 'Standard' ? '' : label)

/** "1 product", "3 products". */
export const plural = (count: number, word: string, many = `${word}s`) => `${count.toLocaleString('en-IN')} ${count === 1 ? word : many}`
