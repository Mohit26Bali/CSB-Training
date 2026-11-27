/**
 * CodeMirror measures text with layout APIs that jsdom does not implement. These stubs let the REAL editor run in tests (it still builds
 * its document, state, keymaps and DOM); they are not a replacement of the editor.
 */
export function installLayoutStubs() {
  const rect = { x: 0, y: 0, width: 0, height: 0, top: 0, left: 0, right: 0, bottom: 0, toJSON: () => ({}) }
  const rects = () => {
    const list = []
    list.item = () => null
    return list
  }
  if (typeof Range !== 'undefined') {
    Range.prototype.getClientRects = rects
    Range.prototype.getBoundingClientRect = () => rect
  }
  if (typeof Element !== 'undefined') Element.prototype.scrollIntoView = () => {}
  if (typeof document !== 'undefined' && !document.elementFromPoint) document.elementFromPoint = () => null
}
