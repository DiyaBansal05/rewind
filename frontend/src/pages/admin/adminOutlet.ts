import { useOutletContext } from 'react-router-dom'

export interface AdminOutletContext {
  /** Re-fetch the nav badge counts after a page changes something they count. */
  refreshBadges: () => void
}

export function useAdminOutlet() {
  return useOutletContext<AdminOutletContext>()
}
