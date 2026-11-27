import { EditorView } from '@codemirror/view'
import { cleanup, render, screen } from '@testing-library/react'
import { useState } from 'react'
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest'
import CodeEditor from './CodeEditor'
import { installLayoutStubs } from './testSupport'

beforeAll(installLayoutStubs)
afterEach(cleanup)

const viewOf = (container) => EditorView.findFromDOM(container.querySelector('.cm-editor'))
const text = (container) => viewOf(container).state.doc.toString()

function key(view, name) {
  view.contentDOM.dispatchEvent(new KeyboardEvent('keydown', { key: name, code: name, bubbles: true, cancelable: true }))
}

describe('CodeEditor (CodeMirror 6)', () => {
  it('starts with the given text, shows line numbers and is labelled for assistive technology', () => {
    const { container } = render(<CodeEditor value={'class A {\n}\n'} label="Java source code of the test" onChange={() => {}} />)
    expect(text(container)).toBe('class A {\n}\n')
    expect(container.querySelector('.cm-lineNumbers')).not.toBeNull()
    const content = container.querySelector('.cm-content')
    expect(content.getAttribute('aria-label')).toBe('Java source code of the test')
    expect(content.getAttribute('aria-multiline')).toBe('true')
    expect(content.getAttribute('spellcheck')).toBe('false')
  })

  it('highlights Java: keywords and strings get their own syntax classes', () => {
    const { container } = render(<CodeEditor value={'public class A { String s = "x"; }'} onChange={() => {}} />)
    // the highlighter wraps tokens in spans that carry generated classes; plain text would have none
    expect(container.querySelectorAll('.cm-line span[class]').length).toBeGreaterThan(2)
  })

  it('reports edits made by the user', () => {
    const onChange = vi.fn()
    const { container } = render(<CodeEditor value="a" onChange={onChange} />)
    const view = viewOf(container)
    view.dispatch({ changes: { from: 1, insert: 'b' }, userEvent: 'input.type' })
    expect(onChange).toHaveBeenLastCalledWith('ab')
  })

  it('replaces its document when the value is set from outside (the reset button), without echoing a change loop', () => {
    function Harness() {
      const [v, setV] = useState('one')
      return (
        <>
          <button onClick={() => setV('two')}>set</button>
          <CodeEditor value={v} onChange={setV} />
        </>
      )
    }
    const { container } = render(<Harness />)
    expect(text(container)).toBe('one')
    screen.getByText('set').click()
    return Promise.resolve().then(() => {
      expect(text(container)).toBe('two')
    })
  })

  it('a read-only editor does not report changes and is marked read-only', () => {
    const onChange = vi.fn()
    const { container } = render(<CodeEditor value="fixed" readOnly onChange={onChange} />)
    const view = viewOf(container)
    expect(view.state.readOnly).toBe(true)
    view.dispatch({ changes: { from: 0, insert: 'x' } })
    expect(onChange).not.toHaveBeenCalled()
  })

  it('Tab indents, but Escape followed by Tab leaves the editor alone so keyboard users are not trapped', () => {
    const { container } = render(<CodeEditor value="x" onChange={() => {}} />)
    const view = viewOf(container)
    view.dispatch({ selection: { anchor: 0 } })
    key(view, 'Tab')
    expect(text(container)).toBe('    x') // indented by four spaces
    view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: 'x' }, selection: { anchor: 0 } })
    key(view, 'Escape')
    key(view, 'Tab')
    expect(text(container)).toBe('x') // Tab was not consumed by the editor: the browser moves focus
    key(view, 'a') // any other key restores Tab-to-indent
    view.dispatch({ selection: { anchor: 0 } })
    key(view, 'Tab')
    expect(text(container)).toBe('    x')
  })

  it('removes its DOM when unmounted', () => {
    const { container, unmount } = render(<CodeEditor value="x" onChange={() => {}} />)
    expect(container.querySelector('.cm-editor')).not.toBeNull()
    unmount()
    expect(document.querySelector('.cm-editor')).toBeNull()
  })
})
