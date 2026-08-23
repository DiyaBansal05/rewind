import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { apiFetch } from '../../api/client'
import type { Batch, EnrolledStudent } from '../../api/types'
import { useAutoRefresh } from '../../hooks/useAutoRefresh'
import { ConfirmDialog } from '../../components/ConfirmDialog'
import { RefreshButton } from './shared'

export function AdminBatchDetailPage() {
  const { batchId } = useParams<{ batchId: string }>()
  const navigate = useNavigate()

  const [batch, setBatch] = useState<Batch | null>(null)
  const [notFound, setNotFound] = useState(false)
  const [roster, setRoster] = useState<EnrolledStudent[]>([])
  const [removeTarget, setRemoveTarget] = useState<EnrolledStudent | null>(null)
  const [removing, setRemoving] = useState(false)

  function refresh() {
    if (!batchId) return
    apiFetch<Batch>(`/api/admin/batches/${batchId}`).then((b) => { setBatch(b); setNotFound(false) }).catch(() => setNotFound(true))
    apiFetch<EnrolledStudent[]>(`/api/admin/batches/${batchId}/students`).then(setRoster).catch(() => {})
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

  if (notFound) {
    return (
      <div>
        <Link to="/admin/batches" className="text-sm text-slate-500 hover:text-slate-800">&larr; Back to batches</Link>
        <p className="mt-6 text-sm text-slate-400 text-center py-8">This batch no longer exists.</p>
      </div>
    )
  }

  return (
    <div>
      <button onClick={() => navigate('/admin/batches')} className="text-sm text-slate-500 hover:text-slate-800">
        &larr; Back to batches
      </button>

      <div className="mt-3 flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold text-slate-900">{batch?.name ?? 'Loading...'}</h2>
          {batch && (
            <p className="text-xs text-slate-500 mt-0.5">
              {batch.courseName} &middot; {batch.classDaysOfWeek.map((d) => d.slice(0, 3)).join(', ')} &middot; {batch.classStartTime.slice(0, 5)}&ndash;{batch.classEndTime.slice(0, 5)}
            </p>
          )}
        </div>
        <RefreshButton onClick={refresh} />
      </div>

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
                  className="shrink-0 rounded-md border border-red-200 px-3 py-1.5 text-sm font-medium text-red-600 hover:bg-red-50"
                >
                  Remove
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>

      {removeTarget && batch && (
        <ConfirmDialog
          message={<>Are you sure you want to remove <span className="font-semibold">{removeTarget.name}</span> from <span className="font-semibold">{batch.name}</span>?</>}
          confirmLabel="Remove"
          busy={removing}
          onConfirm={confirmRemove}
          onCancel={() => setRemoveTarget(null)}
        />
      )}
    </div>
  )
}
