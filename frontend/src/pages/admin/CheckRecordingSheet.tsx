import { useEffect, useState } from 'react'
import { apiFetch } from '../../api/client'
import type { CheckResponse } from '../../api/types'
import { BottomSheet } from '../../components/BottomSheet'
import { formatClassDate } from '../../lib/format'
import { formatTimeRange } from '../../lib/schedule'
import { INSTITUTE_TIMEZONE } from '../../constants'

/**
 * Lets the admin watch the recording a request would get before approving
 * it. Nothing changes until they tap Approve or Deny here.
 */
export function CheckRecordingSheet({
  requestId, studentName, initialFileId, onClose, onApprove, onDeny,
}: {
  requestId: string
  studentName: string
  initialFileId?: string
  onClose: () => void
  onApprove: (zoomRecordingFileId: string) => void
  onDeny: () => void
}) {
  const [data, setData] = useState<CheckResponse | null>(null)
  const [failed, setFailed] = useState(false)
  const [selectedId, setSelectedId] = useState<string | null>(initialFileId ?? null)

  useEffect(() => {
    apiFetch<CheckResponse>(`/api/admin/recording-requests/${requestId}/check`)
      .then((d) => {
        setData(d)
        setSelectedId((prev) => (prev && d.candidates.some((c) => c.recordingFileId === prev) ? prev : d.candidates[0]?.recordingFileId ?? null))
      })
      .catch(() => setFailed(true))
  }, [requestId])

  const selected = data?.candidates.find((c) => c.recordingFileId === selectedId)

  return (
    <BottomSheet title="Check recording" onClose={onClose}>
      {data ? (
        <p className="text-sm text-slate-500">
          {studentName} · {data.batchName} · {formatClassDate(data.classDate)}
        </p>
      ) : (
        <p className="text-sm text-slate-500">{studentName}</p>
      )}

      {failed && <p className="mt-4 text-sm text-red-600">Couldn't look up the recording. Close this and try again.</p>}
      {!data && !failed && <p className="mt-6 mb-4 text-sm text-slate-400 text-center">Finding the recording in Zoom...</p>}

      {data && (
        <>
          {data.classStart && data.classEnd && (
            <p className="mt-2 text-xs text-slate-500">
              Class time used: <span className="font-medium text-slate-700">{formatTimeRange(data.classStart, data.classEnd)}</span>
              {data.classTimeChanged && <span className="ml-1 rounded-full bg-amber-50 px-1.5 py-0.5 text-[10px] font-medium text-amber-700">changed for this day</span>}
            </p>
          )}

          {data.candidates.length === 0 ? (
            <div className="mt-4 rounded-xl bg-slate-50 p-4 text-sm text-slate-700">
              {data.message ?? 'No recording found for this class.'}
            </div>
          ) : (
            <>
              {data.candidates.length > 1 && (
                <div className="mt-3">
                  <p className="text-xs font-medium text-slate-600">
                    {data.candidates.length} recordings match this class time. Watch each and approve the right one.
                  </p>
                  <div className="mt-2 -mx-5 px-5 flex gap-2 overflow-x-auto no-scrollbar" role="radiogroup" aria-label="Recordings">
                    {data.candidates.map((c, i) => (
                      <button
                        key={c.recordingFileId} role="radio" aria-checked={c.recordingFileId === selectedId}
                        onClick={() => setSelectedId(c.recordingFileId)}
                        className={`shrink-0 rounded-xl px-3 py-2 text-left text-xs ${c.recordingFileId === selectedId ? 'bg-slate-900 text-white' : 'bg-slate-100 text-slate-700'}`}
                      >
                        <span className="block font-semibold">Recording {i + 1}</span>
                        <span className="block opacity-80">starts {startTime(c.startTime)}</span>
                      </button>
                    ))}
                  </div>
                </div>
              )}

              {selected && (
                <div className="mt-3">
                  <video
                    key={selected.recordingFileId} src={selected.previewUrl}
                    controls playsInline preload="metadata" controlsList="nodownload"
                    className="w-full aspect-video rounded-xl bg-black"
                  />
                  <p className="mt-1.5 text-xs text-slate-600">
                    <span className="font-medium">{selected.meetingTopic}</span> · started {startTime(selected.startTime)}
                  </p>
                  <p className="text-[11px] text-slate-400">Video not loading? The preview link lasts 15 minutes; reopen this to refresh it.</p>
                </div>
              )}
            </>
          )}

          <div className="mt-5 grid grid-cols-2 gap-2">
            <button onClick={onDeny} className="rounded-xl bg-slate-100 py-3 text-sm font-medium text-slate-700">
              Deny request
            </button>
            {selected ? (
              <button onClick={() => onApprove(selected.recordingFileId)} className="rounded-xl bg-emerald-600 py-3 text-sm font-semibold text-white">
                Approve this one
              </button>
            ) : (
              <button onClick={onClose} className="rounded-xl border border-slate-200 py-3 text-sm font-medium text-slate-700">
                Close
              </button>
            )}
          </div>
        </>
      )}
    </BottomSheet>
  )
}

function startTime(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    weekday: 'short', day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit', timeZone: INSTITUTE_TIMEZONE,
  })
}
