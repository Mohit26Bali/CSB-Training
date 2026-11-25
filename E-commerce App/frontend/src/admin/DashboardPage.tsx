import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { getDashboard, setVariantStock, type Dashboard } from '../api/admin'
import type { OrderStatus } from '../api/types'
import { useToast } from '../components/Toast'
import { Card, ErrorState, PageSpinner } from '../components/ui'
import { errorMessage } from '../hooks'
import { formatDateTime, formatPrice, plural } from '../lib/format'
import { StatusBadge } from '../pages/OrderPages'
import { PageHeader, td, th } from './AdminLayout'

const compact = new Intl.NumberFormat('en-IN', { notation: 'compact', maximumFractionDigits: 1 })
const shortDate = (iso: string) => new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })

function Kpi({ label, value, note }: { label: string; value: string; note?: string }) {
  return (
    <Card className="p-4">
      <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">{label}</p>
      <p className="mt-1 text-2xl font-semibold text-slate-900">{value}</p>
      {note && <p className="mt-0.5 text-xs text-slate-500">{note}</p>}
    </Card>
  )
}

/**
 * Daily revenue for the last 14 days as one series of bars (single hue, rounded tops, recessive grid). Hovering or
 * focusing a bar shows the day's revenue and order count; the same numbers are available as a table.
 */
function SalesChart({ days }: { days: Dashboard['salesLast14Days'] }) {
  const [active, setActive] = useState<number | null>(null)
  const [showTable, setShowTable] = useState(false)
  const max = Math.max(...days.map((d) => d.revenue), 1)
  const step = Math.pow(10, Math.floor(Math.log10(max)))
  const top = Math.ceil(max / step) * step
  const ticks = [0, top / 2, top]
  const W = 700
  const H = 220
  const left = 48
  const bottom = 24
  const plotH = H - bottom - 8
  const slot = (W - left) / days.length
  const barW = Math.min(28, slot - 8)
  const y = (v: number) => 8 + plotH - (v / top) * plotH
  const hovered = active === null ? null : days[active]

  return (
    <Card>
      <div className="mb-3 flex items-baseline justify-between gap-3">
        <div>
          <h2 className="font-semibold text-slate-900">Revenue, last 14 days</h2>
          <p className="text-xs text-slate-500">Cancelled orders are not counted</p>
        </div>
        <button className="text-xs font-semibold text-brand-700" onClick={() => setShowTable((s) => !s)}>{showTable ? 'Show chart' : 'Show as table'}</button>
      </div>
      {showTable ? (
        <table className="w-full text-sm">
          <thead><tr><th className={th}>Day</th><th className={th}>Orders</th><th className={`${th} text-right`}>Revenue</th></tr></thead>
          <tbody>
            {days.map((d) => (
              <tr key={d.date} className="border-t border-slate-100"><td className={td}>{shortDate(d.date)}</td><td className={td}>{d.orders}</td><td className={`${td} text-right`}>{formatPrice(d.revenue)}</td></tr>
            ))}
          </tbody>
        </table>
      ) : (
        <div className="relative">
          <svg viewBox={`0 0 ${W} ${H}`} className="h-auto w-full" role="img" aria-label="Bar chart of daily revenue for the last 14 days">
            {ticks.map((t) => (
              <g key={t}>
                <line x1={left} x2={W} y1={y(t)} y2={y(t)} stroke="#e2e8f0" strokeWidth="1" />
                <text x={left - 8} y={y(t) + 4} textAnchor="end" className="fill-slate-400 text-[11px]">₹{compact.format(t)}</text>
              </g>
            ))}
            {days.map((d, i) => {
              const x = left + i * slot + (slot - barW) / 2
              const h = Math.max(0, y(0) - y(d.revenue))
              const r = Math.min(4, h)
              return (
                <g key={d.date} onMouseEnter={() => setActive(i)} onMouseLeave={() => setActive(null)} onFocus={() => setActive(i)} onBlur={() => setActive(null)}
                  tabIndex={0} aria-label={`${shortDate(d.date)}: ${formatPrice(d.revenue)}, ${plural(d.orders, 'order')}`}>
                  {/* hit target taller and wider than the bar */}
                  <rect x={left + i * slot} y={8} width={slot} height={plotH} fill="transparent" />
                  {h > 0 && (
                    <path d={`M${x},${y(0)} V${y(d.revenue) + r} Q${x},${y(d.revenue)} ${x + r},${y(d.revenue)} H${x + barW - r} Q${x + barW},${y(d.revenue)} ${x + barW},${y(d.revenue) + r} V${y(0)} Z`}
                      fill={active === i ? '#4338ca' : '#6366f1'} />
                  )}
                  {(i % 2 === days.length % 2 || days.length <= 7) && (
                    <text x={x + barW / 2} y={H - 6} textAnchor="middle" className="fill-slate-500 text-[11px]">{shortDate(d.date)}</text>
                  )}
                </g>
              )
            })}
          </svg>
          {hovered && active !== null && (
            // Centred over the bar, but anchored to its left / right side near the chart's edges so it never overflows
            <div className={`pointer-events-none absolute whitespace-nowrap rounded-md bg-slate-900 px-3 py-2 text-xs text-white shadow-lg ${
                active < 2 ? '' : active > days.length - 3 ? '-translate-x-full' : '-translate-x-1/2'}`}
              style={{ left: `${((left + active * slot + slot / 2) / W) * 100}%`, top: 0 }}>
              <p className="font-semibold">{shortDate(hovered.date)}</p>
              <p>{formatPrice(hovered.revenue)} · {hovered.orders} {hovered.orders === 1 ? 'order' : 'orders'}</p>
            </div>
          )}
        </div>
      )}
    </Card>
  )
}

