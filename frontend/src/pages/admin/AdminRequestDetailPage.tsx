import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { apiFetch, ApiError } from '../../api/client'
import type { RequestDetail } from '../../api/types'
import { useAutoRefresh } from '../../hooks/useAutoRefresh'
import { ConfirmDialog } from '../../components/ConfirmDialog'
import { StatusBadge } from '../../components/StatusBadge'
import { formatClassDate, formatClock, formatDateTime, formatDuration, timeAgo, timeUntil } from '../../lib/format'
import { useAdminOutlet } from './adminOutlet'

const EXTEND_OPTIONS = [1, 3, 7]

export function AdminRequestDetailPage() {
  const { requestId } = useParams<{ requestId: string }>()
  const navigate = useNavigate()
  const { refreshBadges } = useAdminOutlet()
  const [detail, setDetail] = useState<RequestDetail | null>(null)
  const [notFound, setNotFound] = useState(false)
  const [busy, setBusy] = useState<'extend' | 'revoke' | null>(null)
  const [message, setMessage] = useState<{ tone: 'ok' | 'error'; text: string } | null>(null)
  const [confirmRevoke, setConfirmRevoke] = useState(false)

  function refresh() {
    if (!requestId) return
    apiFetch<RequestDetail>(`/api/admin/recording-requests/${requestId}`)
      .then((d) => { setDetail(d); setNotFound(false) })
      .catch((err) => { if (err instanceof ApiError && err.status === 404) setNotFound(true) })
  }

  useAutoRefresh(refresh)

  function back() {
    // Return to the list with its filters if we came from there; otherwise go to the Approved tab.
    if (window.history.state?.idx > 0) navigate(-1)
    else navigate('/admin/requests?tab=APPROVED')
  }

  async function extend(days: number) {
    setBusy('extend')
    setMessage(null)
    try {
      await apiFetch(`/api/admin/recording-requests/${requestId}/extend`, { method: 'POST', body: JSON.stringify({ days }) })
      setMessage({ tone: 'ok', text: `Extended by ${days} day${days > 1 ? 's' : ''}. The student's link stays the same and they've been notified.` })
      refresh()
    } catch {
      setMessage({ tone: 'error', text: "Couldn't extend access. Try again." })
    } finally {
      setBusy(null)
    }
  }

  async function revoke() {
    setBusy('revoke')
    setMessage(null)
    try {
      await apiFetch(`/api/admin/recording-requests/${requestId}/revoke`, { method: 'POST' })
      setConfirmRevoke(false)
      setMessage({ tone: 'ok', text: 'Access revoked. The link has stopped working.' })
      refresh()
      refreshBadges()
    } catch {
      setMessage({ tone: 'error', text: "Couldn't revoke access. Try again." })
    } finally {
      setBusy(null)
    }
  }

  if (notFound) {
    return (
      <div>
        <Link to="/admin/requests" className="text-sm text-slate-500 hover:text-slate-800">&larr; Back to requests</Link>
        <p className="mt-6 text-sm text-slate-400 text-center py-8">This request no longer exists.</p>
      </div>
    )
  }

  if (!detail) {
    return <p className="text-sm text-slate-400 py-8 text-center">Loading...</p>
  }

  const s = detail.summary
  const isActive = s.status === 'APPROVED'
  const canExtend = s.status === 'APPROVED' || s.status === 'EXPIRED'
  const left = s.accessExpiresAt ? timeUntil(s.accessExpiresAt) : null

  return (
    <div className="md:max-w-3xl">
      <button onClick={back} className="text-sm text-slate-500 hover:text-slate-800 -ml-1 px-1 py-1">&larr; Back</button>

      <div className="mt-2 grid grid-cols-1 md:grid-cols-2 gap-3 items-start">
        <div className="space-y-3">
          {/* Who / what */}
          <section className="bg-white rounded-xl border border-slate-200 p-4">
            <div className="flex items-start justify-between gap-2">
              <div className="min-w-0">
                <h2 className="font-semibold text-slate-900 truncate">{s.studentName}</h2>
                <p className="text-xs text-slate-500">{s.studentPhone}</p>
              </div>
              <StatusBadge status={s.status} />
            </div>
            <p className="text-xs text-slate-500 mt-2">
              {s.batchName} · class on <span className="font-semibold text-slate-700">{formatClassDate(s.classDate)}</span>
            </p>
            <p className="text-[11px] text-slate-400 mt-0.5">
              Requested {timeAgo(s.requestedAt)}{s.decidedAt ? ` · decided ${formatDateTime(s.decidedAt)}` : ''}
            </p>
            {s.accessExpiresAt && <ExpiryBar status={s.status} decidedAt={s.decidedAt} expiresAt={s.accessExpiresAt} left={left} />}
          </section>

          {/* Extend / revoke */}
          {(canExtend || isActive) && (
            <section className="bg-white rounded-xl border border-slate-200 p-4">
              <h3 className="text-sm font-semibold text-slate-900">{s.status === 'EXPIRED' ? 'Re-open access' : 'Extend access'}</h3>
              <p className="text-xs text-slate-500 mt-0.5">
                {s.status === 'EXPIRED'
                  ? 'The link stopped working. Re-opening it counts from now.'
                  : 'Adds time to what is left. The link the student has keeps working.'}
              </p>
              <div className="mt-3 grid grid-cols-3 gap-2">
                {EXTEND_OPTIONS.map((d) => (
                  <button
                    key={d} onClick={() => extend(d)} disabled={busy !== null}
                    className="py-3 rounded-xl text-sm font-semibold bg-slate-100 text-slate-800 hover:bg-slate-200 active:scale-[0.98] transition disabled:opacity-50"
                  >
                    +{d} day{d > 1 ? 's' : ''}
                  </button>
                ))}
              </div>
              {isActive && (
                <button
                  onClick={() => setConfirmRevoke(true)} disabled={busy !== null}
                  className="mt-3 w-full py-3 rounded-xl border border-red-200 text-red-600 text-sm font-medium hover:bg-red-50 disabled:opacity-50"
                >
                  Revoke access now
                </button>
              )}
            </section>
          )}

          {message && (
            <p role="status" className={`rounded-xl px-4 py-3 text-sm ${message.tone === 'ok' ? 'bg-emerald-50 text-emerald-800 border border-emerald-200' : 'bg-red-50 text-red-700 border border-red-200'}`}>
              {message.text}
            </p>
          )}
        </div>

        <WatchCard detail={detail} />
      </div>

      {confirmRevoke && (
        <ConfirmDialog
          title={`Revoke ${s.studentName}'s access?`}
          message="The link stops working immediately, even mid-video. You can approve a new request later."
          confirmLabel="Revoke"
          busyLabel="Revoking..."
          cancelLabel="Keep access"
          busy={busy === 'revoke'}
          onConfirm={revoke}
          onCancel={() => setConfirmRevoke(false)}
        />
      )}
    </div>
  )
}

