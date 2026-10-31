import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { apiFetch, ApiError } from '../../api/client'
import type { Batch, ClassChange, EnrolledStudent } from '../../api/types'
import { useAutoRefresh } from '../../hooks/useAutoRefresh'
import { BottomSheet } from '../../components/BottomSheet'
import { ConfirmDialog } from '../../components/ConfirmDialog'
import { formatClassDate, toIsoDate } from '../../lib/format'
import { DAY_LONG, DAY_SHORT, dayOfWeekOf, formatTimeRange, toInputTime } from '../../lib/schedule'
import { RefreshButton } from './shared'
import { WeeklyScheduleEditor } from './WeeklyScheduleEditor'
import { draftFromSlots, slotsFromDraft, validateDraft, type ScheduleDraft } from '../../lib/scheduleDraft'

export function AdminBatchDetailPage() {
  const { batchId } = useParams<{ batchId: string }>()
  const navigate = useNavigate()

  const [batch, setBatch] = useState<Batch | null>(null)
  const [notFound, setNotFound] = useState(false)
  const [roster, setRoster] = useState<EnrolledStudent[]>([])
  const [changes, setChanges] = useState<ClassChange[]>([])
  const [removeTarget, setRemoveTarget] = useState<EnrolledStudent | null>(null)
  const [removing, setRemoving] = useState(false)
  const [sheet, setSheet] = useState<'timetable' | 'change' | null>(null)
  const [editingChange, setEditingChange] = useState<ClassChange | null>(null)
  const [deleteChange, setDeleteChange] = useState<ClassChange | null>(null)
  const [deleting, setDeleting] = useState(false)

  function refresh() {
    if (!batchId) return
    apiFetch<Batch>(`/api/admin/batches/${batchId}`).then((b) => { setBatch(b); setNotFound(false) }).catch(() => setNotFound(true))
    apiFetch<EnrolledStudent[]>(`/api/admin/batches/${batchId}/students`).then(setRoster).catch(() => {})
    apiFetch<ClassChange[]>(`/api/admin/batches/${batchId}/class-changes`).then(setChanges).catch(() => {})
  }

  useAutoRefresh(refresh)

  async function confirmRemove() {
    if (!removeTarget) return
    setRemoving(true)
    try {
      await apiFetch(`/api/admin/enrollments/${removeTarget.enrollmentId}`, { method: 'DELETE' })
      setRoster((prev) => prev.filter((s) => s.enrollmentId !== removeTarget.enrollmentId))
      setRemoveTarget(null)
    } finally {
      setRemoving(false)
    }
  }

  async function confirmDeleteChange() {
    if (!deleteChange) return
    setDeleting(true)
    try {
      await apiFetch(`/api/admin/batches/${batchId}/class-changes/${deleteChange.id}`, { method: 'DELETE' })
      setChanges((prev) => prev.filter((c) => c.id !== deleteChange.id))
      setDeleteChange(null)
    } finally {
      setDeleting(false)
    }
  }

  if (notFound) {
    return (
      <div>
        <Link to="/admin/batches" className="text-sm text-slate-500 hover:text-slate-800">&larr; Back to batches</Link>
        <p className="mt-6 text-sm text-slate-400 text-center py-8">This batch no longer exists.</p>
      </div>
    )
  }

  const today = toIsoDate(new Date())
  const upcoming = changes.filter((c) => c.classDate >= today)
  const recent = changes.filter((c) => c.classDate < today).reverse()

  return (
    <div className="md:max-w-3xl">
      <button onClick={() => navigate('/admin/batches')} className="text-sm text-slate-500 hover:text-slate-800 -ml-1 px-1 py-1">
        &larr; Back to batches
      </button>

      <div className="mt-2 flex items-center justify-between gap-3">
        <div className="min-w-0">
          <h2 className="text-lg font-semibold text-slate-900 truncate">{batch?.name ?? 'Loading...'}</h2>
          {batch && <p className="text-xs text-slate-500 mt-0.5">{batch.courseName}</p>}
        </div>
        <RefreshButton onClick={refresh} />
      </div>

      {batch && (
        <div className="mt-4 grid grid-cols-1 md:grid-cols-2 gap-3 items-start">
          {/* Regular weekly timetable */}
          <section className="bg-white rounded-xl border border-slate-200 p-4">
            <div className="flex items-center justify-between gap-2">
              <h3 className="text-sm font-semibold text-slate-900">Class timetable</h3>
              <button onClick={() => setSheet('timetable')} className="text-xs font-semibold text-emerald-700 px-2 py-1 -mr-2">Edit</button>
            </div>
            <ul className="mt-2 divide-y divide-slate-100">
              {batch.weeklySchedule.map((s) => (
                <li key={s.dayOfWeek} className="flex justify-between gap-3 py-2 text-sm">
                  <span className="text-slate-600">{DAY_LONG[s.dayOfWeek]}</span>
                  <span className="font-medium text-slate-900 text-right">{formatTimeRange(s.startTime, s.endTime)}</span>
                </li>
              ))}
            </ul>
            <p className="mt-2 text-[11px] text-slate-400">Used to find the right Zoom recording for each class.</p>
          </section>

          {/* Single-date changes */}
          <section className="bg-white rounded-xl border border-slate-200 p-4">
            <div className="flex items-center justify-between gap-2">
              <h3 className="text-sm font-semibold text-slate-900">Class time changes</h3>
            </div>
            <p className="text-xs text-slate-500 mt-0.5">
              Class moved, cancelled or added on one day? Note it here so students get the right recording. Students don't see this.
            </p>
            <button
              onClick={() => { setEditingChange(null); setSheet('change') }}
              className="mt-3 w-full rounded-xl bg-slate-900 py-2.5 text-sm font-semibold text-white"
            >
              Change one class
            </button>

            {changes.length === 0 ? (
              <p className="mt-3 text-xs text-slate-400">No changes in the last 30 days or coming up.</p>
            ) : (
              <div className="mt-3 space-y-3">
                {upcoming.length > 0 && (
                  <ChangeList title="Coming up" items={upcoming} onEdit={(c) => { setEditingChange(c); setSheet('change') }} onDelete={setDeleteChange} />
                )}
                {recent.length > 0 && (
                  <ChangeList title="Recent" items={recent} muted onEdit={(c) => { setEditingChange(c); setSheet('change') }} onDelete={setDeleteChange} />
                )}
              </div>
            )}
          </section>
        </div>
      )}

      <div className="mt-5">
        <h3 className="text-sm font-semibold text-slate-900">Students ({roster.length})</h3>
        {roster.length === 0 ? (
          <p className="mt-3 text-sm text-slate-400 text-center py-8">No approved students yet.</p>
        ) : (
          <ul className="mt-2 divide-y divide-slate-100 rounded-xl border border-slate-200 bg-white">
            {roster.map((s) => (
              <li key={s.enrollmentId} className="flex items-center justify-between gap-3 px-4 py-3.5">
                <div className="min-w-0">
                  <p className="text-sm font-medium text-slate-800 truncate">{s.name}</p>
                  <p className="text-xs text-slate-500">{s.phoneNumber}</p>
                </div>
                <button
                  onClick={() => setRemoveTarget(s)}
                  className="shrink-0 rounded-xl border border-red-200 px-3 py-2 text-sm font-medium text-red-600 hover:bg-red-50"
                >
                  Remove
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>

      {sheet === 'timetable' && batch && (
        <TimetableSheet
          batch={batch}
          onClose={() => setSheet(null)}
          onSaved={(b) => { setBatch(b); setSheet(null) }}
        />
      )}

      {sheet === 'change' && batch && (
        <ClassChangeSheet
          batch={batch}
          existing={changes}
          initial={editingChange}
          onClose={() => setSheet(null)}
          onSaved={() => { setSheet(null); refresh() }}
        />
      )}

      {deleteChange && (
        <ConfirmDialog
          title="Remove this change?"
          message={<>{formatClassDate(deleteChange.classDate)} goes back to the usual timetable.</>}
          confirmLabel="Remove"
          busyLabel="Removing..."
          busy={deleting}
          onConfirm={confirmDeleteChange}
          onCancel={() => setDeleteChange(null)}
        />
      )}

      {removeTarget && batch && (
        <ConfirmDialog
          title="Remove student?"
          message={<>Are you sure you want to remove <span className="font-semibold">{removeTarget.name}</span> from <span className="font-semibold">{batch.name}</span>?</>}
          confirmLabel="Remove"
          busyLabel="Removing..."
          busy={removing}
          onConfirm={confirmRemove}
          onCancel={() => setRemoveTarget(null)}
        />
      )}
    </div>
  )
}

function ChangeList({ title, items, muted, onEdit, onDelete }: {
  title: string
  items: ClassChange[]
  muted?: boolean
  onEdit: (c: ClassChange) => void
  onDelete: (c: ClassChange) => void
}) {
  return (
    <div>
      <p className="text-[10px] font-semibold uppercase tracking-wider text-slate-400">{title}</p>
      <ul className="mt-1 space-y-2">
        {items.map((c) => (
          <li key={c.id} className={`rounded-xl border border-slate-200 p-3 ${muted ? 'bg-slate-50' : 'bg-white'}`}>
            <div className="flex items-start justify-between gap-2">
              <div className="min-w-0">
                <p className="text-sm font-semibold text-slate-900">{formatClassDate(c.classDate)}</p>
                <p className={`text-xs mt-0.5 ${c.cancelled ? 'text-red-600' : 'text-slate-700'}`}>{describeChange(c)}</p>
                {c.note && <p className="text-[11px] text-slate-400 mt-0.5">{c.note}</p>}
              </div>
              <div className="shrink-0 flex gap-1">
                <button onClick={() => onEdit(c)} className="text-xs font-medium text-slate-600 px-2 py-1.5">Edit</button>
                <button onClick={() => onDelete(c)} className="text-xs font-medium text-red-600 px-2 py-1.5">Remove</button>
              </div>
            </div>
          </li>
        ))}
      </ul>
    </div>
  )
}

function describeChange(c: ClassChange): string {
  if (c.cancelled) return c.regularStart ? 'Cancelled' : 'Cancelled (not a usual class day)'
  const time = formatTimeRange(c.startTime!, c.endTime!)
  if (!c.regularStart) return `Extra class, ${time}`
  return `Moved to ${time} (usually ${formatTimeRange(c.regularStart, c.regularEnd!)})`
}

function TimetableSheet({ batch, onClose, onSaved }: { batch: Batch; onClose: () => void; onSaved: (b: Batch) => void }) {
  const [draft, setDraft] = useState<ScheduleDraft>(() => draftFromSlots(batch.weeklySchedule))
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function save() {
    const problem = validateDraft(draft)
    if (problem) { setError(problem); return }
    setSaving(true)
    setError(null)
    try {
      const updated = await apiFetch<Batch>(`/api/admin/batches/${batch.id}/schedule`, {
        method: 'PUT',
        body: JSON.stringify({ weeklySchedule: slotsFromDraft(draft) }),
      })
      onSaved(updated)
    } catch (err) {
      setError(messageFrom(err) ?? "Couldn't save the timetable. Try again.")
    } finally {
      setSaving(false)
    }
  }

  return (
    <BottomSheet title="Class timetable" onClose={onClose}>
      <p className="text-sm text-slate-500 mb-3">
        The usual time for each class day. For a one-off change, use "Change one class" instead.
      </p>
      <WeeklyScheduleEditor value={draft} onChange={setDraft} />
      {error && <p className="mt-3 text-sm text-red-600">{error}</p>}
      <button onClick={save} disabled={saving} className="mt-4 w-full rounded-xl bg-slate-900 py-3 text-sm font-semibold text-white disabled:opacity-50">
        {saving ? 'Saving...' : 'Save timetable'}
      </button>
    </BottomSheet>
  )
}

function ClassChangeSheet({ batch, existing, initial, onClose, onSaved }: {
  batch: Batch
  existing: ClassChange[]
  initial: ClassChange | null
  onClose: () => void
  onSaved: () => void
}) {
  const [date, setDate] = useState(initial?.classDate ?? toIsoDate(new Date()))
  const regular = batch.weeklySchedule.find((s) => s.dayOfWeek === dayOfWeekOf(date))
  const already = existing.find((c) => c.classDate === date && c.id !== initial?.id)
  const [mode, setMode] = useState<'time' | 'cancelled'>(initial?.cancelled ? 'cancelled' : 'time')
  const [start, setStart] = useState(initial?.startTime ? toInputTime(initial.startTime) : regular ? toInputTime(regular.startTime) : '')
  const [end, setEnd] = useState(initial?.endTime ? toInputTime(initial.endTime) : regular ? toInputTime(regular.endTime) : '')
  const [note, setNote] = useState(initial?.note ?? '')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  function pickDate(next: string) {
    setDate(next)
    if (!next) return
    const slot = batch.weeklySchedule.find((s) => s.dayOfWeek === dayOfWeekOf(next))
    if (slot && !initial) {
      setStart(toInputTime(slot.startTime))
      setEnd(toInputTime(slot.endTime))
    }
  }

  async function save() {
    if (!date) { setError('Pick the date.'); return }
    if (mode === 'time') {
      if (!start || !end) { setError('Set the start and end time.'); return }
      if (start === end) { setError("Start and end time can't be the same."); return }
    }
    setSaving(true)
    setError(null)
    try {
      // Moving a change to another date: drop the old one so it doesn't linger.
      if (initial && initial.classDate !== date) {
        await apiFetch(`/api/admin/batches/${batch.id}/class-changes/${initial.id}`, { method: 'DELETE' })
      }
      await apiFetch(`/api/admin/batches/${batch.id}/class-changes`, {
        method: 'POST',
        body: JSON.stringify({
          classDate: date,
          cancelled: mode === 'cancelled',
          startTime: mode === 'time' ? `${start}:00` : null,
          endTime: mode === 'time' ? `${end}:00` : null,
          note: note.trim() || null,
        }),
      })
      onSaved()
    } catch (err) {
      setError(messageFrom(err) ?? "Couldn't save the change. Try again.")
    } finally {
      setSaving(false)
    }
  }

  const overnight = mode === 'time' && start && end && end <= start && start !== end

  return (
    <BottomSheet title={initial ? 'Edit class change' : 'Change one class'} onClose={onClose}>
      <label htmlFor="change-date" className="block text-xs font-medium text-slate-600 mt-3">Date</label>
      <input
        id="change-date" type="date" value={date} onChange={(e) => pickDate(e.target.value)}
        className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2.5 text-sm"
      />
      {date && (
        <p className="mt-1.5 text-xs text-slate-500">
          {regular
            ? <>Usually {DAY_SHORT[regular.dayOfWeek]} {formatTimeRange(regular.startTime, regular.endTime)}.</>
            : <>{batch.name} doesn't normally have class on {DAY_LONG[dayOfWeekOf(date)]}s. Setting a time adds an extra class.</>}
        </p>
      )}
      {already && (
        <p className="mt-2 rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-800">
          There's already a change saved for this date. Saving replaces it.
        </p>
      )}

      <div className="mt-4 grid grid-cols-2 gap-2" role="radiogroup" aria-label="What changed">
        <button
          type="button" role="radio" aria-checked={mode === 'time'} onClick={() => setMode('time')}
          className={`rounded-xl py-2.5 text-sm font-medium ${mode === 'time' ? 'bg-slate-900 text-white' : 'bg-slate-100 text-slate-700'}`}
        >
          {regular ? 'Different time' : 'Extra class'}
        </button>
        <button
          type="button" role="radio" aria-checked={mode === 'cancelled'} onClick={() => setMode('cancelled')}
          className={`rounded-xl py-2.5 text-sm font-medium ${mode === 'cancelled' ? 'bg-slate-900 text-white' : 'bg-slate-100 text-slate-700'}`}
        >
          No class
        </button>
      </div>

      {mode === 'time' ? (
        <div className="mt-3">
          <p className="text-xs font-medium text-slate-600">Class time that day</p>
          <div className="mt-1 flex items-center gap-2">
            <input type="time" aria-label="New start time" value={start} onChange={(e) => setStart(e.target.value)}
              className="min-w-0 flex-1 rounded-xl border border-slate-300 px-3 py-2.5 text-sm" />
            <span className="text-xs text-slate-400" aria-hidden="true">to</span>
            <input type="time" aria-label="New end time" value={end} onChange={(e) => setEnd(e.target.value)}
              className="min-w-0 flex-1 rounded-xl border border-slate-300 px-3 py-2.5 text-sm" />
          </div>
          {overnight && <p className="mt-1 text-[11px] text-slate-500">Ends after midnight</p>}
        </div>
      ) : (
        <p className="mt-3 text-xs text-slate-500">
          Requests for this date won't find a recording, and students won't be offered it as a class day.
        </p>
      )}

      <label htmlFor="change-note" className="block text-xs font-medium text-slate-600 mt-4">Note for yourself (optional)</label>
      <input
        id="change-note" value={note} onChange={(e) => setNote(e.target.value)} maxLength={255}
        placeholder="e.g. Moved for Diwali"
        className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2.5 text-sm"
      />

      {error && <p className="mt-3 text-sm text-red-600">{error}</p>}
      <button onClick={save} disabled={saving} className="mt-5 w-full rounded-xl bg-slate-900 py-3 text-sm font-semibold text-white disabled:opacity-50">
        {saving ? 'Saving...' : 'Save change'}
      </button>
    </BottomSheet>
  )
}

function messageFrom(err: unknown): string | null {
  if (!(err instanceof ApiError)) return null
  try {
    return (JSON.parse(err.message) as { message?: string }).message ?? null
  } catch {
    return null
  }
}
