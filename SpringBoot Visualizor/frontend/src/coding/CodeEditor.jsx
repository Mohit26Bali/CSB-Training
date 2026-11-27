import { indentLess, indentMore, history, historyKeymap, defaultKeymap } from '@codemirror/commands'
import { java } from '@codemirror/lang-java'
import { bracketMatching, HighlightStyle, indentUnit, syntaxHighlighting } from '@codemirror/language'
import { EditorState } from '@codemirror/state'
import { drawSelection, EditorView, highlightActiveLine, highlightActiveLineGutter, keymap, lineNumbers } from '@codemirror/view'
import { tags as t } from '@lezer/highlight'
import { useEffect, useRef } from 'react'

/**
 * A Java 17 source editor on CodeMirror 6. Every colour comes from the application's design tokens (CSS variables), so it follows the
 * Graphite + Lime theme and cannot drift from it; no colour is written here.
 *
 * Keyboard: Tab indents, which a code editor needs, but that must not trap keyboard users. Press Escape and the NEXT Tab moves focus out of
 * the editor (the standard CodeMirror accessibility pattern); any other key restores Tab-to-indent.
 *
 * It is a controlled component: `value` is the source of truth, `onChange` receives the new text. Setting `value` from outside (the reset
 * button) replaces the document.
 */
const highlight = HighlightStyle.define([
  { tag: [t.keyword, t.modifier, t.controlKeyword, t.definitionKeyword, t.operatorKeyword, t.moduleKeyword], color: 'var(--syntax-keyword)' },
  { tag: [t.string, t.character, t.special(t.string)], color: 'var(--syntax-string)' },
  { tag: [t.number, t.bool, t.null, t.atom, t.integer, t.float], color: 'var(--syntax-number)' },
  { tag: [t.typeName, t.className, t.namespace, t.annotation], color: 'var(--syntax-type)' },
  { tag: [t.function(t.variableName), t.function(t.propertyName), t.definition(t.function(t.variableName))], color: 'var(--syntax-function)' },
  { tag: [t.comment, t.lineComment, t.blockComment], color: 'var(--syntax-comment)', fontStyle: 'italic' },
  { tag: [t.punctuation, t.bracket, t.operator, t.separator, t.derefOperator], color: 'var(--syntax-punctuation)' },
  { tag: t.invalid, color: 'var(--syntax-error)' },
])

const theme = EditorView.theme(
  {
    '&': { color: 'var(--color-text-primary)', backgroundColor: 'var(--color-bg-code)', fontSize: '0.875rem', height: '100%' },
    '.cm-scroller': { fontFamily: 'var(--font-mono-stack)', lineHeight: '1.6', overflow: 'auto' },
    '.cm-content': { caretColor: 'var(--color-accent-primary)', padding: '0.5rem 0' },
    '.cm-line': { padding: '0 0.75rem' },
    '.cm-cursor, .cm-dropCursor': { borderLeftColor: 'var(--color-accent-primary)', borderLeftWidth: '2px' },
    '.cm-gutters': { backgroundColor: 'var(--color-bg-code-header)', color: 'var(--color-text-muted)', border: 'none', borderRight: '1px solid var(--color-border-subtle)' },
    '.cm-activeLine': { backgroundColor: 'var(--color-bg-surface)' },
    '.cm-activeLineGutter': { backgroundColor: 'var(--color-bg-hover)', color: 'var(--color-text-primary)' },
    '&.cm-focused': { outline: '2px solid var(--color-border-focus)', outlineOffset: '-2px' },
    '&.cm-focused > .cm-scroller > .cm-selectionLayer .cm-selectionBackground, .cm-selectionBackground': { backgroundColor: 'var(--color-accent-border)' },
    '.cm-matchingBracket': { backgroundColor: 'var(--color-accent-subtle)', outline: '1px solid var(--color-accent-border)' },
  },
  { dark: true },
)

const prefersReducedMotion = () => typeof window !== 'undefined' && typeof window.matchMedia === 'function' && window.matchMedia('(prefers-reduced-motion: reduce)').matches

export default function CodeEditor({ value, onChange, readOnly = false, label = 'Java source code', className = '', testId = 'code-editor' }) {
  const host = useRef(null)
  const view = useRef(null)
  const latestOnChange = useRef(onChange)
  latestOnChange.current = onChange

  useEffect(() => {
    let tabLeavesEditor = false
    const state = EditorState.create({
      doc: value ?? '',
      extensions: [
        lineNumbers(),
        highlightActiveLine(),
        highlightActiveLineGutter(),
        history(),
        drawSelection({ cursorBlinkRate: prefersReducedMotion() ? 0 : 1200 }),
        bracketMatching(),
        indentUnit.of('    '),
        EditorState.tabSize.of(4),
        java(),
        syntaxHighlighting(highlight),
        theme,
        keymap.of([
          {
            key: 'Escape',
            run: () => {
              tabLeavesEditor = true
              return true
            },
          },
          { key: 'Tab', run: (v) => (tabLeavesEditor ? false : indentMore(v)), shift: (v) => (tabLeavesEditor ? false : indentLess(v)) },
          ...defaultKeymap,
          ...historyKeymap,
        ]),
        EditorView.domEventHandlers({
          keydown: (event) => {
            if (event.key !== 'Escape' && event.key !== 'Tab') tabLeavesEditor = false
            return false
          },
          blur: () => {
            tabLeavesEditor = false
            return false
          },
        }),
        EditorView.contentAttributes.of({ 'aria-label': label, 'aria-multiline': 'true', spellcheck: 'false', autocapitalize: 'off', autocorrect: 'off' }),
        EditorState.readOnly.of(readOnly),
        EditorView.updateListener.of((update) => {
          if (update.docChanged && !readOnly) latestOnChange.current?.(update.state.doc.toString())
        }),
      ],
    })
    view.current = new EditorView({ state, parent: host.current })
    return () => {
      view.current?.destroy()
      view.current = null
    }
    // The editor is created once per mount (and again if the read-only mode or label changes); the text is synchronised below.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [readOnly, label])

  useEffect(() => {
    const v = view.current
    if (v && value !== undefined && v.state.doc.toString() !== value) {
      v.dispatch({ changes: { from: 0, to: v.state.doc.length, insert: value } })
    }
  }, [value, readOnly, label])

  return <div ref={host} data-testid={testId} className={`overflow-hidden rounded-md border border-line-strong bg-code ${className}`} />
}
