import { useState, type FormEvent } from 'react'
import { apiFetch } from '../../api/client'
import { DAYS_OF_WEEK, type DayOfWeek } from '../../api/types'

export function CreateBatchForm({ onCreated, onCancel }: { onCreated: () => void; onCancel: () => void }) {
  const [name, setName] = useState('')
  const [courseName, setCourseName] = useState('')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [days, setDays] = useState<Set<DayOfWeek>>(new Set())
  const [classStartTime, setClassStartTime] = useState('18:00')
  const [classEndTime, setClassEndTime] = useState('19:00')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  function toggleDay(day: DayOfWeek) {
    setDays((prev) => {
      const next = new Set(prev)
      if (next.has(day)) next.delete(day)
      else next.add(day)
      return next
    })
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (days.size === 0) {
      setError('Pick at least one class day')
      return
    }
    setError(null)
    setSaving(true)
    try {
      await apiFetch('/api/admin/batches', {
        method: 'POST',
        body: JSON.stringify({
          name, courseName, startDate, endDate,
          classDaysOfWeek: Array.from(days),
          classStartTime: `${classStartTime}:00`,
          classEndTime: `${classEndTime}:00`,
        }),
      })
      onCreated()
    } catch {
      setError('Could not create batch. Check the fields and try again.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="rounded-xl border border-slate-200 bg-white p-4">
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-sm font-semibold text-slate-900">New batch</h3>
        <button type="button" onClick={onCancel} className="text-xs text-slate-400 hover:text-slate-600">Cancel</button>
      </div>
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className="block text-xs font-medium text-slate-600">Batch name</label>
          <input required value={name} onChange={(e) => setName(e.target.value)} className="mt-1 w-full rounded border border-slate-300 px-2 py-1.5 text-sm" />
        </div>
        <div>
          <label className="block text-xs font-medium text-slate-600">Course name</label>
          <input required value={courseName} onChange={(e) => setCourseName(e.target.value)} className="mt-1 w-full rounded border border-slate-300 px-2 py-1.5 text-sm" />
        </div>
        <div>
          <label className="block text-xs font-medium text-slate-600">Start date</label>
          <input type="date" required value={startDate} onChange={(e) => setStartDate(e.target.value)} className="mt-1 w-full rounded border border-slate-300 px-2 py-1.5 text-sm" />
        </div>
        <div>
          <label className="block text-xs font-medium text-slate-600">End date</label>
          <input type="date" required value={endDate} onChange={(e) => setEndDate(e.target.value)} className="mt-1 w-full rounded border border-slate-300 px-2 py-1.5 text-sm" />
        </div>
        <div>
          <label className="block text-xs font-medium text-slate-600">Class start time</label>
          <input type="time" required value={classStartTime} onChange={(e) => setClassStartTime(e.target.value)} className="mt-1 w-full rounded border border-slate-300 px-2 py-1.5 text-sm" />
        </div>
        <div>
          <label className="block text-xs font-medium text-slate-600">Class end time</label>
          <input type="time" required value={classEndTime} onChange={(e) => setClassEndTime(e.target.value)} className="mt-1 w-full rounded border border-slate-300 px-2 py-1.5 text-sm" />
        </div>
      </div>

      <label className="mt-3 block text-xs font-medium text-slate-600">Class days</label>
      <div className="mt-1 flex flex-wrap gap-1.5">
        {DAYS_OF_WEEK.map((day) => (
          <button
            type="button" key={day} onClick={() => toggleDay(day)}
            className={`rounded-full px-2.5 py-1 text-xs font-medium ${days.has(day) ? 'bg-slate-900 text-white' : 'bg-slate-100 text-slate-600'}`}
          >
            {day.slice(0, 3)}
          </button>
        ))}
      </div>

      {error && <p className="mt-3 text-sm text-red-600">{error}</p>}

      <button type="submit" disabled={saving} className="mt-4 w-full rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-50">
        {saving ? 'Creating...' : 'Create batch'}
      </button>
    </form>
  )
}
