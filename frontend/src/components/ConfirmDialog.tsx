import type { ReactNode } from 'react'
import { BottomSheet } from './BottomSheet'

/** Confirmation for destructive actions -- e.g. removing a student from a batch
 *  or revoking a recording link. Tap outside or Cancel to back out. */
export function ConfirmDialog({
  title, message, confirmLabel, busyLabel = 'Working...', cancelLabel = 'Cancel', busy, onConfirm, onCancel,
}: {
  title?: ReactNode
  message: ReactNode
  confirmLabel: string
  busyLabel?: string
  cancelLabel?: string
  busy?: boolean
  onConfirm: () => void
  onCancel: () => void
}) {
  return (
    <BottomSheet title={title} onClose={onCancel}>
      <p className="text-sm text-slate-600">{message}</p>
      <div className="mt-5 grid grid-cols-2 gap-2">
        <button
          onClick={onCancel}
          className="rounded-xl bg-slate-100 px-4 py-3 text-sm font-medium text-slate-700 hover:bg-slate-200"
        >
          {cancelLabel}
        </button>
        <button
          onClick={onConfirm} disabled={busy}
          className="rounded-xl bg-red-600 px-4 py-3 text-sm font-semibold text-white hover:bg-red-700 disabled:opacity-50"
        >
          {busy ? busyLabel : confirmLabel}
        </button>
      </div>
    </BottomSheet>
  )
}