function ExpiryBar({ status, decidedAt, expiresAt, left }: {
  status: string; decidedAt: string | null; expiresAt: string; left: string | null
}) {
  if (status === 'REVOKED') {
    return <p className="mt-3 text-xs font-medium text-slate-500">Access was revoked.</p>
  }
  if (!left) {
    return <p className="mt-3 text-xs font-medium text-slate-500">Access ended {formatDateTime(expiresAt)}.</p>
  }
  const start = decidedAt ? new Date(decidedAt).getTime() : Date.now()
  const end = new Date(expiresAt).getTime()
  const usedPercent = Math.min(100, Math.max(0, ((Date.now() - start) / Math.max(1, end - start)) * 100))
  return (
    <div className="mt-3">
      <div className="flex justify-between text-xs">
        <span className="font-medium text-amber-700">Expires in {left}</span>
        <span className="text-slate-400">until {formatDateTime(expiresAt)}</span>
      </div>
      <div className="mt-1.5 h-1.5 rounded-full bg-slate-100" aria-hidden="true">
        <div className="h-1.5 rounded-full bg-amber-500" style={{ width: `${100 - usedPercent}%` }} />
      </div>
    </div>
  )
}

function WatchCard({ detail }: { detail: RequestDetail }) {
  const s = detail.summary
  if (s.status === 'PENDING' || s.status === 'DENIED') {
    return (
      <section className="bg-white rounded-xl border border-slate-200 p-4">
        <h3 className="text-sm font-semibold text-slate-900">Did they watch it?</h3>
        <p className="text-xs text-slate-500 mt-1">Viewing activity shows up here once the request is approved.</p>
      </section>
    )
  }

  const percent = s.watchPercent
  const circumference = 2 * Math.PI * 15.5
  return (
    <section className="bg-white rounded-xl border border-slate-200 p-4">
      <h3 className="text-sm font-semibold text-slate-900">Did they watch it?</h3>
      <div className="mt-3 flex items-center gap-4">
        <div className="relative w-20 h-20 shrink-0" role="img" aria-label={`Watched ${percent}% of the recording`}>
          <svg viewBox="0 0 36 36" className="w-20 h-20 -rotate-90">
            <circle cx="18" cy="18" r="15.5" fill="none" stroke="#e2e8f0" strokeWidth="4" />
            {percent > 0 && (
              <circle
                cx="18" cy="18" r="15.5" fill="none" stroke="#059669" strokeWidth="4" strokeLinecap="round"
                strokeDasharray={`${(percent / 100) * circumference} ${circumference}`}
              />
            )}
          </svg>
          <span className="absolute inset-0 grid place-items-center text-base font-bold tabular-nums">{percent}%</span>
        </div>
        <dl className="grid grid-cols-2 gap-x-4 gap-y-2 text-xs flex-1 min-w-0">
          <div>
            <dt className="text-slate-500">Watched</dt>
            <dd className="font-semibold tabular-nums">
              {detail.durationSeconds ? `${formatDuration(detail.watchedSeconds)} of ${formatDuration(detail.durationSeconds)}` : '—'}
            </dd>
          </div>
          <div>
            <dt className="text-slate-500">Times opened</dt>
            <dd className="font-semibold tabular-nums">{s.openCount}</dd>
          </div>
          <div>
            <dt className="text-slate-500">First opened</dt>
            <dd className="font-semibold">{detail.firstOpenedAt ? timeAgo(detail.firstOpenedAt) : 'Not yet'}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Last activity</dt>
            <dd className="font-semibold">{s.lastWatchedAt ? timeAgo(s.lastWatchedAt) : '—'}</dd>
          </div>
        </dl>
      </div>

      {detail.sessions.length > 0 ? (
        <ol className="mt-4 border-l-2 border-slate-100 ml-1.5 space-y-3">
          {detail.sessions.map((session, i) => (
            <li key={`${session.openedAt}-${i}`} className="pl-4 relative">
              <span
                aria-hidden="true"
                className={`absolute -left-[7px] top-1 w-3 h-3 rounded-full ring-4 ring-white ${!session.success ? 'bg-red-300' : session.maxPositionSeconds ? 'bg-emerald-500' : 'bg-slate-300'}`}
              />
              <p className="text-xs font-medium text-slate-800">
                {!session.success
                  ? 'Tried to open after access ended'
                  : session.maxPositionSeconds
                    ? `Watched up to ${formatClock(session.maxPositionSeconds)}`
                    : 'Opened the link'}
              </p>
              <p className="text-[11px] text-slate-400">
                {formatDateTime(session.openedAt)}{session.device ? ` · ${session.device}` : ''}
              </p>
            </li>
          ))}
        </ol>
      ) : (
        <p className="mt-4 text-xs text-slate-500">The student hasn't opened the link yet.</p>
      )}
      <p className="mt-4 text-[11px] text-slate-400">
        "Watched" counts the parts of the video that actually played, so skipping ahead doesn't count.
      </p>
    </section>
  )
}
