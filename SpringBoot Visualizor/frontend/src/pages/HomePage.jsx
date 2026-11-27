import { Link } from 'react-router-dom'
import { HERO_HOME } from '../assets/heroImages'
import ModuleCard from '../components/ModuleCard'
import { ButtonLink } from '../components/ui/Button'
import Card from '../components/ui/Card'
import { MODULES } from '../learning/modules'
import ModeBadge from '../learning/components/ModeBadge'

/** The public landing page. Nothing here needs an account or a backend call. */
export default function HomePage() {
  return (
    <div className="min-h-screen bg-app text-fg">
      <header className="absolute inset-x-0 top-0 z-10">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-4 sm:px-6">
          <Link to="/welcome" className="flex items-center gap-2 text-lg font-bold">
            <span className="flex h-7 w-7 items-center justify-center rounded-md bg-accent text-sm text-on-accent" aria-hidden="true">S</span>
            SpringLab
          </Link>
          <nav aria-label="Account" className="flex items-center gap-2">
            <ButtonLink to="/login" variant="ghost" size="sm">Log in</ButtonLink>
            <ButtonLink to="/register" size="sm">Register</ButtonLink>
          </nav>
        </div>
      </header>

      <section className="relative overflow-hidden border-b border-line">
        <img src={HERO_HOME.src} alt="" className="absolute inset-0 h-full w-full object-cover" />
        <div className="absolute inset-0 bg-gradient-to-r from-app via-app/85 to-app/40" />
        <div className="relative mx-auto max-w-6xl px-4 pb-20 pt-32 sm:px-6 sm:pb-28 sm:pt-40">
          <p className="text-sm font-semibold uppercase tracking-wide text-accent">Interactive Spring Boot learning</p>
          <h1 className="mt-3 max-w-3xl text-4xl font-bold leading-tight sm:text-5xl">Learn Spring Boot by watching it run.</h1>
          <p className="mt-4 max-w-2xl text-lg text-fg-2">
            Follow a real request through Spring Security, Spring MVC and Hibernate. Break a JWT on purpose. Watch the SQL Hibernate prepares
            when you change a relationship. Every step says whether it was observed, inferred or only explained.
          </p>
          <div className="mt-8 flex flex-wrap gap-3">
            <ButtonLink to="/register">Get started</ButtonLink>
            <ButtonLink to="/login" variant="secondary">I already have an account</ButtonLink>
          </div>
          <p className="mt-10 text-xs text-muted">{HERO_HOME.credit}</p>
        </div>
      </section>

      <main className="mx-auto max-w-6xl space-y-14 px-4 py-14 sm:px-6">
        <section aria-labelledby="modules-title" className="space-y-4">
          <div>
            <h2 id="modules-title" className="text-2xl font-semibold">Three modules, one idea</h2>
            <p className="mt-1 text-fg-2">Each lesson moves through six short stages: understand, visualize, experiment, observe, explain, test.</p>
          </div>
          <div className="grid gap-4 md:grid-cols-3">
            {MODULES.map((m) => (
              <ModuleCard key={m.id} module={m} />
            ))}
          </div>
        </section>

        <section aria-labelledby="honest-title" className="space-y-4">
          <div>
            <h2 id="honest-title" className="text-2xl font-semibold">No guessing what is real</h2>
            <p className="mt-1 text-fg-2">Every step carries a badge, and the badge only means one thing.</p>
          </div>
          <div className="grid gap-4 md:grid-cols-3">
            {[
              ['CAPTURED', 'Observed while a real request or experiment ran against the real application and database.'],
              ['DERIVED', 'Worked out from observed facts and SpringLab code. The step says what was inferred.'],
              ['CONCEPTUAL', 'An explanation of how the framework works. Nothing about it was observed.'],
            ].map(([provenance, text]) => (
              <Card key={provenance} className="space-y-2">
                <ModeBadge provenance={provenance} />
                <p className="text-sm text-fg-2">{text}</p>
              </Card>
            ))}
          </div>
        </section>
      </main>

      <footer className="border-t border-line bg-sidebar">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-2 px-4 py-6 text-sm text-muted sm:px-6">
          <span>SpringLab: a learning platform for Java and Spring Boot.</span>
          <span>{HERO_HOME.credit}</span>
        </div>
      </footer>
    </div>
  )
}
