import { useState } from 'react'
import { apiFetch, ApiError } from '../../api/client'
import type { QueueItem } from '../../api/types'
import { useAutoRefresh } from '../../hooks/useAutoRefresh'
import { RefreshButton } from './shared'

export function AdminRequestsPage() {
  const [queue, setQueue] = useState<QueueItem[]>([])
  const [decidingId, setDecidingId] = useState<string | null>(null)
  const [decideError, setDecideError] = useState<{ id: string; message: string } | null>(null)

  function refresh() {
    apiFetch<QueueItem[]>('/api/admin/recording-requests').then(setQueue).catch(() => {})
  }

  useAutoRefresh(refresh)

  async function decide(id: string, action: 'approve' | 'deny') {
    setDecidingId(id)
    setDecideError(null)
    try {
      await apiFetch(`/api/admin/recording-requests/${id}/${action}`, { method: 'POST' })
      refresh()
    } catch (err) {
      setDecideError({ id, message: describeDecideError(err) })
    } finally {
      setDecidingId(null)
    }
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-3">
        <div>
          <h2 className="text-base font-semibold text-slate-900">Pending requests</h2>
          <p className="text-xs text-slate-400">{queue.length} waiting</p>
        </div>
        <RefreshButton onClick={refresh} />
      </div>

      {queue.length === 0 ? (
        <p className="text-sm text-slate-400 py-8 text-center">Nothing pending right now.</p>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-2.5">
          {queue.map((item) => {
            const isDeciding = decidingId === item.id
            return (
              <div key={item.id} className="bg-white rounded-xl border border-slate-200 shadow-sm p-3">
                <div className="flex items-center justify-between gap-2">
                  <p className="text-sm truncate">
                    <span className="font-semibold">{item.studentName}</span>{' '}
                    <span className="text-slate-400 text-xs">{item.studentPhone}</span>
                  </p>
                  <span className="shrink-0 text-[9px] font-medium bg-amber-100 text-amber-700 px-1.5 py-0.5 rounded-full">PENDING</span>
                </div>
                <p className="text-xs text-slate-500 mt-0.5">{item.batchName} &middot; {item.classDate}</p>
                <div className="mt-2 grid grid-cols-2 gap-2">
                  <button
                    onClick={() => decide(item.id, 'approve')} disabled={isDeciding}
                    className="bg-emerald-600 text-white font-medium text-sm rounded-lg py-2 active:scale-95 transition disabled:opacity-50"
                  >
                    {isDeciding ? 'Working...' : 'Approve'}
                  </button>
                  <button
                    onClick={() => decide(item.id, 'deny')} disabled={isDeciding}
                    className="bg-slate-100 text-slate-600 font-medium text-sm rounded-lg py-2 active:scale-95 transition disabled:opacity-50"
                  >
                    Deny
                  </button>
                </div>
                {decideError?.id === item.id && (
                  <p className="mt-2 text-xs text-red-600">{decideError.message}</p>
                )}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}

function describeDecideError(err: unknown): string {
  if (!(err instanceof ApiError)) return 'Something went wrong. Try again.'
  try {
    const body = JSON.parse(err.message) as { reason?: string; message?: string }
    switch (body.reason) {
      case 'ALREADY_DECIDED':
        return 'This request was already approved or denied (maybe from another tab).'
      case 'NO_MATCH':
        return body.message ?? 'No matching Zoom recording found for this class date/time yet.'
      case 'MULTIPLE_CANDIDATES':
        return 'Multiple possible recordings match this time window -- manual selection isn\'t built into this view yet.'
      case 'LOOKUP_NOT_ALLOWED':
        return body.message ?? 'That date is outside the allowed lookup window.'
      default:
        return body.message ?? 'Something went wrong. Try again.'
    }
  } catch {
    return 'Something went wrong. Try again.'
  }
}
