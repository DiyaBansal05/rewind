import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { QRCodeSVG } from 'qrcode.react'
import { apiFetch, clearAuth } from '../api/client'
import { DAYS_OF_WEEK, type Batch, type DayOfWeek, type QueueItem, type RegistrationLink } from '../api/types'

export function AdminDashboard() {
  const navigate = useNavigate()
  const [batches, setBatches] = useState<Batch[]>([])
  const [queue, setQueue] = useState<QueueItem[]>([])
  const [qrBatchId, setQrBatchId] = useState<string | null>(null)
  const [qrLink, setQrLink] = useState<RegistrationLink | null>(null)
  const [showCreateForm, setShowCreateForm] = useState(false)

  function refreshBatches() {
    apiFetch<Batch[]>('/api/admin/batches').then(setBatches).catch(() => {})
  }
  function refreshQueue() {
    apiFetch<QueueItem[]>('/api/admin/recording-requests').then(setQueue).catch(() => {})
  }

  useEffect(() => {
    refreshBatches()
    refreshQueue()
  }, [])

  async function showQr(batchId: string) {
    setQrBatchId(batchId)
    setQrLink(null)
    const link = await apiFetch<RegistrationLink>(`/api/admin/batches/${batchId}/registration-link`)
    setQrLink(link)
  }

  async function decide(id: string, action: 'approve' | 'deny') {
    await apiFetch(`/api/admin/recording-requests/${id}/${action}`, { method: 'POST' })
    refreshQueue()
  }

  function logout() {
    clearAuth()
    navigate('/admin/login')
  }

  return (
    <main className="min-h-screen bg-slate-50 px-4 py-8">
      <div className="mx-auto max-w-4xl">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-semibold text-slate-900">Admin dashboard</h1>
          <button onClick={logout} className="text-sm text-slate-500 hover:text-slate-800">Sign out</button>
        </div>

        <section className="mt-8 rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-medium text-slate-900">Pending recording requests</h2>
            <span className="rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-600">{queue.length}</span>
          </div>
          {queue.length === 0 ? (
            <p className="mt-3 text-sm text-slate-400">Nothing pending right now.</p>
          ) : (
            <ul className="mt-4 divide-y divide-slate-100">
              {queue.map((item) => (
                <li key={item.id} className="flex items-center justify-between py-3">
                  <div>
                    <p className="text-sm font-medium text-slate-800">{item.studentName} <span className="font-normal text-slate-400">({item.studentPhone})</span></p>
                    <p className="text-xs text-slate-500">{item.batchName} &middot; class on {item.classDate}</p>
                  </div>
                  <div className="flex gap-2">
                    <button onClick={() => decide(item.id, 'approve')} className="rounded-md bg-emerald-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-emerald-700">Approve</button>
                    <button onClick={() => decide(item.id, 'deny')} className="rounded-md bg-slate-100 px-3 py-1.5 text-xs font-medium text-slate-600 hover:bg-slate-200">Deny</button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="mt-6 rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-medium text-slate-900">Batches</h2>
            <button onClick={() => setShowCreateForm((v) => !v)} className="rounded-md bg-slate-900 px-3 py-1.5 text-xs font-medium text-white hover:bg-slate-800">
              {showCreateForm ? 'Cancel' : 'New batch'}
            </button>
          </div>

          {showCreateForm && (
            <CreateBatchForm
              onCreated={() => {
                setShowCreateForm(false)
                refreshBatches()
              }}
            />
          )}

          <ul className="mt-4 divide-y divide-slate-100">
            {batches.map((b) => (
              <li key={b.id} className="py-3">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm font-medium text-slate-800">{b.name}</p>
                    <p className="text-xs text-slate-500">
                      {b.courseName} &middot; {b.classDaysOfWeek.join(', ')} &middot; {b.classStartTime}&ndash;{b.classEndTime}
                    </p>
                  </div>
                  <button onClick={() => showQr(b.id)} className="rounded-md border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50">
                    Registration QR
                  </button>
                </div>
                {qrBatchId === b.id && qrLink && (
                  <div className="mt-4 flex flex-col items-start gap-2 rounded-lg bg-slate-50 p-4">
                    <QRCodeSVG value={`${window.location.origin}${qrLink.registrationPath}`} size={160} />
                    <input
                      readOnly
                      value={`${window.location.origin}${qrLink.registrationPath}`}
                      className="w-full rounded border border-slate-200 bg-white px-2 py-1 text-xs text-slate-500"
                      onFocus={(e) => e.currentTarget.select()}
                    />
                  </div>
                )}
              </li>
            ))}
          </ul>
        </section>
      </div>
    </main>
  )
}

function CreateBatchForm({ onCreated }: { onCreated: () => void }) {
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
    <form onSubmit={handleSubmit} className="mt-4 rounded-lg border border-slate-200 p-4">
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

      <button type="submit" disabled={saving} className="mt-4 rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-50">
        {saving ? 'Creating...' : 'Create batch'}
      </button>
    </form>
  )
}
