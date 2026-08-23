import { useState } from 'react'
import { QRCodeSVG } from 'qrcode.react'
import { apiFetch } from '../../api/client'
import type { Batch, EnrolledStudent, EnrollmentRequestItem, RegistrationLink } from '../../api/types'
import { useAutoRefresh } from '../../hooks/useAutoRefresh'
import { RefreshButton, Fab } from './shared'
import { CreateBatchForm } from './CreateBatchForm'

export function AdminBatchesPage() {
  const [batches, setBatches] = useState<Batch[]>([])
  const [showCreateForm, setShowCreateForm] = useState(false)
  const [qrBatchId, setQrBatchId] = useState<string | null>(null)
  const [qrLink, setQrLink] = useState<RegistrationLink | null>(null)

  const [joinRequests, setJoinRequests] = useState<EnrollmentRequestItem[]>([])
  const [decidingRequestId, setDecidingRequestId] = useState<string | null>(null)

  const [expandedBatchId, setExpandedBatchId] = useState<string | null>(null)
  const [roster, setRoster] = useState<EnrolledStudent[]>([])
  const [removingId, setRemovingId] = useState<string | null>(null)

  function refresh() {
    apiFetch<Batch[]>('/api/admin/batches').then(setBatches).catch(() => {})
    apiFetch<EnrollmentRequestItem[]>('/api/admin/enrollment-requests').then(setJoinRequests).catch(() => {})
  }

  useAutoRefresh(refresh)

  async function showQr(batchId: string) {
    setQrBatchId(batchId)
    setQrLink(null)
    const link = await apiFetch<RegistrationLink>(`/api/admin/batches/${batchId}/registration-link`)
    setQrLink(link)
  }

  async function toggleRoster(batchId: string) {
    if (expandedBatchId === batchId) {
      setExpandedBatchId(null)
      return
    }
    setExpandedBatchId(batchId)
    const students = await apiFetch<EnrolledStudent[]>(`/api/admin/batches/${batchId}/students`)
    setRoster(students)
  }

  async function decide(requestId: string, action: 'approve' | 'deny') {
    setDecidingRequestId(requestId)
    try {
      await apiFetch(`/api/admin/enrollment-requests/${requestId}/${action}`, { method: 'POST' })
      refresh()
    } catch {
      // Swallow -- the request stays in the list and the admin can just retry.
    } finally {
      setDecidingRequestId(null)
    }
  }

  async function removeStudent(enrollmentId: string) {
    setRemovingId(enrollmentId)
    try {
      await apiFetch(`/api/admin/enrollments/${enrollmentId}`, { method: 'DELETE' })
      setRoster((prev) => prev.filter((s) => s.enrollmentId !== enrollmentId))
    } finally {
      setRemovingId(null)
    }
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-3">
        <div>
          <h2 className="text-base font-semibold text-slate-900">Batches</h2>
          <p className="text-xs text-slate-400">{batches.length} total</p>
        </div>
        <div className="flex items-center gap-2">
          <RefreshButton onClick={refresh} />
          <button
            onClick={() => setShowCreateForm((v) => !v)}
            className="hidden md:inline-block rounded-md bg-slate-900 px-3 py-1.5 text-xs font-medium text-white hover:bg-slate-800"
          >
            {showCreateForm ? 'Cancel' : 'New batch'}
          </button>
        </div>
      </div>

      {showCreateForm && (
        <div className="mb-3">
          <CreateBatchForm
            onCreated={() => { setShowCreateForm(false); refresh() }}
            onCancel={() => setShowCreateForm(false)}
          />
        </div>
      )}

      {joinRequests.length > 0 && (
        <div className="mb-4">
          <h3 className="text-sm font-semibold text-slate-900 mb-2">Join requests</h3>
          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-2.5">
            {joinRequests.map((r) => {
              const isDeciding = decidingRequestId === r.id
              return (
                <div key={r.id} className="bg-white rounded-xl border border-amber-200 shadow-sm p-3">
                  <p className="text-sm truncate">
                    <span className="font-semibold">{r.studentName}</span>{' '}
                    <span className="text-slate-400 text-xs">{r.studentPhone}</span>
                  </p>
                  <p className="text-xs text-slate-500 mt-0.5">wants to join {r.batchName} &middot; {r.courseName}</p>
                  <div className="mt-2 grid grid-cols-2 gap-2">
                    <button
                      onClick={() => decide(r.id, 'approve')} disabled={isDeciding}
                      className="bg-emerald-600 text-white font-medium text-sm rounded-lg py-2 active:scale-95 transition disabled:opacity-50"
                    >
                      {isDeciding ? 'Working...' : 'Approve'}
                    </button>
                    <button
                      onClick={() => decide(r.id, 'deny')} disabled={isDeciding}
                      className="bg-slate-100 text-slate-600 font-medium text-sm rounded-lg py-2 active:scale-95 transition disabled:opacity-50"
                    >
                      Deny
                    </button>
                  </div>
                </div>
              )
            })}
          </div>
        </div>
      )}

      {batches.length === 0 ? (
        <p className="text-sm text-slate-400 py-8 text-center">No batches yet.</p>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-2.5">
          {batches.map((b) => (
            <div key={b.id} className="bg-white rounded-xl border border-slate-200 shadow-sm p-3">
              <div className="flex items-start justify-between gap-2">
                <button onClick={() => toggleRoster(b.id)} className="min-w-0 text-left flex-1">
                  <p className="text-sm font-semibold truncate">{b.name}</p>
                  <p className="text-xs text-slate-500 mt-0.5">
                    {b.courseName} &middot; {b.classDaysOfWeek.map((d) => d.slice(0, 3)).join(', ')} &middot; {b.classStartTime.slice(0, 5)}&ndash;{b.classEndTime.slice(0, 5)}
                  </p>
                </button>
                <button
                  onClick={() => showQr(b.id)}
                  className="shrink-0 rounded-lg border border-slate-300 px-2.5 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50"
                >
                  QR
                </button>
              </div>
              {qrBatchId === b.id && qrLink && (
                <div className="mt-3 flex flex-col items-start gap-2 rounded-lg bg-slate-50 p-3">
                  <QRCodeSVG value={`${window.location.origin}${qrLink.registrationPath}`} size={140} />
                  <input
                    readOnly
                    value={`${window.location.origin}${qrLink.registrationPath}`}
                    className="w-full rounded border border-slate-200 bg-white px-2 py-1 text-xs text-slate-500"
                    onFocus={(e) => e.currentTarget.select()}
                  />
                </div>
              )}
              {expandedBatchId === b.id && (
                <div className="mt-3 rounded-lg bg-slate-50 p-3">
                  <p className="text-[10px] font-semibold uppercase tracking-wide text-slate-500">
                    Students ({roster.length})
                  </p>
                  {roster.length === 0 ? (
                    <p className="mt-1 text-xs text-slate-400">No approved students yet.</p>
                  ) : (
                    <ul className="mt-1.5 space-y-1.5">
                      {roster.map((s) => (
                        <li key={s.enrollmentId} className="flex items-center justify-between gap-2 text-xs">
                          <span className="text-slate-700 truncate">
                            {s.name} <span className="text-slate-400">{s.phoneNumber}</span>
                          </span>
                          <button
                            onClick={() => removeStudent(s.enrollmentId)}
                            disabled={removingId === s.enrollmentId}
                            className="shrink-0 text-red-600 hover:text-red-800 font-medium disabled:opacity-50"
                          >
                            {removingId === s.enrollmentId ? 'Removing...' : 'Remove'}
                          </button>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      <Fab onClick={() => setShowCreateForm(true)} label="New batch" />
    </div>
  )
}
