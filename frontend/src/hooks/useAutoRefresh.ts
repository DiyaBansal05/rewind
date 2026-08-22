import { useEffect } from 'react'

/**
 * Runs `refresh` on mount, then again whenever the tab regains focus or
 * becomes visible. Registrations, requests, and approvals often happen in a
 * different tab/device (a student scanning a QR, an admin approving from
 * their phone) -- without this, a page just goes silently stale until a
 * manual reload.
 */
export function useAutoRefresh(refresh: () => void) {
  useEffect(() => {
    refresh()

    function onFocus() {
      refresh()
    }
    function onVisibilityChange() {
      if (document.visibilityState === 'visible') refresh()
    }
    window.addEventListener('focus', onFocus)
    document.addEventListener('visibilitychange', onVisibilityChange)
    return () => {
      window.removeEventListener('focus', onFocus)
      document.removeEventListener('visibilitychange', onVisibilityChange)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
}
