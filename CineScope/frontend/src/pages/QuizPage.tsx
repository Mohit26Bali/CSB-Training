import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { errorMessage } from '../api/client'
import { useAnswerQuiz, useLeaderboard, useQuestionShown, useStartQuiz, useTitle } from '../api/queries'
import type { QuizAnswerResult, QuizCategory, QuizRound } from '../api/types'
import { Button, ErrorState, Skeleton } from '../components/ui'
import { useDocumentTitle } from '../hooks'
import { posterUrl } from '../lib/images'

/**
 * "Play" (route /play): guess the film, show or speaker from a famous line, from three of its actors, or from a blurred poster. Three screens in one page:
 *   choose  - seven games (famous lines in all four categories mixed or one at a time, the cast quiz, the blurred posters) and the
 *             leaderboard of the one being looked at;
 *   playing - ten questions, one at a time: the clue, four options, then the truth (poster, title, speaker) and the points;
 *   done    - the score, how many were right, the best streak, and the leaderboard with your place.
 *
 * Everything that matters is decided by the server (backend service/QuizService): the questions come without answers, an answer is
 * sent as the index of the option picked, and the reply says whether it was right, which one was, and where the quote is from. The
 * page only shows that. The poster comes from the ordinary title endpoint. Keys 1-4 pick an option and Enter goes on.
 *
 * The poster game shows the picture blurred and sharpens it in steps as seconds pass. The page tells the server when each poster is on
 * screen (POST .../shown) and the server, not this page, decides the speed bonus from its own clock (+5 within 6 seconds, +2 within
 * 12); the blur steps and the bonus shown here only follow the same thresholds so the player knows what to expect.
 */
/** Seconds after which the poster sharpens a step and the speed bonus shrinks; the same numbers as backend QuizService (FAST_SECONDS, SLOW_SECONDS). */
const FAST_SECONDS = 6
const SLOW_SECONDS = 12
const BLUR_STEPS = [20, 12, 6, 3] // pixels of blur at 0-6 s, 6-12 s, 12-18 s and later

function PosterClue({ roundId, seq, path, answered, title }: { roundId: number; seq: number; path: string; answered: boolean; title: string | null }) {
  // Mounted afresh for every question (key), so the clock starts at zero each time. On mount it tells the server the poster is on screen,
  // which starts the server's own clock for the speed bonus; the seconds counted here only drive the blur and the bonus shown.
  const { mutate: reportShown } = useQuestionShown()
  const [seconds, setSeconds] = useState(0)
  const reported = useRef(false) // React runs effects twice in development; the report must go out once per question
  useEffect(() => {
    if (!reported.current) {
      reported.current = true
      reportShown({ roundId, seq })
    }
    const started = Date.now()
    const timer = window.setInterval(() => setSeconds(Math.floor((Date.now() - started) / 1000)), 500)
    return () => window.clearInterval(timer)
  }, [roundId, seq, reportShown])

  const blur = answered ? 0 : BLUR_STEPS[Math.min(BLUR_STEPS.length - 1, Math.floor(seconds / 6))]
  const bonus = seconds < FAST_SECONDS ? 5 : seconds < SLOW_SECONDS ? 2 : 0
  const url = posterUrl(path, 'w500')
  return (
    <figure className="mt-3">
      <div className="mx-auto w-full max-w-xs overflow-hidden rounded-xl border border-line bg-surface-2">
        {url ? (
          <img data-testid="quiz-poster" data-blur={blur} src={url} alt={answered && title ? `${title} poster` : 'A blurred film or show poster'} style={{ filter: `blur(${blur}px)`, transition: 'filter 1s ease' }} className="aspect-2/3 w-full object-cover" />
        ) : (
          <div className="grid aspect-2/3 place-items-center text-sm text-faint">No picture</div>
        )}
      </div>
      {!answered && (
        <figcaption data-testid="quiz-bonus" className="mt-2 text-center text-sm text-muted">
          {bonus > 0 ? `Speed bonus right now: +${bonus}` : 'No speed bonus now, but the picture keeps sharpening'}
        </figcaption>
      )}
    </figure>
  )
}
const CATEGORIES: { value: QuizCategory; label: string; hint: string }[] = [
  { value: 'ALL', label: 'Mixed', hint: 'A bit of everything' },
  { value: 'HOLLYWOOD', label: 'Hollywood', hint: 'English-language films' },
  { value: 'TV', label: 'TV shows', hint: 'English-language series' },
  { value: 'BOLLYWOOD', label: 'Bollywood', hint: 'Hindi films' },
  { value: 'ANIME', label: 'Anime', hint: 'Films and series' },
  { value: 'CAST', label: 'Cast quiz', hint: 'Which film or show has these actors?' },
  { value: 'POSTER', label: 'Blurred posters', hint: 'Name it before it sharpens: quick answers earn a bonus' },
]

