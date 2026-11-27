import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { applicationApi, programApi } from '../../api/endpoints'
import type { Application, Program } from '../../api/types'
import { Button, EmptyState, ErrorState, formatMoney, LoadingState, PageHeader, StatusBadge, Tag } from '../../components/ui'
import { daysLeft } from '../../eligibility'
import { ApplyModal } from './ApplyModal'

function Deadline({ program }: { program: Program }) {
  const days = daysLeft(program.deadline)
  if (!program.open) return <span className="text-xs font-medium text-rose-700">Closed on {program.deadline}</span>
  const text = days === 0 ? 'Closes today' : days === 1 ? 'Closes tomorrow' : `Closes in ${days} days`
  return <span className={`text-xs font-medium ${days <= 7 ? 'text-amber-700' : 'text-slate-500'}`}>{text}</span>
}

export function ProgramsPage() {
  const [applying, setApplying] = useState<Program | null>(null)
  const programs = useQuery({ queryKey: ['programs'], queryFn: programApi.list })
  const mine = useQuery({ queryKey: ['applications', 'mine'], queryFn: applicationApi.mine })

  const byProgram = new Map<number, Application>((mine.data ?? []).map((a) => [a.programId, a]))
  // open scholarships first, closed ones after
  const sorted = [...(programs.data ?? [])].sort((a, b) => Number(b.open) - Number(a.open))

  return (
    <>
      <PageHeader title="Scholarships" subtitle="Apply once for each scholarship you are eligible for." />

      {programs.isPending ? (
        <LoadingState />
      ) : programs.isError ? (
        <ErrorState message={errorMessage(programs.error)} onRetry={() => programs.refetch()} />
      ) : sorted.length === 0 ? (
        <EmptyState title="No scholarships are available right now" hint="Check back soon." />
      ) : (
        <ul className="grid gap-4 md:grid-cols-2">
          {sorted.map((p) => {
            const application = byProgram.get(p.id)
            return (
              <li key={p.id} className={`flex flex-col rounded-xl border border-slate-200 bg-white p-5 shadow-sm ${p.open ? '' : 'opacity-75'}`}>
                <div className="flex items-start justify-between gap-3">
                  <h2 className="font-semibold text-slate-900">{p.name}</h2>
                  <p className="text-lg font-semibold tabular-nums text-brand-700">{formatMoney(p.amount)}</p>
                </div>
                <p className="mt-2 flex-1 text-sm text-slate-600">{p.description ?? 'No description provided.'}</p>
                <div className="mt-3 flex flex-wrap gap-2">
                  <Tag tone="green">CGPA ≥ {p.minGpa.toFixed(2)}</Tag>
                  <Tag>Income ≤ {formatMoney(p.maxFamilyIncome)}</Tag>
                </div>
                <div className="mt-4 flex items-center justify-between gap-3">
                  <Deadline program={p} />
                  {application ? (
                    <span className="flex items-center gap-3">
                      <StatusBadge status={application.status} />
                      <Link to="/applications" className="text-sm font-medium text-brand-700 hover:underline">
                        View
                      </Link>
                    </span>
                  ) : (
                    <Button small disabled={!p.open} onClick={() => setApplying(p)}>
                      {p.open ? 'Apply' : 'Closed'}
                    </Button>
                  )}
                </div>
              </li>
            )
          })}
        </ul>
      )}

      {applying && <ApplyModal program={applying} onClose={() => setApplying(null)} />}
    </>
  )
}
