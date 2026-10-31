import { DAYS_OF_WEEK, type DayOfWeek, type WeeklySlot } from '../api/types'
import { DAY_LONG, toInputTime } from './schedule'

/** Times as "HH:mm" per class day; a missing day means no class that day. */
export type ScheduleDraft = Partial<Record<DayOfWeek, { start: string; end: string }>>

/** Time given to the first class day added to an empty timetable. */
export const DEFAULT_SLOT_TIME = { start: '18:00', end: '19:00' }

export function draftFromSlots(slots: WeeklySlot[]): ScheduleDraft {
  const draft: ScheduleDraft = {}
  slots.forEach((s) => { draft[s.dayOfWeek] = { start: toInputTime(s.startTime), end: toInputTime(s.endTime) } })
  return draft
}

export function slotsFromDraft(draft: ScheduleDraft): WeeklySlot[] {
  return DAYS_OF_WEEK.filter((d) => draft[d]).map((d) => ({
    dayOfWeek: d,
    startTime: `${draft[d]!.start}:00`,
    endTime: `${draft[d]!.end}:00`,
  }))
}

/** Returns a message if the draft can't be saved, otherwise null. */
export function validateDraft(draft: ScheduleDraft): string | null {
  const days = DAYS_OF_WEEK.filter((d) => draft[d])
  if (days.length === 0) return 'Pick at least one class day.'
  const same = days.find((d) => draft[d]!.start === draft[d]!.end)
  if (same) return `${DAY_LONG[same]}: start and end time can't be the same.`
  return null
}