const categoryLabel = (c: QuizCategory | string) => CATEGORIES.find((x) => x.value === c)?.label ?? c

export function QuizPage() {
  useDocumentTitle('Play')
  const start = useStartQuiz()
  const [round, setRound] = useState<QuizRound | null>(null)
  const [summary, setSummary] = useState<QuizAnswerResult | null>(null)
  const [lastCategory, setLastCategory] = useState<QuizCategory>('ALL')

  function begin(category: QuizCategory) {
    setLastCategory(category)
    // The finished result stays on screen until the new round has arrived; clearing it first would flash the old round's first question.
    start.mutate(category, {
      onSuccess: (next) => {
        setSummary(null)
        setRound(next)
      },
    })
  }

  if (round && !summary) {
    return <Playing key={round.id} round={round} onFinished={setSummary} onQuit={() => setRound(null)} />
  }
  if (round && summary) {
    return <Summary round={round} result={summary} onAgain={() => begin(lastCategory)} onChoose={() => { setRound(null); setSummary(null) }} starting={start.isPending} />
  }
  return <Choose onPlay={begin} starting={start.isPending} startingCategory={start.variables} error={start.isError ? errorMessage(start.error) : null} />
}

// ---------------------------------------------------------------- choose

function Choose({ onPlay, starting, startingCategory, error }: { onPlay: (c: QuizCategory) => void; starting: boolean; startingCategory?: QuizCategory; error: string | null }) {
  const [board, setBoard] = useState<QuizCategory>('ALL')
  return (
    <div className="mx-auto max-w-4xl px-4 pb-12 pt-28 sm:px-8">
      <h1 className="text-3xl font-extrabold tracking-tight text-ink sm:text-4xl">Guess the line</h1>
      <p className="mt-1 text-sm text-muted">Ten famous lines. Name the film or show, or who says it. A right answer earns 10 points, and each right answer in a row adds more.</p>

      {error && (
        <p role="alert" className="mt-4 rounded-xl border border-ember-500/40 bg-ember-500/10 px-4 py-3 text-sm text-ink">
          {error}
        </p>
      )}

      <div role="group" aria-label="Choose a category" className="mt-6 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {CATEGORIES.map((c) => (
          <button
            key={c.value}
            type="button"
            data-testid={`quiz-category-${c.value}`}
            disabled={starting}
            onClick={() => onPlay(c.value)}
            className="rounded-2xl border border-line bg-surface p-5 text-left transition-colors hover:border-ember-400 disabled:opacity-60"
          >
            <span className="block text-lg font-bold text-ink">{c.label}</span>
            <span className="mt-1 block text-sm text-muted">{starting && startingCategory === c.value ? 'Getting your questions…' : c.hint}</span>
          </button>
        ))}
      </div>

      <h2 className="mt-12 text-xl font-bold text-ink">Leaderboard</h2>
      <div role="group" aria-label="Leaderboard category" className="mt-3 flex flex-wrap gap-2">
        {CATEGORIES.map((c) => (
          <button
            key={c.value}
            type="button"
            aria-pressed={board === c.value}
            onClick={() => setBoard(c.value)}
            className={`rounded-full border px-4 py-1.5 text-sm font-semibold transition-colors ${board === c.value ? 'border-ember-500 bg-ember-500 text-white' : 'border-line text-muted hover:border-ember-400 hover:text-ink'}`}
          >
            {c.label}
          </button>
        ))}
      </div>
      <Board category={board} />
    </div>
  )
}

function Board({ category }: { category: QuizCategory }) {
  const board = useLeaderboard(category)
  if (board.isLoading) return <Skeleton className="mt-4 h-40 w-full" />
  if (board.isError) return <ErrorState message={errorMessage(board.error)} onRetry={() => board.refetch()} />
  const data = board.data!
  if (data.entries.length === 0) {
    return <p className="mt-4 rounded-2xl border border-dashed border-line p-6 text-sm text-muted">Nobody has finished a {categoryLabel(category)} round yet. Be the first.</p>
  }
  return (
    <div className="mt-4">
      <ol data-testid="quiz-board" aria-label={`Best scores, ${categoryLabel(category)}`} className="divide-y divide-line overflow-hidden rounded-2xl border border-line bg-surface">
        {data.entries.map((row) => (
          <li key={row.username} data-you={row.you} className={`flex items-center gap-4 px-4 py-3 text-sm ${row.you ? 'bg-ember-500/10' : ''}`}>
            <span className="w-8 font-bold text-muted">{row.rank}</span>
            <span className="min-w-0 flex-1 truncate font-semibold text-ink">
              {row.displayName} <span className="font-normal text-faint">@{row.username}</span>
              {row.you && <span className="ml-2 rounded-full bg-ember-500 px-2 py-0.5 text-xs font-bold text-white">you</span>}
            </span>
            <span className="font-bold text-ink">{row.score}</span>
          </li>
        ))}
      </ol>
      {data.you && !data.entries.some((r) => r.you) && (
        <p className="mt-3 text-sm text-muted">
          Your best: <strong className="text-ink">{data.you.score}</strong> points, place {data.you.rank}.
        </p>
      )}
    </div>
  )
}

