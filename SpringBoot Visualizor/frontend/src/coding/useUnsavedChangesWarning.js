import { useLayoutEffect } from 'react'

/**
 * Warns before unsaved work is thrown away, where a browser allows it:
 *  - closing the tab or reloading shows the browser's own prompt (beforeunload);
 *  - clicking an in-app link asks for confirmation first.
 * It cannot intercept the browser's Back button or a button that signs out; those leave without a prompt.
 */
export function useUnsavedChangesWarning(dirty, message = 'You have code that has not been submitted. Leave this page and lose it?') {
  // A layout effect, so the listener is added or removed in the same commit that changes `dirty`: there is no moment in which the page
  // already shows the saved state while a stale "unsaved" listener is still active.
  useLayoutEffect(() => {
    if (!dirty) return undefined
    const onBeforeUnload = (event) => {
      event.preventDefault()
      event.returnValue = ''
    }
    const onClick = (event) => {
      if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return
      const link = event.target instanceof Element ? event.target.closest('a[href]') : null
      if (!link || link.target === '_blank' || link.hasAttribute('download')) return
      const url = new URL(link.href, window.location.href)
      if (url.origin !== window.location.origin) return
      if (url.pathname === window.location.pathname && url.search === window.location.search) return
      if (!window.confirm(message)) {
        event.preventDefault()
        event.stopPropagation()
      }
    }
    window.addEventListener('beforeunload', onBeforeUnload)
    document.addEventListener('click', onClick, true) // capture: runs before the router's own link handler
    return () => {
      window.removeEventListener('beforeunload', onBeforeUnload)
      document.removeEventListener('click', onClick, true)
    }
  }, [dirty, message])
}
