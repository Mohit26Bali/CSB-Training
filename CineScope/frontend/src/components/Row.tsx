import { useRef, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { Icon } from './Icon'
import { SectionTitle } from './ui'

/**
 * A titled horizontal scroller (Netflix-style row). Children are fixed-width cards that snap into place; the arrow
 * buttons (shown on hover on larger screens) scroll by most of the visible width, and touch devices simply swipe.
 * {@code seeAllTo} adds a "See all" link to the full list. Used for every row on the home page and for the cast and
 * similar-titles rows of the detail page.
 */
export function Row({ title, seeAllTo, children }: { title: string; seeAllTo?: string; children: ReactNode }) {
  const scroller = useRef<HTMLDivElement>(null)

  const scroll = (direction: -1 | 1) => {
    const el = scroller.current
    if (el) el.scrollBy({ left: direction * el.clientWidth * 0.85, behavior: 'smooth' })
  }

  return (
    <section aria-label={title} className="group/row">
      <SectionTitle
        action={
          seeAllTo && (
            <Link to={seeAllTo} className="shrink-0 text-sm font-semibold text-ember-400 hover:underline">
              See all
            </Link>
          )
        }
      >
        {title}
      </SectionTitle>
      <div className="relative">
        <div ref={scroller} className="no-scrollbar -mx-4 flex snap-x scroll-px-4 gap-3.5 overflow-x-auto px-4 pb-3 sm:-mx-8 sm:scroll-px-8 sm:gap-4 sm:px-8">
          {children}
        </div>
        <button
          type="button"
          onClick={() => scroll(-1)}
          aria-label={`Scroll ${title} left`}
          className="absolute -left-3 top-[38%] z-10 hidden size-11 -translate-y-1/2 place-items-center rounded-full bg-black/70 text-white opacity-0 shadow-lg backdrop-blur transition-opacity hover:bg-ember-500 focus-visible:opacity-100 md:grid md:group-hover/row:opacity-100"
        >
          <Icon name="chevronLeft" className="size-5" />
        </button>
        <button
          type="button"
          onClick={() => scroll(1)}
          aria-label={`Scroll ${title} right`}
          className="absolute -right-3 top-[38%] z-10 hidden size-11 -translate-y-1/2 place-items-center rounded-full bg-black/70 text-white opacity-0 shadow-lg backdrop-blur transition-opacity hover:bg-ember-500 focus-visible:opacity-100 md:grid md:group-hover/row:opacity-100"
        >
          <Icon name="chevronRight" className="size-5" />
        </button>
      </div>
    </section>
  )
}
