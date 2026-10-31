import type { RecordingRequestStatus } from '../api/types'

const STATUS_STYLES: Record<RecordingRequestStatus, string> = {
  PENDING: 'bg-amber-100 text-amber-700',
  APPROVED: 'bg-emerald-100 text-emerald-700',
  DENIED: 'bg-red-100 text-red-700',
  REVOKED: 'bg-slate-200 text-slate-600',
  EXPIRED: 'bg-slate-200 text-slate-600',
}

export function StatusBadge({ status, className = '' }: { status: RecordingRequestStatus; className?: string }) {
  return (
    <span className={`shrink-0 rounded-full px-2 py-0.5 text-[10px] font-semibold tracking-wide ${STATUS_STYLES[status]} ${className}`}>
      {status}
    </span>
  )
}
