import { Route, Routes } from 'react-router-dom'
import { RequireAdmin } from './auth/RequireAdmin'
import { RequireAuth } from './auth/RequireAuth'
import { AppShell } from './components/AppShell'
import { AdminModerationPage } from './pages/AdminModerationPage'
import { AdminUsersPage } from './pages/AdminUsersPage'
import { BrowsePage } from './pages/BrowsePage'
import { HomePage } from './pages/HomePage'
import { ListPage } from './pages/ListPage'
import { LoginPage } from './pages/LoginPage'
import { NotFound } from './pages/NotFound'
import { PersonPage } from './pages/PersonPage'
import { ProfilePage } from './pages/ProfilePage'
import { QuizPage } from './pages/QuizPage'
import { RegisterPage } from './pages/RegisterPage'
import { SearchPage } from './pages/SearchPage'
import { TitlePage } from './pages/TitlePage'
import { TonightPage } from './pages/TonightPage'
import { WatchlistPage } from './pages/WatchlistPage'

/**
 * The route table. Like a streaming service, the app shows only the sign-in screens until someone signs in:
 *   /login, /register        pages/LoginPage, RegisterPage   accounts, full-screen with film pictures (public)
 * Everything below is behind auth/RequireAuth and sits inside AppShell (header + footer):
 *   /                        pages/HomePage     hero and rows
 *   /browse                  pages/BrowsePage   filter by genre / year / rating
 *   /list/:category/:type    pages/ListPage     one full list ("See all")
 *   /search                  pages/SearchPage   movies, shows and people
 *   /title/:type/:id         pages/TitlePage    details, trailers, cast, similar titles
 *   /watchlist               pages/WatchlistPage             my list
 *   /profile                 pages/ProfilePage               my details, password, delete my account
 *   /admin/users             pages/AdminUsersPage            manage accounts (administrators only - auth/RequireAdmin)
 *   /admin/moderation        pages/AdminModerationPage       review comments the checks held back (administrators only)
 *   /person/:id              pages/PersonPage                actor / director profile and filmography
 *   /tonight                 pages/TonightPage               "what should I watch tonight?" recommender
 *   /play                    pages/QuizPage                  guess the film, show or speaker from a famous line; leaderboard
 */
export default function App() {
  return (
    <Routes>
      {/* the only pages a visitor can see: full-screen, without the header (components/AuthScreen) */}
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />

      {/* everything else: signed-in users only; a visitor is sent to /login and brought back afterwards */}
      <Route element={<RequireAuth />}>
        <Route element={<AppShell />}>
          <Route path="/" element={<HomePage />} />
          <Route path="/browse" element={<BrowsePage />} />
          <Route path="/list/:category/:type" element={<ListPage />} />
          <Route path="/search" element={<SearchPage />} />
          <Route path="/title/:type/:id" element={<TitlePage />} />
          <Route path="/tonight" element={<TonightPage />} />
          <Route path="/person/:id" element={<PersonPage />} />
          <Route path="/watchlist" element={<WatchlistPage />} />
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="/play" element={<QuizPage />} />
          <Route element={<RequireAdmin />}>
            <Route path="/admin/users" element={<AdminUsersPage />} />
            <Route path="/admin/moderation" element={<AdminModerationPage />} />
          </Route>
          <Route path="*" element={<NotFound />} />
        </Route>
      </Route>
    </Routes>
  )
}
