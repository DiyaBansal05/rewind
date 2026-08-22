import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { apiFetch, clearAuth } from '../api/client'
import type { EnrolledBatch, NotificationItem, StudentRecordingRequest } from '../api/types'

const STATUS_STYLES: Record<string, string> = {
  PENDING: 'bg-amber-100 text-amber-700',
  APPROVED: 'bg-emerald-100 text-emerald-700',
  DENIED: 'bg-red-100 text-red-700',
  REVOKED: 'bg-slate-100 text-slate-600',
  EXPIRED: 'bg-slate-100 text-slate-600',
}

/**
 * Notification messages have an absolute backend URL embedded (see
 * NotificationServiceImpl on the backend -- it must be absolute, not a
 * relative /r/{token} path, since the frontend and backend are different
 * origins in both local dev and production). This splits that out and
 * renders it as an actual clickable link, opening in a new tab since the
 * redemption page is a standalone route outside the SPA (same link a
 * student would get via WhatsApp once that's wired up).
 */
function renderMessageWithLink(message: string) {
  const match = message.match(/(https?:\/\/\S+)/)
  if (!match) return message
  const [link] = match
  const index = match.index ?? 0
  return (
    <>
      {message.slice(0, index)}
      <a href={link} target="_blank" rel="noreferrer" className="text-slate-900 underline hover:no-underline">
        {link}
      </a>
      {message.slice(index + link.length)}
    </>
  )
}

export function StudentDashboard() {
  const navigate = useNavigate()
  const [batches, setBatches] = useState<EnrolledBatch[]>([])
  const [history, setHistory] = useState<StudentRecordingRequest[]>([])
  const [notifications, setNotifications] = useState<NotificationItem[]>([])
  const [selectedBatch, setSelectedBatch] = useState('')
  const [classDate, setClassDate] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  function refreshHistory() {
    apiFetch<StudentRecordingRequest[]>('/api/student/recording-requests').then(setHistory).catch(() => {})
  }
  function refreshNotifications() {
    apiFetch<NotificationItem[]>('/api/student/notifications').then(setNotifications).catch(() => {})
  }

  useEffect(() => {
    apiFetch<EnrolledBatch[]>('/api/student/batches').then((b) => {
      setBatches(b)
      if (b.length > 0) setSelectedBatch(b[0].batchId)
    }).catch(() => {})
    refreshHistory()
    refreshNotifications()
  }, [])

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      await apiFetch('/api/student/recording-requests', {
        method: 'POST',
        body: JSON.stringify({ batchId: selectedBatch, classDate }),
      })
      setClassDate('')
      refreshHistory()
    } catch {
      setError('Could not submit request. Try again.')
    } finally {
      setSubmitting(false)
    }
  }

  function logout() {
    clearAuth()
    navigate('/student/login')
  }

  return (
    <main className="min-h-screen bg-slate-50 px-4 py-8">
      <div className="mx-auto max-w-2xl">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-semibold text-slate-900">My recordings</h1>
          <button onClick={logout} className="text-sm text-slate-500 hover:text-slate-800">Sign out</button>
        </div>

        <section className="mt-8 rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <h2 className="text-lg font-medium text-slate-900">Request a recording</h2>
          {batches.length === 0 ? (
            <p className="mt-3 text-sm text-slate-400">You're not enrolled in any batches yet.</p>
          ) : (
            <form onSubmit={handleSubmit} className="mt-4 flex flex-wrap items-end gap-3">
              <div>
                <label className="block text-xs font-medium text-slate-600">Batch</label>
                <select
                  value={selectedBatch} onChange={(e) => setSelectedBatch(e.target.value)}
                  className="mt-1 rounded border border-slate-300 px-2 py-1.5 text-sm"
                >
                  {batches.map((b) => (
                    <option key={b.batchId} value={b.batchId}>{b.batchName}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-xs font-medium text-slate-600">Missed class date</label>
                <input
                  type="date" required value={classDate} onChange={(e) => setClassDate(e.target.value)}
                  className="mt-1 rounded border border-slate-300 px-2 py-1.5 text-sm"
                />
              </div>
              <button type="submit" disabled={submitting} className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-50">
                {submitting ? 'Requesting...' : 'Request recording'}
              </button>
            </form>
          )}
          {error && <p className="mt-3 text-sm text-red-600">{error}</p>}
        </section>

        <section className="mt-6 rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <h2 className="text-lg font-medium text-slate-900">Notifications</h2>
          {notifications.length === 0 ? (
            <p className="mt-3 text-sm text-slate-400">Nothing yet.</p>
          ) : (
            <ul className="mt-3 space-y-2">
              {notifications.map((n) => (
                <li key={n.id} className="break-words rounded-lg bg-slate-50 p-3 text-sm text-slate-700">
                  {renderMessageWithLink(n.message)}
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="mt-6 rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <h2 className="text-lg font-medium text-slate-900">Request history</h2>
          {history.length === 0 ? (
            <p className="mt-3 text-sm text-slate-400">No requests yet.</p>
          ) : (
            <ul className="mt-3 divide-y divide-slate-100">
              {history.map((h) => (
                <li key={h.id} className="flex items-center justify-between py-2.5">
                  <div>
                    <p className="text-sm font-medium text-slate-800">{h.batchName}</p>
                    <p className="text-xs text-slate-500">Class on {h.classDate}</p>
                  </div>
                  <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_STYLES[h.status] ?? ''}`}>{h.status}</span>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </main>
  )
}
