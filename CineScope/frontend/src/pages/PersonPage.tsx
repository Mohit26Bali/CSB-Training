import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError, errorMessage } from '../api/client'
import { usePerson } from '../api/queries'
import type { PersonCredit, PersonDetail } from '../api/types'
import { Row } from '../components/Row'
import { TitleCard } from '../components/TitleCard'
import { Button, EmptyState, ErrorState, SectionTitle, Skeleton } from '../components/ui'
import { useDocumentTitle } from '../hooks'
import { ageFrom, formatDate, initials } from '../lib/format'
import { profileUrl } from '../lib/images'

/**
 * A person's page (route /person/:id): photo, facts (born, died, place, age), biography, their best-known titles and the
 * full filmography split into Acting and Crew. Reached from the cast and director names on a title page and from the
 * people in search results. All of it comes from one request (GET /api/people/{id}, api/queries.ts usePerson); every
 * filmography line links to that title's page (pages/TitlePage).
 */
export function PersonPage() {
  const params = useParams()
  const id = Number(params.id)
  const person = usePerson(id)
  useDocumentTitle(person.data?.name)

  if (!Number.isInteger(id) || id <= 0) {
    return <div className="px-4 pt-28"><EmptyState icon="film" title="That person doesn’t exist" hint="The link may be wrong." /></div>
  }
  if (person.isLoading) return <PersonSkeleton />
  if (person.isError) {
    const notFound = person.error instanceof ApiError && person.error.status === 404
    return (
      <div className="px-4 pt-28">
        {notFound ? (
          <EmptyState icon="film" title="Person not found" hint="They may have been removed from TMDB." />
        ) : (
          <ErrorState message={errorMessage(person.error)} onRetry={() => person.refetch()} />
        )}
      </div>
    )
  }
  return <Profile p={person.data!} />
}

function PersonSkeleton() {
  return (
    <div aria-busy="true" className="mx-auto grid max-w-7xl gap-8 px-4 pt-28 sm:px-8 md:grid-cols-[16rem_1fr]">
      <Skeleton className="aspect-2/3 w-48 md:w-full" />
      <div className="space-y-4">
        <Skeleton className="h-10 w-1/2" />
        <Skeleton className="h-32 w-full" />
      </div>
    </div>
  )
}

function Profile({ p }: { p: PersonDetail }) {
  const photo = profileUrl(p.profilePath, 'h632')
  const age = ageFrom(p.birthday, p.deathday)
  const facts: [string, string | null][] = [
    ['Known for', p.knownForDepartment],
    ['Born', p.birthday ? `${formatDate(p.birthday)}${age !== null && !p.deathday ? ` (age ${age})` : ''}` : null],
    ['Died', p.deathday ? `${formatDate(p.deathday)}${age !== null ? ` (aged ${age})` : ''}` : null],
    ['Place of birth', p.placeOfBirth],
  ]

  return (
    <div className="mx-auto max-w-7xl px-4 pb-6 pt-28 sm:px-8">
      <div className="grid gap-8 md:grid-cols-[16rem_1fr] md:gap-12">
        <aside className="mx-auto w-48 md:mx-0 md:w-full">
          {photo ? (
            <img src={photo} alt={`Photo of ${p.name}`} className="card-shadow aspect-2/3 w-full rounded-2xl object-cover ring-1 ring-line" />
          ) : (
            <div className="grid aspect-2/3 w-full place-items-center rounded-2xl bg-linear-to-br from-slate-600 to-slate-800 text-5xl font-bold text-white/70 ring-1 ring-line">{initials(p.name)}</div>
          )}
          <dl className="mt-6 space-y-4">
            {facts
              .filter(([, value]) => value)
              .map(([label, value]) => (
                <div key={label}>
                  <dt className="text-xs font-semibold uppercase tracking-wide text-faint">{label}</dt>
                  <dd className="mt-0.5 font-semibold text-ink">{value}</dd>
                </div>
              ))}
          </dl>
        </aside>

        <div className="min-w-0">
          <h1 className="text-3xl font-extrabold tracking-tight text-ink sm:text-5xl">{p.name}</h1>
          <Biography text={p.biography} name={p.name} />
        </div>
      </div>

      <div className="mt-12 space-y-14">
        {p.knownFor.length > 0 && (
          <Row title="Known for">
            {p.knownFor.map((credit) => (
              <TitleCard key={`${credit.type}-${credit.id}`} title={{ ...credit }} />
            ))}
          </Row>
        )}
        <Filmography acting={p.acting} crew={p.crew} />
      </div>
    </div>
  )
}

