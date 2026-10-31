import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { apiFetch, ApiError } from '../../api/client'
import type {
  Batch, BulkItemResult, DecisionOutcome, QueueItem, RecordingCandidate, RequestCounts, RequestTab,
} from '../../api/types'
import { useAutoRefresh } from '../../hooks/useAutoRefresh'
import { StatusBadge } from '../../components/StatusBadge'
import { formatClassDate, timeAgo, timeUntil } from '../../lib/format'
import { RefreshButton } from './shared'
import { useAdminOutlet } from './adminOutlet'
import { CheckRecordingSheet } from './CheckRecordingSheet'
import { INSTITUTE_TIMEZONE } from '../../constants'

interface DecideErrorBody {
  reason?: DecisionOutcome
  message?: string
  candidates?: RecordingCandidate[]
}

const TABS: { key: RequestTab; label: string }[] = [
  { key: 'PENDING', label: 'Pending' },
  { key: 'APPROVED', label: 'Approved' },
  { key: 'ALL', label: 'All' },
]

export function AdminRequestsPage() {
  const { refreshBadges } = useAdminOutlet()
  const [params, setParams] = useSearchParams()
  const tab = (params.get('tab') as RequestTab) || 'PENDING'
  const batchFilter = params.get('batch') ?? ''
  const from = params.get('from') ?? ''
  const to = params.get('to') ?? ''
  const query = params.get('q') ?? ''

  const [searchInput, setSearchInput] = useState(query)
  const [showDates, setShowDates] = useState(Boolean(from || to))
  const [items, setItems] = useState<QueueItem[]>([])
  const [loaded, setLoaded] = useState(false)
  const [counts, setCounts] = useState<RequestCounts | null>(null)
  const [batches, setBatches] = useState<Batch[]>([])

  const [busyIds, setBusyIds] = useState<Set<string>>(new Set())
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [candidatesByRequest, setCandidatesByRequest] = useState<Record<string, RecordingCandidate[]>>({})
  const [selectMode, setSelectMode] = useState(false)
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [bulkBusy, setBulkBusy] = useState(false)
  const [bulkSummary, setBulkSummary] = useState<string | null>(null)
  const [checking, setChecking] = useState<{ id: string; name: string; fileId?: string } | null>(null)

  function updateParams(changes: Record<string, string>) {
    const next = new URLSearchParams(params)
    for (const [k, v] of Object.entries(changes)) {
      if (v) next.set(k, v)
      else next.delete(k)
    }
    setParams(next, { replace: true })
  }

  function refresh() {
    const qs = new URLSearchParams({ status: tab })
    if (batchFilter) qs.set('batchId', batchFilter)
    if (query) qs.set('q', query)
    if (from) qs.set('from', from)
    if (to) qs.set('to', to)
    apiFetch<QueueItem[]>(`/api/admin/recording-requests?${qs}`)
      .then((rows) => { setItems(rows); setLoaded(true) })
      .catch(() => setLoaded(true))
    apiFetch<RequestCounts>('/api/admin/recording-requests/counts').then(setCounts).catch(() => {})
  }

  useAutoRefresh(refresh, [tab, batchFilter, query, from, to])
  useAutoRefresh(() => {
    apiFetch<Batch[]>('/api/admin/batches').then(setBatches).catch(() => {})
  })
  useEffect(() => {
    const handle = setTimeout(() => { if (searchInput !== query) updateParams({ q: searchInput.trim() }) }, 300)
    return () => clearTimeout(handle)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchInput])

  const pendingVisible = useMemo(() => items.filter((i) => i.status === 'PENDING'), [items])
  const canSelect = tab !== 'APPROVED' && pendingVisible.length > 0
  const filtersActive = Boolean(batchFilter || query || from || to)

  function switchTab(next: RequestTab) {
    setSelectMode(false)
    setSelected(new Set())
    setBulkSummary(null)
    updateParams({ tab: next === 'PENDING' ? '' : next })
  }

  function markBusy(ids: string[], busy: boolean) {
    setBusyIds((prev) => {
      const next = new Set(prev)
      ids.forEach((id) => (busy ? next.add(id) : next.delete(id)))
      return next
    })
  }

  function clearFeedback(ids: string[]) {
    setErrors((prev) => omit(prev, ids))
    setCandidatesByRequest((prev) => omit(prev, ids))
  }

  async function decide(id: string, action: 'approve' | 'deny', zoomRecordingFileId?: string) {
    markBusy([id], true)
    clearFeedback([id])
    setBulkSummary(null)
    try {
      await apiFetch(`/api/admin/recording-requests/${id}/${action}`, {
        method: 'POST',
        body: action === 'approve' && zoomRecordingFileId ? JSON.stringify({ zoomRecordingFileId }) : undefined,
      })
      setItems((prev) => prev.filter((i) => i.id !== id || tab !== 'PENDING'))
      refresh()
      refreshBadges()
    } catch (err) {
      const body = parseErrorBody(err)
      if (body?.reason === 'MULTIPLE_CANDIDATES' && body.candidates) {
        setCandidatesByRequest((prev) => ({ ...prev, [id]: body.candidates! }))
      }
      setErrors((prev) => ({ ...prev, [id]: describeOutcome(body?.reason, body?.message) }))
    } finally {
      markBusy([id], false)
    }
  }

  async function bulk(action: 'approve' | 'deny') {
    const ids = [...selected]
    if (ids.length === 0) return
    setBulkBusy(true)
    markBusy(ids, true)
    clearFeedback(ids)
    try {
      const results = await apiFetch<BulkItemResult[]>(`/api/admin/recording-requests/bulk-${action}`, {
        method: 'POST',
        body: JSON.stringify({ ids }),
      })
      const done = results.filter((r) => r.outcome === 'APPROVED' || r.outcome === 'DENIED')
      const needsPick = results.filter((r) => r.outcome === 'MULTIPLE_CANDIDATES')
      const failed = results.filter((r) => !done.includes(r) && !needsPick.includes(r))

      setCandidatesByRequest((prev) => ({ ...prev, ...Object.fromEntries(needsPick.map((r) => [r.id, r.candidates])) }))
      setErrors((prev) => ({
        ...prev,
        ...Object.fromEntries([...needsPick, ...failed].map((r) => [r.id, describeOutcome(r.outcome, r.message)])),
      }))
      // Keep the rows that still need attention selected, so a second pass is one tap.
      setSelected(new Set([...needsPick, ...failed].map((r) => r.id)))
      if (needsPick.length === 0 && failed.length === 0) setSelectMode(false)

      const verb = action === 'approve' ? 'approved' : 'denied'
      const parts = [`${done.length} ${verb}`]
      if (needsPick.length) parts.push(`${needsPick.length} need${needsPick.length === 1 ? 's' : ''} a recording picked`)
      if (failed.length) parts.push(`${failed.length} couldn't be ${verb}`)
      setBulkSummary(parts.join(' · '))
      refresh()
      refreshBadges()
    } catch {
      setBulkSummary('Something went wrong. Nothing was changed. Try again.')
    } finally {
      markBusy(ids, false)
      setBulkBusy(false)
    }
  }

  function toggleSelected(id: string) {
    setSelected((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const allVisibleSelected = pendingVisible.length > 0 && pendingVisible.every((i) => selected.has(i.id))

  return (
    <div>
      <div className="flex items-center justify-between mb-3">
        <div>
          <h2 className="text-base font-semibold text-slate-900">Recording requests</h2>
          <p className="text-xs text-slate-400">
            {counts ? `${counts.PENDING} waiting` : ' '}
          </p>
        </div>
        <RefreshButton onClick={refresh} />
      </div>

      {/* Tabs */}
      <div className="bg-slate-200/70 rounded-xl p-1 grid grid-cols-3 md:max-w-md" role="tablist">
        {TABS.map((t) => (
          <button
            key={t.key} role="tab" aria-selected={tab === t.key} onClick={() => switchTab(t.key)}
            className={`py-2 rounded-lg text-sm font-medium transition ${tab === t.key ? 'bg-white shadow-sm text-slate-900' : 'text-slate-500'}`}
          >
            {t.label}
            {counts && <span className="ml-1 text-[11px] text-slate-400 tabular-nums">{counts[t.key]}</span>}
          </button>
        ))}
      </div>

      {/* Search + date */}
      <div className="mt-3 flex gap-2 md:max-w-xl">
        <label className="flex-1 flex items-center gap-2 bg-white border border-slate-200 rounded-xl px-3 text-slate-400 focus-within:border-slate-400">
          <SearchIcon />
          <input
            id="request-search" type="search" value={searchInput} onChange={(e) => setSearchInput(e.target.value)}
            placeholder="Search name or phone" aria-label="Search by student name, phone or batch"
            className="w-full py-2.5 text-sm text-slate-900 outline-none bg-transparent"
          />
        </label>
        <button
          onClick={() => setShowDates((v) => !v)} aria-expanded={showDates}
          className={`px-3 rounded-xl border text-xs font-medium ${from || to ? 'bg-slate-900 text-white border-slate-900' : 'bg-white border-slate-200 text-slate-600'}`}
        >
          Class date
        </button>
      </div>
      {showDates && (
        <div className="mt-2 flex flex-wrap items-end gap-2 rounded-xl bg-white border border-slate-200 p-3 md:max-w-xl">
          <div className="flex-1 min-w-[8rem]">
            <label htmlFor="from-date" className="block text-[11px] font-medium text-slate-500">From</label>
            <input id="from-date" type="date" value={from} max={to || undefined} onChange={(e) => updateParams({ from: e.target.value })}
              className="mt-1 w-full rounded-lg border border-slate-300 px-2 py-1.5 text-sm" />
          </div>
          <div className="flex-1 min-w-[8rem]">
            <label htmlFor="to-date" className="block text-[11px] font-medium text-slate-500">To</label>
            <input id="to-date" type="date" value={to} min={from || undefined} onChange={(e) => updateParams({ to: e.target.value })}
              className="mt-1 w-full rounded-lg border border-slate-300 px-2 py-1.5 text-sm" />
          </div>
          {(from || to) && (
            <button onClick={() => updateParams({ from: '', to: '' })} className="px-3 py-2 text-xs font-medium text-slate-600">Clear</button>
          )}
        </div>
      )}

      {/* Batch chips */}
      {batches.length > 1 && (
        <div className="mt-2.5 -mx-4 px-4 md:mx-0 md:px-0 flex gap-2 overflow-x-auto no-scrollbar md:flex-wrap">
          <Chip active={!batchFilter} onClick={() => updateParams({ batch: '' })}>All batches</Chip>
          {batches.map((b) => (
            <Chip key={b.id} active={batchFilter === b.id} onClick={() => updateParams({ batch: b.id })}>{b.name}</Chip>
          ))}
        </div>
      )}

      {/* List header */}
      <div className="flex items-center justify-between mt-4 mb-2 min-h-[1.5rem]">
        <p className="text-xs text-slate-500">
          {items.length} shown
          {filtersActive && (
            <button
              onClick={() => { setSearchInput(''); updateParams({ batch: '', q: '', from: '', to: '' }) }}
              className="ml-2 font-medium text-slate-700 underline underline-offset-2"
            >
              Clear filters
            </button>
          )}
        </p>
        {canSelect && (
          <div className="flex items-center gap-4">
            {selectMode && (
              <button
                onClick={() => setSelected(allVisibleSelected ? new Set() : new Set(pendingVisible.map((i) => i.id)))}
                className="text-xs font-semibold text-slate-700 py-1"
              >
                {allVisibleSelected ? 'Select none' : `Select all ${pendingVisible.length}`}
              </button>
            )}
            <button
              onClick={() => { setSelectMode((v) => !v); setSelected(new Set()); setBulkSummary(null) }}
              className="text-xs font-semibold text-emerald-700 py-1"
            >
              {selectMode ? 'Cancel' : 'Select'}
            </button>
          </div>
        )}
      </div>

      {bulkSummary && (
        <div role="status" className="mb-3 flex items-start justify-between gap-3 rounded-xl bg-slate-900 text-white px-4 py-3 text-sm">
          <span>{bulkSummary}</span>
          <button onClick={() => setBulkSummary(null)} className="text-slate-300 text-xs font-medium shrink-0">Dismiss</button>
        </div>
      )}

      {loaded && items.length === 0 ? (
        <EmptyState tab={tab} filtersActive={filtersActive} />
      ) : (
        <ul className={`grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-2.5 ${selectMode ? 'pb-24 md:pb-20' : ''}`}>
          {items.map((item) => (
            <RequestCard
              key={item.id}
              item={item}
              busy={busyIds.has(item.id)}
              error={errors[item.id]}
              candidates={candidatesByRequest[item.id]}
              selectable={selectMode && item.status === 'PENDING'}
              selected={selected.has(item.id)}
              onToggle={() => toggleSelected(item.id)}
              onDecide={(action, fileId) => decide(item.id, action, fileId)}
              onCheck={(fileId) => setChecking({ id: item.id, name: item.studentName, fileId })}
            />
          ))}
        </ul>
      )}

      {checking && (
        <CheckRecordingSheet
          requestId={checking.id}
          studentName={checking.name}
          initialFileId={checking.fileId}
          onClose={() => setChecking(null)}
          onApprove={(fileId) => { const id = checking.id; setChecking(null); decide(id, 'approve', fileId) }}
          onDeny={() => { const id = checking.id; setChecking(null); decide(id, 'deny') }}
        />
      )}

      {/* Bulk action bar: above the bottom nav on phones, pinned at the bottom on desktop */}
      {selectMode && (
        <div className="fixed inset-x-3 bottom-[calc(5rem+env(safe-area-inset-bottom))] md:bottom-6 md:inset-x-auto md:left-1/2 md:-translate-x-1/2 md:w-[28rem] z-40 sheet-in">
          <div className="bg-slate-900 text-white rounded-2xl p-3 flex items-center gap-2 shadow-xl">
            <span className="text-sm font-medium flex-1 tabular-nums pl-1">{selected.size} selected</span>
            <button
              onClick={() => bulk('deny')} disabled={selected.size === 0 || bulkBusy}
              className="px-4 py-2.5 rounded-xl bg-white/10 text-sm font-medium disabled:opacity-40"
            >
              Deny
            </button>
            <button
              onClick={() => bulk('approve')} disabled={selected.size === 0 || bulkBusy}
              className="px-4 py-2.5 rounded-xl bg-emerald-500 text-sm font-semibold disabled:opacity-40"
            >
              {bulkBusy ? 'Working...' : `Approve${selected.size ? ` ${selected.size}` : ''}`}
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

function RequestCard({
  item, busy, error, candidates, selectable, selected, onToggle, onDecide, onCheck,
}: {
  item: QueueItem
  busy: boolean
  error?: string
  candidates?: RecordingCandidate[]
  selectable: boolean
  selected: boolean
  onToggle: () => void
  onDecide: (action: 'approve' | 'deny', zoomRecordingFileId?: string) => void
  onCheck: (zoomRecordingFileId?: string) => void
}) {
  const isPending = item.status === 'PENDING'
  const header = (
    <div className="flex items-start gap-3">
      {selectable && (
        <span
          aria-hidden="true"
          className={`mt-0.5 w-5 h-5 rounded-md border-2 grid place-items-center shrink-0 ${selected ? 'bg-emerald-600 border-emerald-600 text-white' : 'border-slate-300 bg-white'}`}
        >
          {selected && <CheckIcon />}
        </span>
      )}
      <div className="min-w-0 flex-1">
        <div className="flex items-center justify-between gap-2">
          <p className="font-semibold text-sm text-slate-900 truncate">{item.studentName}</p>
          <StatusBadge status={item.status} />
        </div>
        <p className="text-xs text-slate-500 mt-0.5">
          {item.batchName} · class on <span className="font-semibold text-slate-700">{formatClassDate(item.classDate)}</span>
        </p>
        <p className="text-[11px] text-slate-400 mt-0.5">{item.studentPhone} · {timeAgo(item.requestedAt)}</p>
        {!isPending && <AccessChips item={item} />}
      </div>
    </div>
  )

  return (
    <li className={`bg-white rounded-xl border p-3.5 transition ${selected ? 'border-emerald-500 ring-2 ring-emerald-100' : 'border-slate-200'}`}>
      {selectable ? (
        <button onClick={onToggle} aria-pressed={selected} className="w-full text-left">{header}</button>
      ) : isPending ? (
        header
      ) : (
        <Link to={`/admin/requests/${item.id}`} className="block">{header}</Link>
      )}

      {isPending && !selectable && (
        candidates ? (
          <CandidatePicker candidates={candidates} busy={busy} onPick={(fileId) => onDecide('approve', fileId)} onDeny={() => onDecide('deny')} onPreview={onCheck} />
        ) : (
          <>
            <button
              onClick={() => onCheck()} disabled={busy}
              className="mt-3 w-full flex items-center justify-center gap-1.5 rounded-xl border border-emerald-200 bg-emerald-50 py-2 text-xs font-semibold text-emerald-800 disabled:opacity-50"
            >
              <PlaySmallIcon /> Check recording
            </button>
            <div className="mt-2 grid grid-cols-2 gap-2">
              <button
                onClick={() => onDecide('approve')} disabled={busy}
                className="bg-emerald-600 text-white font-medium text-sm rounded-xl py-2.5 active:scale-[0.98] transition disabled:opacity-50"
              >
                {busy ? 'Working...' : 'Approve'}
              </button>
              <button
                onClick={() => onDecide('deny')} disabled={busy}
                className="bg-slate-100 text-slate-700 font-medium text-sm rounded-xl py-2.5 active:scale-[0.98] transition disabled:opacity-50"
              >
                Deny
              </button>
            </div>
          </>
        )
      )}

      {selectable && candidates && (
        <p className="mt-2 text-xs text-slate-500">Tap Cancel to pick the right recording for this one.</p>
      )}

      {!isPending && (item.status === 'APPROVED' || item.status === 'EXPIRED') && (
        <Link
          to={`/admin/requests/${item.id}`}
          className="mt-3 block w-full text-center border border-slate-200 text-slate-700 text-sm font-medium rounded-xl py-2.5 hover:bg-slate-50"
        >
          Manage access
        </Link>
      )}

      {error && <p className="mt-2 text-xs text-red-600">{error}</p>}
    </li>
  )
}

function AccessChips({ item }: { item: QueueItem }) {
  const chips: { label: string; tone: string }[] = []
  if (item.status === 'APPROVED' || item.status === 'EXPIRED' || item.status === 'REVOKED') {
    if (item.watchPercent > 0) chips.push({ label: `Watched ${item.watchPercent}%`, tone: 'bg-emerald-50 text-emerald-700' })
    else if (item.openCount > 0) chips.push({ label: 'Opened, not played', tone: 'bg-slate-100 text-slate-600' })
    else chips.push({ label: 'Not opened yet', tone: 'bg-slate-100 text-slate-600' })
  }
  if (item.status === 'APPROVED' && item.accessExpiresAt) {
    const left = timeUntil(item.accessExpiresAt)
    if (left) chips.push({ label: `Expires in ${left}`, tone: 'bg-amber-50 text-amber-700' })
  }
  if (chips.length === 0) return null
  return (
    <div className="flex flex-wrap gap-1.5 mt-2">
      {chips.map((c) => (
        <span key={c.label} className={`text-[11px] font-medium px-2 py-0.5 rounded-full ${c.tone}`}>{c.label}</span>
      ))}
    </div>
  )
}

function CandidatePicker({
  candidates, busy, onPick, onDeny, onPreview,
}: {
  candidates: RecordingCandidate[]
  busy: boolean
  onPick: (fileId: string) => void
  onDeny: () => void
  onPreview: (fileId: string) => void
}) {
  return (
    <div className="mt-3">
      <p className="text-xs font-medium text-slate-600">More than one recording matches. Pick the right one:</p>
      <ul className="mt-1.5 space-y-1.5">
        {candidates.map((c) => (
          <li key={c.recordingFileId} className="flex items-center justify-between gap-2 rounded-lg border border-slate-200 px-2.5 py-2">
            <div className="min-w-0">
              <p className="text-xs font-medium text-slate-800 truncate">{c.meetingTopic}</p>
              <p className="text-[11px] text-slate-500">
                {new Date(c.startTime).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short', timeZone: INSTITUTE_TIMEZONE })}
              </p>
            </div>
            <div className="shrink-0 flex items-center gap-1.5">
              <button
                onClick={() => onPreview(c.recordingFileId)}
                className="rounded-lg border border-slate-300 text-slate-700 text-xs font-medium px-2.5 py-2 hover:bg-slate-50"
              >
                Watch
              </button>
              <button
                onClick={() => onPick(c.recordingFileId)} disabled={busy}
                className="rounded-lg bg-emerald-600 text-white text-xs font-medium px-2.5 py-2 hover:bg-emerald-700 disabled:opacity-50"
              >
                {busy ? '...' : 'Use this'}
              </button>
            </div>
          </li>
        ))}
      </ul>
      <button
        onClick={onDeny} disabled={busy}
        className="mt-2 w-full bg-slate-100 text-slate-600 font-medium text-sm rounded-xl py-2.5 disabled:opacity-50"
      >
        Deny instead
      </button>
    </div>
  )
}

function EmptyState({ tab, filtersActive }: { tab: RequestTab; filtersActive: boolean }) {
  const [title, hint] = filtersActive
    ? ['No requests match', 'Try another batch or date, or clear the search.']
    : tab === 'PENDING'
      ? ['Nothing pending right now', 'New requests from students show up here.']
      : tab === 'APPROVED'
        ? ['No active recording links', 'Approved recordings appear here until their access ends.']
        : ['No requests yet', 'Requests appear here once students start asking for recordings.']
  return (
    <div className="text-center py-12">
      <p className="text-sm font-medium text-slate-700">{title}</p>
      <p className="text-xs text-slate-400 mt-1">{hint}</p>
    </div>
  )
}

function Chip({ active, onClick, children }: { active: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      onClick={onClick} aria-pressed={active}
      className={`shrink-0 px-3 py-1.5 rounded-full text-xs font-medium transition ${active ? 'bg-slate-900 text-white' : 'bg-white border border-slate-200 text-slate-600 hover:border-slate-400'}`}
    >
      {children}
    </button>
  )
}

function SearchIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
      <circle cx="11" cy="11" r="7" /><path d="M21 21l-4.3-4.3" />
    </svg>
  )
}

function PlaySmallIcon() {
  return (
    <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <polygon points="6 4 20 12 6 20 6 4" />
    </svg>
  )
}

function CheckIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" aria-hidden="true">
      <path d="M5 12l5 5L20 7" />
    </svg>
  )
}

function omit<T>(record: Record<string, T>, keys: string[]): Record<string, T> {
  const next = { ...record }
  keys.forEach((k) => delete next[k])
  return next
}

function parseErrorBody(err: unknown): DecideErrorBody | null {
  if (!(err instanceof ApiError)) return null
  try {
    return JSON.parse(err.message) as DecideErrorBody
  } catch {
    return null
  }
}

function describeOutcome(reason: DecisionOutcome | undefined, message?: string | null): string {
  switch (reason) {
    case 'ALREADY_DECIDED':
      return 'This request was already approved or denied (maybe from another tab).'
    case 'NO_MATCH':
      return message ?? 'No matching Zoom recording found for this class date/time yet.'
    case 'MULTIPLE_CANDIDATES':
      return 'More than one recording matches this class time. Pick the right one.'
    case 'LOOKUP_NOT_ALLOWED':
      return message ?? 'That date is outside the allowed lookup window.'
    default:
      return message ?? 'Something went wrong. Try again.'
  }
}
