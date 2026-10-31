import { useState, type FormEvent } from 'react'
import { apiFetch } from '../../api/client'
import { WeeklyScheduleEditor } from './WeeklyScheduleEditor'
import { slotsFromDraft, validateDraft, type ScheduleDraft } from '../../lib/scheduleDraft'

export function CreateBatchForm({ onCreated, onCancel }: { onCreated: () => void; onCancel: () => void }) {
  const [name, setName] = useState('')
  const [courseName, setCourseName] = useState('')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [schedule, setSchedule] = useState<ScheduleDraft>({})
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    const problem = validateDraft(schedule)
    if (problem) {
      setError(problem)
      return
    }
    setError(null)
    setSaving(true)
    try {
      await apiFetch('/api/admin/batches', {
        method: 'POST',
        body: JSON.stringify({
          name, courseName, startDate, endDate,
          weeklySchedule: slotsFromDraft(schedule),
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
        <button type="button" onClick={onCancel} className="px-2 py-1 text-xs text-slate-500 hover:text-slate-700">Cancel</button>
      </div>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <div>
          <label className="block text-xs font-medium text-slate-600">Batch name</label>
          <input required value={name} onChange={(e) => setName(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
        </div>
        <div>
          <label className="block text-xs font-medium text-slate-600">Course name</label>
          <input required value={courseName} onChange={(e) => setCourseName(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
        </div>
        <div>
          <label className="block text-xs font-medium text-slate-600">Start date</label>
          <input type="date" required value={startDate} onChange={(e) => setStartDate(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
        </div>
        <div>
          <label className="block text-xs font-medium text-slate-600">End date</label>
          <input type="date" required value={endDate} onChange={(e) => setEndDate(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
        </div>
      </div>

      <p className="mt-4 block text-xs font-medium text-slate-600">Class days and times</p>
      <p className="text-[11px] text-slate-400 mb-2">Tap a day to add it. Each day can have its own time.</p>
      <WeeklyScheduleEditor value={schedule} onChange={setSchedule} />

      {error && <p className="mt-3 text-sm text-red-600">{error}</p>}

      <button type="submit" disabled={saving} className="mt-4 w-full rounded-xl bg-slate-900 px-4 py-3 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-50">
        {saving ? 'Creating...' : 'Create batch'}
      </button>
    </form>
  )
}
