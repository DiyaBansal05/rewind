import type { ReactNode } from 'react'

/** Small blocking confirmation dialog for destructive actions -- e.g. removing a
 *  student from a batch. Click outside or Cancel to back out without side effects. */
export function ConfirmDialog({
  message, confirmLabel, busy, onConfirm, onCancel,
}: {
  message: ReactNode
  confirmLabel: string
  busy?: boolean
  onConfirm: () => void
  onCancel: () => void
}) {
  return (
    <div className="fixed inset-0 z-40 bg-black/40 flex items-center justify-center p-4" onClick={onCancel}>
      <div className="w-full max-w-sm rounded-xl bg-white shadow-lg p-5" onClick={(e) => e.stopPropagation()}>
        <p className="text-sm text-slate-700">{message}</p>
        <div className="mt-5 grid grid-cols-2 gap-2">
          <button
            onClick={onCancel}
            className="rounded-md border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
          >
            Cancel
          </button>
          <button
            onClick={onConfirm} disabled={busy}
            className="rounded-md bg-red-600 px-4 py-2 text-sm font-medium text-white hover:bg-red-700 disabled:opacity-50"
          >
            {busy ? 'Removing...' : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}
