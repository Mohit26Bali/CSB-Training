import { useParams, useSearchParams } from 'react-router-dom'
import { api, type AssignmentDetail } from '../api'
import { useAsync } from '../hooks'
import { ErrorNotice, Loading } from '../components/Feedback'
import { OverviewTab } from '../tabs/OverviewTab'
import { SchemaTab } from '../tabs/SchemaTab'
import { DataTab } from '../tabs/DataTab'
import { QueriesTab } from '../tabs/QueriesTab'
import { ScriptsTab } from '../tabs/ScriptsTab'
import { NegativesTab } from '../tabs/NegativesTab'
import { PlaygroundTab } from '../tabs/PlaygroundTab'

const TABS = [
  { key: 'overview', label: 'Overview' },
  { key: 'schema', label: 'Schema' },
  { key: 'data', label: 'Data' },
  { key: 'queries', label: 'Queries' },
  { key: 'scripts', label: 'Scripts' },
  { key: 'negative', label: 'Must fail' },
  { key: 'playground', label: 'Playground' },
] as const
type TabKey = (typeof TABS)[number]['key']

function TabBody({ tab, a }: { tab: TabKey; a: AssignmentDetail }) {
  switch (tab) {
    case 'overview': return <OverviewTab a={a} />
    case 'schema': return <SchemaTab a={a} />
    case 'data': return <DataTab a={a} />
    case 'queries': return <QueriesTab a={a} />
    case 'scripts': return <ScriptsTab a={a} />
    case 'negative': return <NegativesTab a={a} />
    case 'playground': return <PlaygroundTab a={a} />
  }
}

export function AssignmentPage() {
  const { id = '' } = useParams()
  const [params, setParams] = useSearchParams()
  const tab = (TABS.find((t) => t.key === params.get('tab'))?.key ?? 'overview') as TabKey
  const detail = useAsync(() => api.assignment(id), [id])

  if (detail.loading && !detail.data) return <Loading label="Loading assignment" />
  if (detail.error) return <ErrorNotice message={detail.error} onRetry={detail.reload} />
  const a = detail.data!

  return (
    <>
      <p className="crumb">Assignment {a.id} · {a.category}</p>
      <h1>{a.title}</h1>
      <p className="meta">Database <code>{a.originalDb}</code> · {a.tables.length} tables · {a.queries.length} queries · {a.negatives.length} must-fail tests</p>
      <div role="tablist" aria-label="Assignment sections" className="tabs">
        {TABS.map((t) => (
          <button
            key={t.key}
            role="tab"
            id={`tab-${t.key}`}
            aria-selected={tab === t.key}
            aria-controls="tabpanel"
            className={tab === t.key ? 'tab active' : 'tab'}
            onClick={() => setParams(t.key === 'overview' ? {} : { tab: t.key })}
          >
            {t.label}
          </button>
        ))}
      </div>
      <section id="tabpanel" role="tabpanel" aria-labelledby={`tab-${tab}`} className="panel" key={`${a.id}-${tab}`}>
        <TabBody tab={tab} a={a} />
      </section>
    </>
  )
}
