import { useState } from 'react'
import { QRCodeSVG } from 'qrcode.react'
import { apiFetch } from '../../api/client'
import type { Batch, RegistrationLink } from '../../api/types'
import { useAutoRefresh } from '../../hooks/useAutoRefresh'
import { RefreshButton, Fab } from './shared'
import { CreateBatchForm } from './CreateBatchForm'

export function AdminBatchesPage() {
  const [batches, setBatches] = useState<Batch[]>([])
  const [showCreateForm, setShowCreateForm] = useState(false)
  const [qrBatchId, setQrBatchId] = useState<string | null>(null)
  const [qrLink, setQrLink] = useState<RegistrationLink | null>(null)

  function refresh() {
    apiFetch<Batch[]>('/api/admin/batches').then(setBatches).catch(() => {})
  }

  useAutoRefresh(refresh)

  async function showQr(batchId: string) {
    setQrBatchId(batchId)
    setQrLink(null)
    const link = await apiFetch<RegistrationLink>(`/api/admin/batches/${batchId}/registration-link`)
    setQrLink(link)
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

      {batches.length === 0 ? (
        <p className="text-sm text-slate-400 py-8 text-center">No batches yet.</p>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-2.5">
          {batches.map((b) => (
            <div key={b.id} className="bg-white rounded-xl border border-slate-200 shadow-sm p-3">
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <p className="text-sm font-semibold truncate">{b.name}</p>
                  <p className="text-xs text-slate-500 mt-0.5">
                    {b.courseName} &middot; {b.classDaysOfWeek.map((d) => d.slice(0, 3)).join(', ')} &middot; {b.classStartTime.slice(0, 5)}&ndash;{b.classEndTime.slice(0, 5)}
                  </p>
                </div>
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
            </div>
          ))}
        </div>
      )}

      <Fab onClick={() => setShowCreateForm(true)} label="New batch" />
    </div>
  )
}