const STATUS_ORDER: OrderStatus[] = ['PLACED', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED']

function Restock({ variantId, stock }: { variantId: number; stock: number }) {
  const [value, setValue] = useState(String(Math.max(stock, 20)))
  const queryClient = useQueryClient()
  const toast = useToast()
  const save = useMutation({
    mutationFn: () => setVariantStock(variantId, Number(value)),
    onSuccess: () => {
      toast('Stock updated')
      queryClient.invalidateQueries({ queryKey: ['admin', 'dashboard'] })
    },
    onError: (e) => toast(errorMessage(e), 'error'),
  })
  return (
    <form className="flex items-center gap-1" onSubmit={(e) => { e.preventDefault(); save.mutate() }}>
      <input aria-label="New stock" value={value} inputMode="numeric" onChange={(e) => setValue(e.target.value.replace(/\D/g, ''))}
        className="w-16 rounded border border-slate-300 px-2 py-1 text-sm" />
      <button type="submit" disabled={save.isPending || !value} className="rounded bg-brand-600 px-2 py-1 text-xs font-semibold text-white disabled:opacity-50">Set</button>
    </form>
  )
}

export function DashboardPage() {
  const dashboard = useQuery({ queryKey: ['admin', 'dashboard'], queryFn: getDashboard })
  if (dashboard.isPending) return <PageSpinner />
  if (dashboard.isError) return <ErrorState message={errorMessage(dashboard.error)} onRetry={() => dashboard.refetch()} />
  const d = dashboard.data

  return (
    <div className="space-y-4">
      <PageHeader title="Dashboard" />
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-3 xl:grid-cols-6">
        <Kpi label="Revenue" value={formatPrice(d.revenue)} note={plural(d.orders, 'order')} />
        <Kpi label="Average order" value={formatPrice(d.averageOrderValue)} />
        <Kpi label="Today" value={formatPrice(d.revenueToday)} note={`${d.ordersToday} ${d.ordersToday === 1 ? 'order' : 'orders'}`} />
        <Kpi label="Customers" value={d.customers.toLocaleString('en-IN')} />
        <Kpi label="Active products" value={d.activeProducts.toLocaleString('en-IN')} />
        <Kpi label="To ship" value={String(d.ordersByStatus.PLACED + d.ordersByStatus.CONFIRMED)} note="placed or confirmed" />
      </div>

      <div className="grid gap-4 xl:grid-cols-[2fr_1fr]">
        <SalesChart days={d.salesLast14Days} />
        <Card>
          <h2 className="mb-3 font-semibold text-slate-900">Orders by status</h2>
          <ul className="space-y-2">
            {STATUS_ORDER.map((s) => (
              <li key={s}>
                <Link to={`/admin/orders?status=${s}`} className="flex items-center justify-between rounded-md px-2 py-2 hover:bg-slate-50">
                  <StatusBadge status={s} />
                  <span className="text-lg font-semibold text-slate-900">{d.ordersByStatus[s]}</span>
                </Link>
              </li>
            ))}
          </ul>
        </Card>
      </div>

      <div className="grid gap-4 xl:grid-cols-2">
        <Card className="p-0">
          <h2 className="px-4 pt-4 font-semibold text-slate-900">Best sellers</h2>
          <table className="mt-2 w-full text-sm">
            <thead><tr><th className={th}>Product</th><th className={`${th} text-right`}>Units</th><th className={`${th} text-right`}>Revenue</th></tr></thead>
            <tbody>
              {d.topProducts.map((p) => (
                <tr key={p.productId} className="border-t border-slate-100">
                  <td className={td}>
                    <Link to={`/admin/products/${p.productId}`} className="flex items-center gap-2 hover:text-brand-700">
                      <img src={p.thumbnailUrl} alt="" className="h-8 w-8 rounded object-contain" />
                      <span className="line-clamp-1">{p.title}</span>
                    </Link>
                  </td>
                  <td className={`${td} text-right`}>{p.unitsSold}</td>
                  <td className={`${td} text-right`}>{formatPrice(p.revenue)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
        <Card className="p-0">
          <h2 className="px-4 pt-4 font-semibold text-slate-900">Low stock <span className="text-xs font-normal text-slate-500">(5 or fewer left)</span></h2>
          <table className="mt-2 w-full text-sm">
            <thead><tr><th className={th}>Item</th><th className={`${th} text-right`}>Left</th><th className={th}>Restock</th></tr></thead>
            <tbody>
              {d.lowStock.map((v) => (
                <tr key={v.variantId} className="border-t border-slate-100">
                  <td className={td}>
                    <Link to={`/admin/products/${v.productId}`} className="hover:text-brand-700">{v.title}</Link>
                    {v.label !== 'Standard' && <span className="text-slate-500"> · {v.label}</span>}
                  </td>
                  <td className={`${td} text-right font-semibold ${v.stock === 0 ? 'text-red-600' : 'text-amber-600'}`}>{v.stock}</td>
                  <td className={td}><Restock variantId={v.variantId} stock={v.stock} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      </div>

      <Card className="p-0">
        <div className="flex items-center justify-between px-4 pt-4">
          <h2 className="font-semibold text-slate-900">Latest orders</h2>
          <Link to="/admin/orders" className="text-sm font-semibold text-brand-700">All orders</Link>
        </div>
        <div className="overflow-x-auto">
          <table className="mt-2 w-full min-w-[640px] text-sm">
            <thead><tr><th className={th}>Order</th><th className={th}>Customer</th><th className={th}>Placed</th><th className={th}>Status</th><th className={`${th} text-right`}>Total</th></tr></thead>
            <tbody>
              {d.recentOrders.map((o) => (
                <tr key={o.orderNumber} className="border-t border-slate-100 hover:bg-slate-50">
                  <td className={td}><Link to={`/admin/orders/${o.orderNumber}`} className="font-medium text-brand-700">{o.orderNumber}</Link></td>
                  <td className={td}>{o.customerName}<span className="block text-xs text-slate-500">{o.city}</span></td>
                  <td className={td}>{formatDateTime(o.placedAt)}</td>
                  <td className={td}><StatusBadge status={o.status} /></td>
                  <td className={`${td} text-right font-medium`}>{formatPrice(o.grandTotal)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  )
}
