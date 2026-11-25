import { useState } from 'react'
import { errorMessage } from '../api/client'
import { useProviders } from '../api/queries'
import type { MediaType, WatchProvider } from '../api/types'
import { useAuth } from '../auth/useAuth'
import { logoUrl } from '../lib/images'
import { REGIONS, loadRegion, regionName, saveRegion } from '../lib/region'
import { Icon } from './Icon'
import { SectionTitle, Skeleton } from './ui'

/**
 * The "Where to watch" section of a title page (pages/TitlePage). Asks the backend which services offer the title in
 * one country (GET /api/titles/{type}/{id}/providers, api/queries.ts useProviders) and shows them in four groups:
 * Stream (subscription), Free, Rent and Buy. The country starts as India (or the signed-in user's own region), can be
 * changed with the selector and is remembered (lib/region.ts). The service logos link nowhere by themselves; the
 * "More options" link goes to TMDB's page for the title, which sends people on to each service. TMDB's availability
 * data comes from JustWatch, which TMDB's terms require us to credit.
 */
const GROUPS: { key: 'stream' | 'free' | 'rent' | 'buy'; label: string; hint: string }[] = [
  { key: 'stream', label: 'Stream', hint: 'included with a subscription' },
  { key: 'free', label: 'Free', hint: 'no payment needed, may have adverts' },
  { key: 'rent', label: 'Rent', hint: 'pay per viewing' },
  { key: 'buy', label: 'Buy', hint: 'pay once, keep it' },
]

export function WhereToWatch({ type, id }: { type: MediaType; id: number }) {
  const { session } = useAuth()
  const [region, setRegion] = useState(() => loadRegion(session?.region ?? 'IN'))
  const providers = useProviders(type, id, region)

  function change(next: string) {
    setRegion(next)
    saveRegion(next)
  }

  const data = providers.data
  const groups = data ? GROUPS.filter((g) => data[g.key].length > 0) : []

  return (
    <section aria-label="Where to watch">
      <SectionTitle
        action={
          <label className="flex items-center gap-2 text-sm text-muted">
            Country
            <select
              value={region}
              onChange={(e) => change(e.target.value)}
              className="h-10 rounded-xl border border-line bg-surface px-3 text-sm text-ink focus:border-ember-400 focus:outline-none"
            >
              {REGIONS.map((code) => (
                <option key={code} value={code}>
                  {regionName(code)}
                </option>
              ))}
            </select>
          </label>
        }
      >
        Where to watch
      </SectionTitle>

      {providers.isLoading ? (
        <div aria-busy="true" className="flex gap-3">
          {Array.from({ length: 4 }, (_, i) => (
            <Skeleton key={i} className="size-16" />
          ))}
        </div>
      ) : providers.isError ? (
        <p role="alert" className="text-sm text-muted">
          Couldn’t check where to watch. {errorMessage(providers.error)}{' '}
          <button type="button" onClick={() => providers.refetch()} className="font-semibold text-ember-400 hover:underline">
            Try again
          </button>
        </p>
      ) : groups.length === 0 ? (
        <p className="text-sm text-muted">No streaming, rental or purchase options are listed for {regionName(region)} yet.</p>
      ) : (
        <div className="space-y-5">
          {groups.map((group) => (
            <div key={group.key} role="group" aria-label={group.label}>
              <p className="mb-2 text-sm font-semibold text-ink">
                {group.label} <span className="font-normal text-faint">· {group.hint}</span>
              </p>
              <ul className="flex flex-wrap gap-3">
                {data![group.key].map((p) => (
                  <Provider key={p.id} provider={p} />
                ))}
              </ul>
            </div>
          ))}
        </div>
      )}

      <p className="mt-4 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-faint">
        {data?.link && (
          <a href={data.link} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 font-semibold text-ember-400 hover:underline">
            More options <Icon name="external" className="size-3.5" />
          </a>
        )}
        <span>Availability data provided by JustWatch.</span>
      </p>
    </section>
  )
}

function Provider({ provider }: { provider: WatchProvider }) {
  const logo = logoUrl(provider.logoPath)
  return (
    <li title={provider.name} className="w-16 text-center">
      <div className="size-16 overflow-hidden rounded-xl bg-surface ring-1 ring-line">
        {logo ? (
          <img src={logo} alt="" loading="lazy" className="size-full object-cover" />
        ) : (
          <span className="grid size-full place-items-center p-1 text-xs font-semibold text-muted">{provider.name}</span>
        )}
      </div>
      <span className="clamp-2 mt-1 block text-xs leading-tight text-muted">{provider.name}</span>
    </li>
  )
}