// ---------------------------------------------------------------- playing

function Playing({ round, onFinished, onQuit }: { round: QuizRound; onFinished: (last: QuizAnswerResult) => void; onQuit: () => void }) {
  const answer = useAnswerQuiz()
  const [index, setIndex] = useState(0)
  const [picked, setPicked] = useState<number | null>(null)
  const [result, setResult] = useState<QuizAnswerResult | null>(null)
  const [score, setScore] = useState(0)
  const [streak, setStreak] = useState(0)
  const question = round.questions[index]

  const choose = useCallback(
    (choice: number) => {
      if (result || answer.isPending) return
      setPicked(choice)
      answer.mutate(
        { roundId: round.id, seq: question.seq, choice },
        {
          onSuccess: (r) => {
            setResult(r)
            setScore(r.score)
            setStreak(r.streak)
          },
          onError: () => setPicked(null),
        },
      )
    },
    [answer, question.seq, result, round.id],
  )

  const next = useCallback(() => {
    if (!result) return
    if (result.finished) {
      onFinished(result)
      return
    }
    setIndex((i) => i + 1)
    setPicked(null)
    setResult(null)
  }, [onFinished, result])

  useEffect(() => {
    function onKey(event: KeyboardEvent) {
      if (event.ctrlKey || event.metaKey || event.altKey) return
      if (event.key >= '1' && event.key <= '4') choose(Number(event.key) - 1)
      // Enter on a focused button already clicks it (the Next button is focused when it appears): handling it here too would skip a question
      else if (event.key === 'Enter' && result && !(event.target instanceof HTMLElement && event.target.closest('button, a, input, textarea, select'))) next()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [choose, next, result])

  const optionClass = (i: number) => {
    const base = 'flex w-full items-center gap-3 rounded-xl border px-4 py-3 text-left text-base font-semibold transition-colors disabled:cursor-default'
    if (!result) return `${base} border-line bg-surface text-ink hover:border-ember-400 ${picked === i ? 'opacity-70' : ''}`
    if (i === result.correctIndex) return `${base} border-emerald-500 bg-emerald-500/15 text-ink`
    if (i === picked) return `${base} border-red-500 bg-red-500/15 text-ink`
    return `${base} border-line bg-surface text-muted opacity-60`
  }

  return (
    <div className="mx-auto max-w-3xl px-4 pb-12 pt-28 sm:px-8">
      <div className="flex flex-wrap items-center justify-between gap-3 text-sm">
        <span data-testid="quiz-progress" className="font-semibold text-muted">
          Question {index + 1} of {round.total} · {categoryLabel(round.category)}
        </span>
        <span className="flex items-center gap-4">
          <span className="text-muted">
            Score <strong data-testid="quiz-score" className="text-lg text-ink">{score}</strong>
          </span>
          <span className="text-muted">
            Streak <strong data-testid="quiz-streak" className="text-lg text-ink">{streak}</strong>
          </span>
        </span>
      </div>
      <div role="progressbar" aria-label="Round progress" aria-valuemin={0} aria-valuemax={round.total} aria-valuenow={index + (result ? 1 : 0)} className="mt-3 h-1.5 overflow-hidden rounded-full bg-line">
        <div className="h-full bg-ember-500 transition-all" style={{ width: `${((index + (result ? 1 : 0)) / round.total) * 100}%` }} />
      </div>

      <section aria-label="Question" data-testid="quiz-question" className="mt-8">
        <p className="text-sm font-semibold uppercase tracking-wide text-faint">{question.prompt}</p>
        {question.kind === 'CAST' ? (
          <ul data-testid="quiz-actors" aria-label="Actors" className="mt-3 space-y-1 text-2xl font-extrabold leading-snug tracking-tight text-ink sm:text-4xl">
            {(question.actors ?? []).map((actor) => (
              <li key={actor}>{actor}</li>
            ))}
          </ul>
        ) : question.kind === 'POSTER' ? (
          <PosterClue key={question.seq} roundId={round.id} seq={question.seq} path={question.posterPath ?? ''} answered={!!result} title={result?.reveal.title ?? null} />
        ) : (
          <blockquote data-testid="quiz-line" className="mt-3 text-2xl font-extrabold leading-snug tracking-tight text-ink sm:text-4xl">
            “{question.line}”
          </blockquote>
        )}

        <div role="group" aria-label="Options" className="mt-6 space-y-3">
          {question.options.map((option, i) => (
            <button key={option} type="button" data-testid={`quiz-option-${i}`} disabled={!!result || answer.isPending} onClick={() => choose(i)} className={optionClass(i)}>
              <span aria-hidden="true" className="grid size-7 shrink-0 place-items-center rounded-md border border-current/30 text-xs">
                {i + 1}
              </span>
              <span className="min-w-0 flex-1">{option}</span>
              {result && i === result.correctIndex && <span className="text-sm font-bold text-emerald-500">Right answer</span>}
              {result && i === picked && i !== result.correctIndex && <span className="text-sm font-bold text-red-500">Your answer</span>}
            </button>
          ))}
        </div>
      </section>

      {answer.isError && (
        <p role="alert" className="mt-4 rounded-xl border border-ember-500/40 bg-ember-500/10 px-4 py-3 text-sm text-ink">
          {errorMessage(answer.error)}
        </p>
      )}

      {result && <Reveal result={result} onNext={next} last={result.finished} />}

      {!result && (
        <button type="button" onClick={onQuit} className="mt-8 text-sm text-faint underline-offset-2 hover:text-ink hover:underline">
          Leave this round
        </button>
      )}
    </div>
  )
}

function Reveal({ result, onNext, last }: { result: QuizAnswerResult; onNext: () => void; last: boolean }) {
  const title = useTitle(result.reveal.mediaType, result.reveal.tmdbId)
  const poster = posterUrl(title.data?.posterPath ?? null, 'w185')
  return (
    <section aria-label="Answer" aria-live="polite" data-testid="quiz-reveal" className="mt-6 rounded-2xl border border-line bg-surface p-4 sm:p-5">
      <div className="flex gap-4">
        {poster && <img src={poster} alt="" className="h-28 w-auto shrink-0 rounded-lg" />}
        <div className="min-w-0">
          <p data-testid="quiz-verdict" className={`text-lg font-extrabold ${result.correct ? 'text-emerald-500' : 'text-red-500'}`}>
            {result.correct ? `Correct! +${result.points}` : 'Not this time'}
          </p>
          {result.speedBonus > 0 && (
            <p data-testid="quiz-speed-bonus" className="text-sm font-semibold text-emerald-500">
              Includes +{result.speedBonus} for answering quickly
            </p>
          )}
          <p className="mt-1 text-ink">
            {result.reveal.speaker && (
              <>
                <strong>{result.reveal.speaker}</strong> in{' '}
              </>
            )}
            <Link to={`/title/${result.reveal.mediaType}/${result.reveal.tmdbId}`} className="font-semibold text-ember-400 hover:underline">
              {result.reveal.title}
            </Link>{' '}
            ({result.reveal.year})
          </p>
          {result.correct && result.streak >= 2 && <p className="mt-1 text-sm text-muted">{result.streak} in a row.</p>}
        </div>
      </div>
      <Button className="mt-4" data-testid="quiz-next" onClick={onNext} autoFocus>
        {last ? 'See my result' : 'Next question'}
      </Button>
    </section>
  )
}

// ---------------------------------------------------------------- done

function Summary({ round, result, onAgain, onChoose, starting }: { round: QuizRound; result: QuizAnswerResult; onAgain: () => void; onChoose: () => void; starting: boolean }) {
  const verdict =
    result.correctCount === round.total ? 'A perfect round!' : result.correctCount >= 8 ? 'Superb.' : result.correctCount >= 5 ? 'Nicely done.' : 'Keep watching, you will get there.'
  return (
    <div className="mx-auto max-w-3xl px-4 pb-12 pt-28 sm:px-8">
      <section aria-label="Your result" data-testid="quiz-summary" className="rounded-2xl border border-line bg-surface p-6 text-center sm:p-8">
        <p className="text-sm font-semibold uppercase tracking-wide text-faint">{categoryLabel(round.category)} round finished</p>
        <p data-testid="quiz-final-score" className="mt-2 text-6xl font-extrabold text-ink">
          {result.score}
        </p>
        <p className="text-sm text-muted">points</p>
        <p className="mt-4 text-lg font-semibold text-ink">{verdict}</p>
        <p className="mt-1 text-sm text-muted">
          <span data-testid="quiz-correct-count">{result.correctCount}</span> of {round.total} right · best streak {result.bestStreak}
        </p>
        <div className="mt-6 flex flex-wrap justify-center gap-3">
          <Button onClick={onAgain} loading={starting} data-testid="quiz-again">
            Play {categoryLabel(round.category)} again
          </Button>
          <Button variant="secondary" onClick={onChoose}>
            Choose another category
          </Button>
        </div>
      </section>

      <h2 className="mt-10 text-xl font-bold text-ink">Leaderboard · {categoryLabel(round.category)}</h2>
      <Board category={round.category} />
    </div>
  )
}
