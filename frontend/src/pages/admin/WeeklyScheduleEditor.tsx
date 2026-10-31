import { DAYS_OF_WEEK, type DayOfWeek } from '../../api/types'
import { DAY_LONG, DAY_SHORT } from '../../lib/schedule'
import { DEFAULT_SLOT_TIME, type ScheduleDraft } from '../../lib/scheduleDraft'

/**
 * One row per weekday: tap the day to turn class on or off, then set that
 * day's own start and end time. Turning a day on copies the time of the
 * day above it, so "same time every weekday" is still just a few taps.
 */
export function WeeklyScheduleEditor({ value, onChange }: { value: ScheduleDraft; onChange: (next: ScheduleDraft) => void }) {
  const enabled = DAYS_OF_WEEK.filter((d) => value[d])
  const allSame = enabled.every((d) => value[d]!.start === value[enabled[0]]!.start && value[d]!.end === value[enabled[0]]!.end)

  function toggle(day: DayOfWeek) {
    const next = { ...value }
    if (next[day]) {
      delete next[day]
    } else {
      const before = DAYS_OF_WEEK.slice(0, DAYS_OF_WEEK.indexOf(day)).reverse().find((d) => value[d])
      const source = before ?? enabled[0]
      next[day] = source ? { ...value[source]! } : { ...DEFAULT_SLOT_TIME }
    }
    onChange(next)
  }

  function setTime(day: DayOfWeek, field: 'start' | 'end', time: string) {
    onChange({ ...value, [day]: { ...value[day]!, [field]: time } })
  }

  function copyFirstToAll() {
    const first = value[enabled[0]]!
    const next: ScheduleDraft = {}
    enabled.forEach((d) => { next[d] = { ...first } })
    onChange(next)
  }

  return (
    <div>
      <ul className="space-y-1.5">
        {DAYS_OF_WEEK.map((day) => {
          const slot = value[day]
          const overnight = slot && slot.end <= slot.start
          return (
            <li key={day} className="flex flex-wrap items-center gap-2 min-h-[2.75rem]">
              <button
                type="button" onClick={() => toggle(day)} aria-pressed={Boolean(slot)}
                aria-label={`${DAY_LONG[day]}: ${slot ? 'has class' : 'no class'}`}
                className={`w-14 shrink-0 rounded-lg py-2.5 text-xs font-semibold transition ${slot ? 'bg-slate-900 text-white' : 'bg-slate-100 text-slate-500'}`}
              >
                {DAY_SHORT[day]}
              </button>
              {slot ? (
                // Wraps under the day on narrow phones so the time pickers stay readable.
                <div className="flex items-center gap-1.5 flex-1 min-w-[17rem]">
                  <input
                    type="time" required value={slot.start} onChange={(e) => setTime(day, 'start', e.target.value)}
                    aria-label={`${DAY_LONG[day]} start time`}
                    className="min-w-0 flex-1 rounded-lg border border-slate-300 px-2 py-2 text-sm"
                  />
                  <span className="text-slate-400 text-xs" aria-hidden="true">to</span>
                  <input
                    type="time" required value={slot.end} onChange={(e) => setTime(day, 'end', e.target.value)}
                    aria-label={`${DAY_LONG[day]} end time`}
                    className="min-w-0 flex-1 rounded-lg border border-slate-300 px-2 py-2 text-sm"
                  />
                </div>
              ) : (
                <span className="text-xs text-slate-400">No class</span>
              )}
              {overnight && slot.end !== slot.start && (
                <p className="w-full text-[11px] text-slate-500">{DAY_SHORT[day]} class ends after midnight</p>
              )}
            </li>
          )
        })}
      </ul>
      {enabled.length > 1 && !allSame && (
        <button type="button" onClick={copyFirstToAll} className="mt-2 text-xs font-medium text-emerald-700 py-1">
          Use {DAY_LONG[enabled[0]]}'s time for every class day
        </button>
      )}
    </div>
  )
}
