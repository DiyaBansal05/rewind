/** Small display helpers shared by admin and student pages. All times show in the viewer's locale. */

const MINUTE = 60_000
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR

/** 4512 -> "1:15:12", 312 -> "5:12" */
export function formatClock(totalSeconds: number): string {
  const s = Math.max(0, Math.floor(totalSeconds))
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = String(s % 60).padStart(2, '0')
  return h > 0 ? `${h}:${String(m).padStart(2, '0')}:${sec}` : `${m}:${sec}`
}

/** 4512 -> "1h 15m", 312 -> "5m" */
export function formatDuration(totalSeconds: number): string {
  const minutes = Math.round(totalSeconds / 60)
  if (minutes < 60) return `${minutes}m`
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return m === 0 ? `${h}h` : `${h}h ${String(m).padStart(2, '0')}m`
}

/** Minutes -> "3h 20m" / "45m" / "2d 4h" */
export function formatMinutes(minutes: number): string {
  if (minutes < 60) return `${minutes}m`
  if (minutes < 24 * 60) return formatDuration(minutes * 60)
  const d = Math.floor(minutes / (24 * 60))
  const h = Math.floor((minutes % (24 * 60)) / 60)
  return h === 0 ? `${d}d` : `${d}d ${h}h`
}

/** "12 min ago", "3 h ago", "Yesterday", "29 Oct" */
export function timeAgo(iso: string, now = Date.now()): string {
  const then = new Date(iso).getTime()
  const diff = now - then
  if (diff < MINUTE) return 'Just now'
  if (diff < HOUR) return `${Math.floor(diff / MINUTE)} min ago`
  if (diff < DAY) return `${Math.floor(diff / HOUR)} h ago`
  if (diff < 2 * DAY) return 'Yesterday'
  if (diff < 7 * DAY) return `${Math.floor(diff / DAY)} days ago`
  return formatShortDate(iso)
}

/** Time left until `iso`, e.g. "1 d 4 h", "5 h", "20 min"; null once passed. */
export function timeUntil(iso: string, now = Date.now()): string | null {
  const diff = new Date(iso).getTime() - now
  if (diff <= 0) return null
  if (diff < HOUR) return `${Math.max(1, Math.floor(diff / MINUTE))} min`
  if (diff < DAY) return `${Math.floor(diff / HOUR)} h`
  const d = Math.floor(diff / DAY)
  const h = Math.floor((diff % DAY) / HOUR)
  return h === 0 ? `${d} d` : `${d} d ${h} h`
}

/** "2026-10-29" (a calendar date, no time zone) -> "Wed, 29 Oct" */
export function formatClassDate(isoDate: string, withWeekday = true): string {
  const [y, m, d] = isoDate.split('-').map(Number)
  const date = new Date(y, m - 1, d)
  return date.toLocaleDateString(undefined, withWeekday
    ? { weekday: 'short', day: 'numeric', month: 'short' }
    : { day: 'numeric', month: 'short' })
}

export function formatShortDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short' })
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString(undefined, { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })
}

/** Local calendar date as "YYYY-MM-DD" (not UTC, unlike toISOString). */
export function toIsoDate(date: Date): string {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}
