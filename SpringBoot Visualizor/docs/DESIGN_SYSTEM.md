# SpringLab design system: Graphite + Lime (dark)

One global dark theme. `frontend/src/index.css` is the single source of truth: CSS custom properties in `:root` (the specified token
names, for example `--color-bg-surface`, `--color-accent-primary`, `--diagram-security`) and short Tailwind 4 aliases in `@theme inline`
(`bg-surface`, `text-fg`, `border-line`, `bg-accent`, `text-captured`, `border-layer-security/45`, ...). Components use the aliases;
`designSystem.test.js` fails if a raw palette class (`bg-white`, `text-slate-500`, `indigo-*`, ...) or a hex colour appears in a component.

## Tokens in use

| Purpose | Utilities | Notes |
|---|---|---|
| Page and surfaces | `bg-app`, `bg-sidebar`, `bg-surface`, `bg-elevated`, `bg-subtle`, `bg-hover`, `bg-code` | cards `bg-surface` + `border-line`, 12px radius (`rounded-xl`), no shadow; popovers and menus `bg-elevated` |
| Text | `text-fg`, `text-fg-2`, `text-muted`, `text-on-accent` | `text-on-accent` is the dark text for lime and status fills |
| Borders | `border-line`, `border-line-strong`, `border-line-subtle`, `border-focus` | form controls use `--color-border-input` (3:1) |
| Accent | `bg-accent`, `text-accent`, `bg-accent-hover`, `bg-accent-active`, `bg-accent-soft`, `text-accent-2` | lime for the primary action, the active navigation item and the current diagram step; not for every heading |
| Status | `text-success/warning/error/info`, `bg-*-soft`, `border-*-line` | indicators and labels, never large fills |
| Provenance | `text-captured/derived/conceptual`, `bg-*-soft` | CAPTURED = LIVE (green), DERIVED = INFERRED (amber), CONCEPTUAL = SIMULATED (violet, dashed). The meanings are fixed. |
| Architecture layers | `layer-client / mvc / security / business / persistence / database` | identify where a component lives; never used for execution state |
| Syntax | `--syntax-*` (used by the code inspector) | restrained, all >= 4.5:1 on `bg-code` |

Diagram execution state is separate from layer colour: current = lime border and the word **Now**; completed = green and **Passed**;
a refusal = red and **Rejected**; upcoming = muted; not reached = dashed and **Not reached**. Edges use lime (active), green (completed),
red (rejection branch) and muted (upcoming); the arrowhead shows direction.

## Deliberate deviations from the brief (both measured, both in `index.css`)

* `--color-text-muted` is `#98a397`, not `#879187`. The specified value measures 4.23:1 on elevated and 3.70:1 on subtle surfaces (AA needs 4.5:1).
* `--color-border-input` (`#738171`) is added for form-control outlines because `--color-border-strong` measures 2.39:1 (WCAG 1.4.11 needs 3:1).
  `border-strong` remains for decorative borders.

## Accessibility measures

Contrast is computed from the tokens in `designSystem.test.js` (text, accent and status colours on every surface, dark text on every solid fill,
syntax colours on the code panel, control outlines and focus colour). One visible focus ring everywhere (`:focus-visible`, lime). Colour is never the
only signal: provenance badges carry a label and an icon (simulated is also dashed), diagram states carry a word, validation errors carry text.
`prefers-reduced-motion` stops packets, transitions and smooth scrolling. Diagrams have a text alternative, a caption and the step list.

## Layout rules (why the pages are shorter)

* A lesson shows **one stage at a time** (stage bar, Previous and Next, `#stage-...` in the address). In-page links such as `#experiment-...` open the stage that owns them.
* Inside a walkthrough the explanation stays visible; **All steps** and **Source code** are collapsed `<details>` sections until opened.
* The header has five items: Dashboard, Learn (menu with the three lessons), Challenge Arena, Progress, and Manage (staff only), plus an account menu.

## Images

Photographs come from Pexels (credits in `IMAGE_CREDITS.md`). They are decorative (empty `alt`), sit under a dark overlay, and the API key is never stored in the project.
