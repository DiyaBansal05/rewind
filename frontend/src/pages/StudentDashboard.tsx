import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { apiFetch, clearAuth } from '../api/client'
import type { EnrolledBatch, NotificationItem, StudentRecordingRequest } from '../api/types'
import { useAutoRefresh } from '../hooks/useAutoRefresh'
import { BottomSheet } from '../components/BottomSheet'
import { StatusBadge } from '../components/StatusBadge'
import { INSTITUTE_NAME } from '../constants'
import { formatClassDate, formatClock, formatDateTime, formatShortDate, timeAgo, timeUntil, toIsoDate } from '../lib/format'
import { formatDays } from '../lib/schedule'

const SEEN_KEY = 'rp_notifications_seen_at'

/**
 * Notification messages have an absolute backend URL embedded (see
 * NotificationServiceImpl on the backend -- it must be absolute, not a
 * relative /r/{token} path, since the frontend and backend are different
 * origins in both local dev and production). This splits that out and
 * renders it as a clickable link, opening in a new tab since the player is
 * a standalone page outside the SPA (same link a student would get via
 * WhatsApp once that's wired up).
 */
function renderMessageWithLink(message: string) {
  const match = message.match(/(https?:\/\/\S+)/)
  if (!match) return message
  const [link] = match
  const index = match.index ?? 0
  return (
    <>
      {message.slice(0, index)}
      <a href={link} target="_blank" rel="noreferrer" className="font-medium text-emerald-700 underline underline-offset-2 break-all">
        Open recording
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
  const [loaded, setLoaded] = useState(false)
  const [sheet, setSheet] = useState<'request' | 'notifications' | null>(null)
  const [toast, setToast] = useState<string | null>(null)
  const [seenAt, setSeenAt] = useState(() => readSeenAt())

  function refresh() {
    apiFetch<{ name: string; phoneNumber: string }>('/api/student/me').then((me) => setStudentName(me.name)).catch(() => {})
    apiFetch<EnrolledBatch[]>('/api/student/batches').then(setBatches).catch(() => {})
    apiFetch<StudentRecordingRequest[]>('/api/student/recording-requests')
      .then((h) => { setHistory(h); setLoaded(true) })
      .catch(() => setLoaded(true))
    apiFetch<NotificationItem[]>('/api/student/notifications').then(setNotifications).catch(() => {})
  }

  useAutoRefresh(refresh)

  function logout() {
    clearAuth()
    navigate('/student/login')
  }

  function openNotifications() {
    setSheet('notifications')
    const now = Date.now()
    setSeenAt(now)
    try { localStorage.setItem(SEEN_KEY, String(now)) } catch { /* private mode */ }
  }

  const approvedBatches = batches.filter((b) => b.status === 'APPROVED')
  const pendingBatches = batches.filter((b) => b.status === 'PENDING')
  const ready = history.filter((h) => h.status === 'APPROVED' && h.accessExpiresAt && timeUntil(h.accessExpiresAt))
  const others = history.filter((h) => !ready.includes(h))
  const hasUnread = notifications.some((n) => new Date(n.sentAt).getTime() > seenAt)
  const firstName = studentName.split(' ')[0]

  return (
    <main className="min-h-screen bg-slate-50">
      <header className="bg-white border-b border-slate-200">
        <div className="mx-auto max-w-3xl px-4 pt-[calc(1.25rem+env(safe-area-inset-top))] pb-4">
          <div className="flex items-center justify-between gap-3">
            <div className="min-w-0">
              <p className="text-[11px] text-slate-400 truncate">{INSTITUTE_NAME}</p>
              <h1 className="text-xl font-bold text-slate-900 truncate">{firstName ? `Hi, ${firstName}` : 'Welcome'}</h1>
            </div>
            <div className="flex items-center gap-1 shrink-0">
              <button
                onClick={openNotifications} aria-label={hasUnread ? 'Notifications (new)' : 'Notifications'}
                className="relative w-10 h-10 rounded-full bg-slate-100 grid place-items-center text-slate-600 hover:bg-slate-200"
              >
                <BellIcon />
                {hasUnread && <span className="absolute top-2 right-2.5 w-2 h-2 rounded-full bg-red-500 ring-2 ring-slate-100" />}
              </button>
              <button onClick={logout} className="px-2 py-2 text-sm text-slate-500 hover:text-slate-800">Sign out</button>
            </div>
          </div>

          {approvedBatches.length > 0 && (
            <button
              onClick={() => setSheet('request')}
              className="mt-4 w-full py-3.5 rounded-xl bg-slate-900 text-white font-semibold flex items-center justify-center gap-2 active:scale-[0.99] transition"
            >
              <PlusIcon /> Request a missed class
            </button>
          )}
        </div>
      </header>

      <div className="mx-auto max-w-3xl px-4 py-4 pb-[calc(2rem+env(safe-area-inset-bottom))] space-y-4">
        {toast && (
          <div role="status" className="flex items-start justify-between gap-3 rounded-xl bg-emerald-50 border border-emerald-200 px-4 py-3 text-sm text-emerald-800">
            <span>{toast}</span>
            <button onClick={() => setToast(null)} className="text-xs font-medium shrink-0">Dismiss</button>
          </div>
        )}

        {pendingBatches.length > 0 && (
          <div className="rounded-xl border border-amber-200 bg-amber-50 p-4">
            <p className="text-sm font-medium text-amber-800">Waiting for your instructor</p>
            <ul className="mt-1 space-y-0.5">
              {pendingBatches.map((b) => (
                <li key={b.batchId} className="text-sm text-amber-700">
                  {b.batchName} · {b.courseName}. You can request recordings once you're approved.
                </li>
              ))}
            </ul>
          </div>
        )}

        {loaded && approvedBatches.length === 0 && pendingBatches.length === 0 && (
          <div className="rounded-xl border border-slate-200 bg-white p-5 text-center">
            <p className="text-sm font-medium text-slate-800">You're not in any batch yet</p>
            <p className="mt-1 text-sm text-slate-500">Scan your batch's QR code to join it, then come back here.</p>
          </div>
        )}

        {ready.length > 0 && (
          <section className="space-y-3">
            <h2 className="text-xs font-semibold uppercase tracking-wider text-slate-400">Ready to watch</h2>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              {ready.map((r) => <ReadyCard key={r.id} request={r} onShowNotifications={openNotifications} />)}
            </div>
          </section>
        )}

        <section className="space-y-3">
          <h2 className="text-xs font-semibold uppercase tracking-wider text-slate-400 pt-1">Your requests</h2>
          {!loaded ? (
            <p className="text-sm text-slate-400">Loading...</p>
          ) : others.length === 0 ? (
            <p className="text-sm text-slate-500">
              {ready.length > 0 ? 'Nothing else right now.' : 'No requests yet. Missed a class? Tap "Request a missed class" above.'}
            </p>
          ) : (
            <ul className="space-y-2.5">
              {others.map((r) => <RequestRow key={r.id} request={r} />)}
            </ul>
          )}
        </section>
      </div>

      {sheet === 'request' && (
        <RequestSheet
          batches={approvedBatches}
          history={history}
          onClose={() => setSheet(null)}
          onCreated={(batchName, classDate) => {
            setSheet(null)
            setToast(`Request sent for ${batchName}, ${formatClassDate(classDate)}. We'll let you know when it's ready.`)
            refresh()
          }}
        />
      )}

      {sheet === 'notifications' && (
        <BottomSheet title="Notifications" onClose={() => setSheet(null)}>
          {notifications.length === 0 ? (
            <p className="mt-2 text-sm text-slate-500">Nothing yet.</p>
          ) : (
            <ul className="mt-3 space-y-2">
              {notifications.map((n) => (
                <li key={n.id} className="rounded-xl bg-slate-50 p-3 text-sm text-slate-700">
                  <p className="break-words">{renderMessageWithLink(n.message)}</p>
                  <p className="mt-1 text-[11px] text-slate-400">{timeAgo(n.sentAt)}</p>
                </li>
              ))}
            </ul>
          )}
        </BottomSheet>
      )}
    </main>
  )
}

function ReadyCard({ request: r, onShowNotifications }: { request: StudentRecordingRequest; onShowNotifications: () => void }) {
  const left = r.accessExpiresAt ? timeUntil(r.accessExpiresAt) : null
  const started = r.lastPositionSeconds > 30 && r.durationSeconds
  const progress = r.durationSeconds ? Math.min(100, (r.lastPositionSeconds / r.durationSeconds) * 100) : 0
  return (
    <div className="rounded-2xl bg-gradient-to-br from-emerald-600 to-emerald-700 text-white p-4 shadow-sm">
      <div className="flex justify-between items-start gap-2">
        <div className="min-w-0">
          <p className="font-semibold truncate">{r.batchName}</p>
          <p className="text-xs text-emerald-100">Class on {formatClassDate(r.classDate)}</p>
        </div>
        {left && <span className="shrink-0 text-[11px] bg-white/20 px-2 py-0.5 rounded-full">Expires in {left}</span>}
      </div>
      {started ? (
        <>
          <div className="mt-3 h-1.5 rounded-full bg-white/25" aria-hidden="true">
            <div className="h-1.5 rounded-full bg-white" style={{ width: `${progress}%` }} />
          </div>
          <p className="text-[11px] text-emerald-100 mt-1">
            You stopped at {formatClock(r.lastPositionSeconds)} of {formatClock(r.durationSeconds!)}
          </p>
        </>
      ) : (
        <p className="text-[11px] text-emerald-100 mt-3">Available until {r.accessExpiresAt ? formatDateTime(r.accessExpiresAt) : ''}</p>
      )}
      {r.watchUrl ? (
        <a
          href={r.watchUrl} target="_blank" rel="noreferrer"
          className="mt-3 w-full py-3 rounded-xl bg-white text-emerald-800 font-semibold flex items-center justify-center gap-2 active:scale-[0.99] transition"
        >
          <PlayIcon /> {started ? 'Resume watching' : 'Watch now'}
        </a>
      ) : (
        <button
          onClick={onShowNotifications}
          className="mt-3 w-full py-3 rounded-xl bg-white text-emerald-800 font-semibold"
        >
          Open link from notifications
        </button>
      )}
    </div>
  )
}

function RequestRow({ request: r }: { request: StudentRecordingRequest }) {
  const note = r.status === 'PENDING'
    ? `Sent ${timeAgo(r.requestedAt).toLowerCase()}. Your instructor will review it soon.`
    : r.status === 'DENIED'
      ? "Your instructor couldn't share this recording. Ask them if you're not sure why."
      : r.status === 'EXPIRED'
        ? `Access ended${r.accessExpiresAt ? ` ${formatShortDate(r.accessExpiresAt)}` : ''}. Request it again if you still need it.`
        : r.status === 'REVOKED'
          ? 'Your instructor turned off access to this recording.'
          : ''
  const stripe = r.status === 'PENDING' ? 'bg-amber-400' : r.status === 'DENIED' ? 'bg-red-400' : 'bg-slate-300'
  return (
    <li className="bg-white rounded-xl border border-slate-200 p-3.5 flex gap-3">
      <span className={`w-1 rounded-full shrink-0 ${stripe}`} aria-hidden="true" />
      <div className="flex-1 min-w-0">
        <div className="flex justify-between gap-2">
          <p className="text-sm font-semibold text-slate-900 truncate">{r.batchName}</p>
          <StatusBadge status={r.status} />
        </div>
        <p className="text-xs text-slate-500">{formatClassDate(r.classDate)}</p>
        {note && <p className="text-[11px] text-slate-400 mt-1">{note}</p>}
      </div>
    </li>
  )
}

function RequestSheet({
  batches, history, onClose, onCreated,
}: {
  batches: EnrolledBatch[]
  history: StudentRecordingRequest[]
  onClose: () => void
  onCreated: (batchName: string, classDate: string) => void
}) {
  const [batchId, setBatchId] = useState(batches[0]?.batchId ?? '')
  const [classDate, setClassDate] = useState('')
  const [customDate, setCustomDate] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const batch = batches.find((b) => b.batchId === batchId)
  const recentClasses = batch?.recentClassDates ?? []
  const duplicate = history.find((h) => h.batchId === batchId && h.classDate === classDate && (h.status === 'PENDING' || h.status === 'APPROVED'))

  function pickBatch(id: string) {
    setBatchId(id)
    setClassDate('')
    setCustomDate(false)
  }

  async function submit() {
    if (!batch || !classDate) return
    setSubmitting(true)
    setError(null)
    try {
      await apiFetch('/api/student/recording-requests', {
        method: 'POST',
        body: JSON.stringify({ batchId, classDate }),
      })
      onCreated(batch.batchName, classDate)
    } catch {
      setError("Couldn't send the request. Check your connection and try again.")
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <BottomSheet title="Request a recording" onClose={onClose}>
      {batches.length > 1 && (
        <>
          <p className="text-xs font-medium text-slate-600 mt-4">Batch</p>
          <div className="mt-1.5 grid grid-cols-1 gap-2" role="radiogroup" aria-label="Batch">
            {batches.map((b) => (
              <button
                key={b.batchId} role="radio" aria-checked={b.batchId === batchId} onClick={() => pickBatch(b.batchId)}
                className={`text-left px-4 py-3 rounded-xl text-sm font-medium ${b.batchId === batchId ? 'border-2 border-slate-900' : 'border border-slate-200'}`}
              >
                {b.batchName}
                <span className="block text-[11px] text-slate-500 font-normal">{describeSchedule(b)}</span>
              </button>
            ))}
          </div>
        </>
      )}
      {batches.length === 1 && batch && (
        <p className="mt-1 text-sm text-slate-500">{batch.batchName} · {describeSchedule(batch)}</p>
      )}

      <p className="text-xs font-medium text-slate-600 mt-4">Which class did you miss?</p>
      <div className="mt-1.5 -mx-5 px-5 flex gap-2 overflow-x-auto no-scrollbar" role="radiogroup" aria-label="Class date">
        {recentClasses.map((d) => {
          const [weekday, day, month] = splitDate(d)
          const active = !customDate && classDate === d
          return (
            <button
              key={d} role="radio" aria-checked={active} onClick={() => { setCustomDate(false); setClassDate(d) }}
              className={`shrink-0 w-16 py-2 rounded-xl text-center ${active ? 'bg-slate-900 text-white' : 'bg-slate-100 text-slate-700'}`}
            >
              <span className="block text-[10px]">{weekday}</span>
              <span className="block text-base font-semibold leading-tight">{day}</span>
              <span className="block text-[10px]">{month}</span>
            </button>
          )
        })}
        <button
          onClick={() => { setCustomDate(true); setClassDate('') }}
          className={`shrink-0 px-3 rounded-xl text-xs ${customDate ? 'bg-slate-900 text-white' : 'border border-dashed border-slate-300 text-slate-500'}`}
        >
          Other date
        </button>
      </div>
      {customDate && (
        <input
          type="date" aria-label="Missed class date" value={classDate} max={toIsoDate(new Date())}
          onChange={(e) => setClassDate(e.target.value)}
          className="mt-2 w-full rounded-xl border border-slate-300 px-3 py-2.5 text-sm"
        />
      )}
      <p className="text-[11px] text-slate-400 mt-1.5">
        {recentClasses.length > 0 ? 'Showing your most recent class days.' : 'Pick the date of the class you missed.'}
      </p>

      {duplicate && (
        <p className="mt-3 rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-800">
          You already have a {duplicate.status === 'PENDING' ? 'pending request' : 'recording'} for this class.
        </p>
      )}
      {error && <p className="mt-3 text-sm text-red-600">{error}</p>}

      <button
        onClick={submit} disabled={!classDate || submitting || Boolean(duplicate)}
        className="mt-5 w-full py-3.5 rounded-xl bg-slate-900 text-white font-semibold disabled:opacity-40"
      >
        {submitting ? 'Sending...' : 'Send request'}
      </button>
    </BottomSheet>
  )
}

/** Students only see which days the batch meets; times (and any one-off changes) stay with the admin. */
function describeSchedule(b: EnrolledBatch): string {
  return b.classDays.length > 0 ? `Classes on ${formatDays(b.classDays)}` : b.courseName
}

function splitDate(isoDate: string): [string, string, string] {
  const [y, m, d] = isoDate.split('-').map(Number)
  const date = new Date(y, m - 1, d)
  return [
    date.toLocaleDateString(undefined, { weekday: 'short' }),
    String(d),
    date.toLocaleDateString(undefined, { month: 'short' }),
  ]
}

function readSeenAt(): number {
  try {
    return Number(localStorage.getItem(SEEN_KEY) ?? 0)
  } catch {
    return 0
  }
}

function BellIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
      <path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" /><path d="M13.7 21a2 2 0 0 1-3.4 0" />
    </svg>
  )
}

function PlusIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" aria-hidden="true">
      <path d="M12 5v14M5 12h14" />
    </svg>
  )
}

function PlayIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <polygon points="6 4 20 12 6 20 6 4" />
    </svg>
  )
}
