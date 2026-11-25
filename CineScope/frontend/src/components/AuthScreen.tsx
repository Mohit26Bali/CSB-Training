import type { ReactNode } from 'react'
import { ShowcaseCarousel } from './ShowcaseCarousel'

/**
 * The whole screen of /login and /register: the film pictures and famous lines (components/ShowcaseCarousel) on the left,
 * the form on the right - or, on a phone, the form over a dimmed picture. These two pages are the only ones outside the app
 * shell (components/AppShell), because a visitor sees nothing else until they sign in (auth/RequireAuth). The form itself
 * is components/AuthForm.
 */
export function AuthScreen({ children }: { children: ReactNode }) {
  return (
    <div className="relative min-h-screen lg:grid lg:grid-cols-[minmax(0,1.25fr)_minmax(0,1fr)]">
      <ShowcaseCarousel />
      <main className="relative z-10 flex min-h-screen items-center justify-center px-4 py-10">
        <div className="w-full max-w-md">{children}</div>
      </main>
    </div>
  )
}
