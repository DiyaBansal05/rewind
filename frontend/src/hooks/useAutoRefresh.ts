import { useEffect, useRef } from 'react'

/**
 * Runs `refresh` on mount, then again whenever the tab regains focus or
 * becomes visible. Registrations, requests, and approvals often happen in a
 * different tab/device (a student scanning a QR, an admin approving from
 * their phone) -- without this, a page just goes silently stale until a
 * manual reload.
 *
 * Always calls the latest `refresh`, so pages whose refresh depends on
 * state (filters, the selected tab) don't re-fetch with stale values. Pass
 * that state as `deps` to also refresh whenever it changes.
 */
export function useAutoRefresh(refresh: () => void, deps: readonly unknown[] = []) {
  const latest = useRef(refresh)
  latest.current = refresh
  const mounted = useRef(false)

  useEffect(() => {
    if (!mounted.current) {
      mounted.current = true
      return
    }
    latest.current()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)

  useEffect(() => {
    latest.current()

    function onFocus() {
      latest.current()
    }
    function onVisibilityChange() {
      if (document.visibilityState === 'visible') latest.current()
    }
    window.addEventListener('focus', onFocus)
    document.addEventListener('visibilitychange', onVisibilityChange)
    return () => {
      window.removeEventListener('focus', onFocus)
      document.removeEventListener('visibilitychange', onVisibilityChange)
    }
  }, [])
}