const COLLAPSED_CHARS = 700

/** The biography, cut after a few lines with "Read more" when it is long. */
function Biography({ text, name }: { text: string | null; name: string }) {
  const [open, setOpen] = useState(false)
  if (!text) return <p className="mt-6 text-muted">We don’t have a biography for {name} yet.</p>
  const long = text.length > COLLAPSED_CHARS
  return (
    <section aria-label="Biography" className="mt-6">
      <h2 className="text-lg font-bold text-ink">Biography</h2>
      <p className="mt-2 max-w-3xl whitespace-pre-line text-base leading-relaxed text-ink/85">{long && !open ? `${text.slice(0, COLLAPSED_CHARS).trimEnd()}…` : text}</p>
      {long && (
        <button type="button" onClick={() => setOpen((o) => !o)} aria-expanded={open} className="mt-2 text-sm font-semibold text-ember-400 hover:underline">
          {open ? 'Show less' : 'Read more'}
        </button>
      )}
    </section>
  )
}

const PAGE = 30

/** Acting and Crew lists (tabs), newest first; long careers show 30 lines and a "Show all" button. */
function Filmography({ acting, crew }: { acting: PersonCredit[]; crew: PersonCredit[] }) {
  const [tab, setTab] = useState<'acting' | 'crew'>(acting.length === 0 && crew.length > 0 ? 'crew' : 'acting')
  const [all, setAll] = useState(false)
  if (acting.length === 0 && crew.length === 0) return null
  const list = tab === 'acting' ? acting : crew
  const shown = all ? list : list.slice(0, PAGE)

  return (
    <section aria-label="Filmography">
      <SectionTitle>Filmography</SectionTitle>
      <div role="tablist" aria-label="Kind of work" className="mb-4 flex gap-2">
        {([['acting', 'Acting', acting.length], ['crew', 'Crew', crew.length]] as const)
          .filter(([, , count]) => count > 0)
          .map(([key, label, count]) => (
            <button
              key={key}
              role="tab"
              type="button"
              aria-selected={tab === key}
              onClick={() => {
                setTab(key)
                setAll(false)
              }}
              className={`rounded-full border px-4 py-2 text-sm font-semibold transition-colors ${
                tab === key ? 'border-ember-500 bg-ember-500 text-white' : 'border-line text-muted hover:border-ember-400 hover:text-ink'
              }`}
            >
              {label} <span className="ml-1 opacity-80">{count}</span>
            </button>
          ))}
      </div>
      <ul className="divide-y divide-line rounded-2xl border border-line bg-surface">
        {shown.map((credit) => (
          <li key={`${credit.type}-${credit.id}`}>
            <Link to={`/title/${credit.type}/${credit.id}`} className="flex items-baseline gap-4 px-4 py-3 hover:bg-ink/5">
              <span className="w-12 shrink-0 text-sm tabular-nums text-faint">{credit.year ?? '—'}</span>
              <span className="min-w-0 flex-1">
                <span className="block font-semibold text-ink">{credit.title}</span>
                {credit.role && <span className="block text-sm text-muted">{tab === 'acting' ? `as ${credit.role}` : credit.role}</span>}
              </span>
              <span className="shrink-0 text-xs text-faint">{credit.type === 'tv' ? 'TV' : 'Movie'}</span>
            </Link>
          </li>
        ))}
      </ul>
      {!all && list.length > PAGE && (
        <div className="mt-4 text-center">
          <Button variant="secondary" onClick={() => setAll(true)}>
            Show all {list.length}
          </Button>
        </div>
      )}
    </section>
  )
}
