import { useState } from 'react'
import { apiFetch } from '../../api/client'
import type { Batch, StudentSummary, StudentRequestDetail } from '../../api/types'
import { useAutoRefresh } from '../../hooks/useAutoRefresh'
import { RefreshButton } from './shared'

const STATUS_STYLES: Record<string, string> = {
  PENDING: 'bg-amber-100 text-amber-700',
  APPROVED: 'bg-emerald-100 text-emerald-700',
  DENIED: 'bg-red-100 text-red-700',
  REVOKED: 'bg-slate-100 text-slate-600',
  EXPIRED: 'bg-slate-100 text-slate-600',
}

export function AdminStudentsPage() {
  const [students, setStudents] = useState<StudentSummary[]>([])
  const [batches, setBatches] = useState<Batch[]>([])
  const [expandedStudentId, setExpandedStudentId] = useState<string | null>(null)
  const [studentRequests, setStudentRequests] = useState<StudentRequestDetail[]>([])
  const [studentSearch, setStudentSearch] = useState('')
  const [studentBatchFilter, setStudentBatchFilter] = useState('ALL')

  function refresh() {
    apiFetch<StudentSummary[]>('/api/admin/students').then(setStudents).catch(() => {})
    apiFetch<Batch[]>('/api/admin/batches').then(setBatches).catch(() => {})
  }

  useAutoRefresh(refresh)

  const filteredStudents = students.filter((s) => {
    const matchesSearch = studentSearch.trim() === ''
      || s.name.toLowerCase().includes(studentSearch.trim().toLowerCase())
      || s.phoneNumber.includes(studentSearch.trim())
    const matchesBatch = studentBatchFilter === 'ALL'
      || s.batches.some((b) => b.batchId === studentBatchFilter)
    return matchesSearch && matchesBatch
  })

  async function toggleStudent(studentId: string) {
    if (expandedStudentId === studentId) {
      setExpandedStudentId(null)
      return
    }
    setExpandedStudentId(studentId)
    const requests = await apiFetch<StudentRequestDetail[]>(`/api/admin/students/${studentId}/recording-requests`)
    setStudentRequests(requests)
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-3">
        <div>
          <h2 className="text-base font-semibold text-slate-900">Students</h2>
          <p className="text-xs text-slate-400">{students.length} registered</p>
        </div>
        <RefreshButton onClick={refresh} />
      </div>

      {students.length > 0 && (
        <div className="flex flex-wrap gap-2 mb-3">
          <input
            type="text" value={studentSearch} onChange={(e) => setStudentSearch(e.target.value)}
            placeholder="Search by name or phone..."
            className="flex-1 min-w-[10rem] rounded-md border border-slate-300 px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-slate-900"
          />
          <select
            value={studentBatchFilter} onChange={(e) => setStudentBatchFilter(e.target.value)}
            className="rounded-md border border-slate-300 px-2 py-1.5 text-sm"
          >
            <option value="ALL">All batches</option>
            {batches.map((b) => (
              <option key={b.id} value={b.id}>{b.name}</option>
            ))}
          </select>
        </div>
      )}

      {students.length === 0 ? (
        <p className="text-sm text-slate-400 py-8 text-center">No students registered yet.</p>
      ) : filteredStudents.length === 0 ? (
        <p className="text-sm text-slate-400 py-8 text-center">No students match your search/filter.</p>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-2.5">
          {filteredStudents.map((s) => (
            <div key={s.id} className="bg-white rounded-xl border border-slate-200 shadow-sm p-3">
              <button onClick={() => toggleStudent(s.id)} className="flex w-full items-center justify-between gap-2 text-left">
                <p className="text-sm truncate">
                  <span className="font-semibold">{s.name}</span>{' '}
                  <span className="text-slate-400 text-xs">{s.phoneNumber}</span>
                </p>
                <span className="shrink-0 rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-medium text-slate-600">
                  {s.totalRequests} req
                </span>
              </button>
              <p className="text-xs text-slate-500 mt-0.5">{s.batches.map((b) => b.batchName).join(', ') || 'Not enrolled'}</p>

              {expandedStudentId === s.id && (
                <div className="mt-2 space-y-3 rounded-lg bg-slate-50 p-3">
                  {s.batches.length === 0 ? (
                    <p className="text-xs text-slate-400">Not enrolled in any batch.</p>
                  ) : (
                    s.batches.map((b) => {
                      const batchRequests = studentRequests.filter((r) => r.batchId === b.batchId)
                      return (
                        <div key={b.batchId}>
                          <p className="text-[10px] font-semibold uppercase tracking-wide text-slate-500">{b.batchName}</p>
                          {batchRequests.length === 0 ? (
                            <p className="mt-1 text-xs text-slate-400">No requests for this batch.</p>
                          ) : (
                            <ul className="mt-1 space-y-1">
                              {batchRequests.map((r) => (
                                <li key={r.id} className="flex items-center justify-between text-xs">
                                  <span className="text-slate-700">{r.classDate}</span>
                                  <span className={`rounded-full px-1.5 py-0.5 text-[9px] font-medium ${STATUS_STYLES[r.status] ?? ''}`}>{r.status}</span>
                                </li>
                              ))}
                            </ul>
                          )}
                        </div>
                      )
                    })
                  )}
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
