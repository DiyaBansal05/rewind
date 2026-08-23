import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { apiFetch, clearAuth } from '../api/client'
import type { EnrolledBatch, NotificationItem, StudentRecordingRequest } from '../api/types'
import { useAutoRefresh } from '../hooks/useAutoRefresh'
import { RefreshButton } from '../components/RefreshButton'
import { INSTITUTE_NAME } from '../constants'

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
  const [studentName, setStudentName] = useState('')
  const [selectedBatch, setSelectedBatch] = useState('')
  const [classDate, setClassDate] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  function refresh() {
    apiFetch<{ name: string; phoneNumber: string }>('/api/student/me').then((me) => setStudentName(me.name)).catch(() => {})
    apiFetch<EnrolledBatch[]>('/api/student/batches').then((b) => {
      setBatches(b)
      const approved = b.filter((x) => x.status === 'APPROVED')
      setSelectedBatch((prev) => prev || (approved.length > 0 ? approved[0].batchId : ''))
    }).catch(() => {})
    apiFetch<StudentRecordingRequest[]>('/api/student/recording-requests').then(setHistory).catch(() => {})
    apiFetch<NotificationItem[]>('/api/student/notifications').then(setNotifications).catch(() => {})
  }

  useAutoRefresh(refresh)

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
      refresh()
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

  const approvedBatches = batches.filter((b) => b.status === 'APPROVED')
  const pendingBatches = batches.filter((b) => b.status === 'PENDING')

  return (
    <main className="min-h-screen bg-slate-50 px-4 py-6 md:py-8">
      <div className="mx-auto max-w-4xl">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-xl md:text-2xl font-semibold text-slate-900">{INSTITUTE_NAME}</h1>
            <p className="text-xs md:text-sm text-slate-400">{studentName ? `Welcome, ${studentName}` : 'Welcome'}</p>
          </div>
          <div className="flex items-center gap-3">
            <RefreshButton onClick={refresh} />
            <button onClick={logout} className="text-sm text-slate-500 hover:text-slate-800">Sign out</button>
          </div>
        </div>

        {pendingBatches.length > 0 && (
          <div className="mt-6 rounded-xl border border-amber-200 bg-amber-50 p-4">
            <p className="text-sm font-medium text-amber-800">Waiting for approval</p>
            <ul className="mt-1 space-y-0.5">
              {pendingBatches.map((b) => (
                <li key={b.batchId} className="text-sm text-amber-700">
                  {b.batchName} &middot; {b.courseName} -- your instructor hasn't approved this yet.
                </li>
              ))}
            </ul>
          </div>
        )}

        <section className="mt-6 rounded-xl border border-slate-200 bg-white p-5 md:p-6 shadow-sm">
          <h2 className="text-base md:text-lg font-medium text-slate-900">Request a recording</h2>
          {approvedBatches.length === 0 ? (
            <p className="mt-3 text-sm text-slate-400">
              {pendingBatches.length > 0 ? 'Once your instructor approves you, you can request recordings here.' : "You're not enrolled in any batches yet."}
            </p>
          ) : (
            <form onSubmit={handleSubmit} className="mt-4 flex flex-wrap items-end gap-3">
              <div>
                <label className="block text-xs font-medium text-slate-600">Batch</label>
                <select
                  value={selectedBatch} onChange={(e) => setSelectedBatch(e.target.value)}
                  className="mt-1 rounded border border-slate-300 px-2 py-1.5 text-sm"
                >
                  {approvedBatches.map((b) => (
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

        <div className="mt-6 grid grid-cols-1 lg:grid-cols-2 gap-6">
          <section className="rounded-xl border border-slate-200 bg-white p-5 md:p-6 shadow-sm">
            <h2 className="text-base md:text-lg font-medium text-slate-900">Notifications</h2>
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

          <section className="rounded-xl border border-slate-200 bg-white p-5 md:p-6 shadow-sm">
            <h2 className="text-base md:text-lg font-medium text-slate-900">Request history</h2>
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
      </div>
    </main>
  )
}
