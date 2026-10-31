import { DAYS_OF_WEEK, type DayOfWeek, type WeeklySlot } from '../api/types'

export const DAY_SHORT: Record<DayOfWeek, string> = {
  MONDAY: 'Mon', TUESDAY: 'Tue', WEDNESDAY: 'Wed', THURSDAY: 'Thu', FRIDAY: 'Fri', SATURDAY: 'Sat', SUNDAY: 'Sun',
}

export const DAY_LONG: Record<DayOfWeek, string> = {
  MONDAY: 'Monday', TUESDAY: 'Tuesday', WEDNESDAY: 'Wednesday', THURSDAY: 'Thursday',
  FRIDAY: 'Friday', SATURDAY: 'Saturday', SUNDAY: 'Sunday',
}

const JS_DAYS: DayOfWeek[] = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY']

/** Weekday of a "YYYY-MM-DD" calendar date. */
export function dayOfWeekOf(isoDate: string): DayOfWeek {
  const [y, m, d] = isoDate.split('-').map(Number)
  return JS_DAYS[new Date(y, m - 1, d).getDay()]
}

/** "22:00:00" or "22:00" -> "10:00 PM" */
export function formatTime(time: string): string {
  const [h, m] = time.split(':').map(Number)
  return new Date(2000, 0, 1, h, m).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })
}

/** "10:00 PM–11:30 PM", marking classes that end after midnight. */
export function formatTimeRange(start: string, end: string): string {
  const overnight = end.slice(0, 5) <= start.slice(0, 5)
  return `${formatTime(start)}–${formatTime(end)}${overnight ? ' (next day)' : ''}`
}

/** "HH:mm" for <input type="time"> */
export function toInputTime(time: string): string {
  return time.slice(0, 5)
}

/**
 * Groups days that share a time: "Mon–Fri 11:00 AM–12:00 PM" or
 * "Wed 11:00 AM–12:00 PM · Sat, Sun 10:00 PM–11:30 PM".
 */
export function summarizeSchedule(slots: WeeklySlot[]): string {
  if (slots.length === 0) return 'No class days set'
  const groups = new Map<string, DayOfWeek[]>()
  for (const day of DAYS_OF_WEEK) {
    const slot = slots.find((s) => s.dayOfWeek === day)
    if (!slot) continue
    const key = `${toInputTime(slot.startTime)}|${toInputTime(slot.endTime)}`
    groups.set(key, [...(groups.get(key) ?? []), day])
  }
  return [...groups.entries()]
    .map(([key, days]) => {
      const [start, end] = key.split('|')
      return `${formatDays(days)} ${formatTimeRange(start, end)}`
    })
    .join(' · ')
}

/** [MON, TUE, WED, FRI] -> "Mon–Wed, Fri" */
export function formatDays(days: DayOfWeek[]): string {
  const idx = days.map((d) => DAYS_OF_WEEK.indexOf(d)).sort((a, b) => a - b)
  const parts: string[] = []
  for (let i = 0; i < idx.length; i++) {
    let j = i
    while (j + 1 < idx.length && idx[j + 1] === idx[j] + 1) j++
    const from = DAY_SHORT[DAYS_OF_WEEK[idx[i]]]
    const to = DAY_SHORT[DAYS_OF_WEEK[idx[j]]]
    parts.push(j - i >= 2 ? `${from}–${to}` : j > i ? `${from}, ${to}` : from)
    i = j
  }
  return parts.join(', ')
}
