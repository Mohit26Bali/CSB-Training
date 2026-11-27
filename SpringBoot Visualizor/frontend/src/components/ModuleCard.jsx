import { ButtonLink } from './ui/Button'
import Card from './ui/Card'
import Icon from './ui/Icon'
import StatusBadge from './ui/StatusBadge'

/**
 * One learning module on the dashboard. Optional facts (`meta`, `lastOpened`) are shown only when the caller has real
 * data for them; an upcoming module has no action and says so.
 */
export default function ModuleCard({ module, meta, lastOpened = false }) {
  const available = module.status === 'available'
  return (
    <Card
      as="article"
      data-testid={`module-${module.id}`}
      className={`flex h-full flex-col transition focus-within:ring-2 focus-within:ring-accent ${
        available ? 'hover:-translate-y-0.5 hover:border-accent-line hover:shadow-md motion-reduce:transform-none' : 'bg-elevated/70'
      }`}
    >
      <div className="flex items-start justify-between gap-3">
        <span
          className={`flex h-10 w-10 items-center justify-center rounded-lg ${
            available ? 'bg-accent-soft text-accent' : 'bg-hover text-muted'
          }`}
        >
          <Icon name={module.icon} className="h-5 w-5" />
        </span>
        {available ? <StatusBadge tone="success">Available</StatusBadge> : <StatusBadge>Upcoming</StatusBadge>}
      </div>
      <h3 className={`mt-3 text-base font-semibold ${available ? 'text-fg' : 'text-fg-2'}`}>{module.title}</h3>
      <p className="mt-1 flex-1 text-sm text-fg-2">{module.description}</p>
      {meta && <p className="mt-2 text-xs text-muted">{meta}</p>}
      <div className="mt-4">
        {available ? (
          <ButtonLink to={module.path} variant={lastOpened ? 'primary' : 'secondary'} className="w-full sm:w-auto">
            {lastOpened ? 'Continue learning' : 'Start learning'}
            <Icon name="arrow" className="h-4 w-4" />
          </ButtonLink>
        ) : (
          <p className="text-sm text-muted">Coming in a later phase. Not available yet.</p>
        )}
      </div>
    </Card>
  )
}
