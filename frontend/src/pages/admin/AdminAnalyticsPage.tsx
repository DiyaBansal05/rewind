import { useState } from 'react'
import { apiFetch } from '../../api/client'
import type { AnalyticsReport } from '../../api/types'
import { useAutoRefresh } from '../../hooks/useAutoRefresh'
import { formatClassDate, formatMinutes } from '../../lib/format'
import { RefreshButton } from './shared'

const PERIODS = [7, 30, 90]
const WEEKDAY_LABELS: Record<string, string> = {
  MONDAY: 'Mon', TUESDAY: 'Tue', WEDNESDAY: 'Wed', THURSDAY: 'Thu', FRIDAY: 'Fri', SATURDAY: 'Sat', SUNDAY: 'Sun',
}

export function AdminAnalyticsPage() {
  const [days, setDays] = useState(30)
  const [report, setReport] = useState<AnalyticsReport | null>(null)
  const [failed, setFailed] = useState(false)

  function refresh() {
    apiFetch<AnalyticsReport>(`/api/admin/analytics?days=${days}`)
      .then((r) => { setReport(r); setFailed(false) })
      .catch(() => setFailed(true))
  }

  useAutoRefresh(refresh, [days])

  return (
    <div>
      <div className="flex items-center justify-between mb-3">
        <div>
          <h2 className="text-base font-semibold text-slate-900">Analytics</h2>
          <p className="text-xs text-slate-400">How recordings are being requested and used</p>
        </div>
        <RefreshButton onClick={refresh} />
      </div>

      <div className="flex gap-2" role="group" aria-label="Time period">
        {PERIODS.map((p) => (
          <button
            key={p} onClick={() => setDays(p)} aria-pressed={days === p}
            className={`px-3.5 py-1.5 rounded-full text-xs font-medium ${days === p ? 'bg-slate-900 text-white' : 'bg-white border border-slate-200 text-slate-600 hover:border-slate-400'}`}
          >
            {p} days
          </button>
        ))}
      </div>

      {failed && !report && <p className="mt-6 text-sm text-red-600">Couldn't load analytics. Try refreshing.</p>}
      {!report ? (
        !failed && <p className="mt-6 text-sm text-slate-400">Loading...</p>
      ) : (
        <div className={`mt-3 space-y-3 transition-opacity ${report.days !== days ? 'opacity-60' : ''}`}>
          <Kpis report={report} />
          <div className="grid grid-cols-1 lg:grid-cols-5 gap-3">
            <div className="lg:col-span-3 space-y-3">
              <PerDayChart report={report} />
              <MissedWeekdays report={report} />
            </div>
            <div className="lg:col-span-2 space-y-3">
              <PerBatch report={report} />
              <TopRequesters report={report} />
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function Kpis({ report }: { report: AnalyticsReport }) {
  const prev = report.previousPeriodRequests
  const change = prev === 0
    ? (report.totalRequests > 0 ? 'none in the previous period' : 'same as previous period')
    : `${report.totalRequests >= prev ? '+' : ''}${Math.round(((report.totalRequests - prev) / prev) * 100)}% vs previous ${report.days} days`
  return (
    <div className="grid grid-cols-2 md:grid-cols-4 gap-2.5">
      <Kpi label="Requests" value={String(report.totalRequests)} sub={change} />
      <Kpi
        label="Approval rate"
        value={report.approvalRatePercent === null ? '—' : `${report.approvalRatePercent}%`}
        sub={report.pendingNow > 0 ? `${report.pendingNow} still waiting` : 'of decided requests'}
      />
      <Kpi
        label="Time to decide"
        value={report.medianDecisionMinutes === null ? '—' : formatMinutes(report.medianDecisionMinutes)}
        sub="median, request to decision"
      />
      <Kpi
        label="Actually watched"
        value={report.watchedRatePercent === null ? '—' : `${report.watchedRatePercent}%`}
        sub={report.grantedCount === 0
          ? 'no approved recordings yet'
          : `${report.watchedCount} of ${report.grantedCount} approved${report.averageWatchPercent !== null ? ` · avg ${report.averageWatchPercent}% seen` : ''}`}
        tone="good"
      />
    </div>
  )
}

function Kpi({ label, value, sub, tone }: { label: string; value: string; sub: string; tone?: 'good' }) {
  return (
    <div className="bg-white rounded-xl border border-slate-200 p-3">
      <p className="text-[11px] text-slate-500">{label}</p>
      <p className="text-xl font-bold text-slate-900 tabular-nums mt-0.5">{value}</p>
      <p className={`text-[11px] mt-0.5 ${tone === 'good' ? 'text-emerald-700' : 'text-slate-500'}`}>{sub}</p>
    </div>
  )
}

function PerDayChart({ report }: { report: AnalyticsReport }) {
  // 90 single-day bars get too thin on a phone, so long periods are grouped by week.
  const buckets = report.days > 31 ? groupByWeek(report.perDay) : report.perDay.map((d) => ({ label: d.date, count: d.count }))
  const max = Math.max(4, ...buckets.map((b) => b.count))
  const niceMax = Math.ceil(max / 4) * 4
  const peak = buckets.reduce((best, b) => (b.count > best.count ? b : best), buckets[0])
  const width = 320
  const height = 110
  const axis = 16 // left gutter for the y-axis numbers
  const barWidth = (width - axis) / buckets.length
  const labelEvery = Math.ceil(buckets.length / 4)

  return (
    <section className="bg-white rounded-xl border border-slate-200 p-3.5">
      <div className="flex items-baseline justify-between">
        <h3 className="text-sm font-semibold text-slate-900">Requests per {report.days > 31 ? 'week' : 'day'}</h3>
        <span className="text-[11px] text-slate-400">last {report.days} days</span>
      </div>
      <svg viewBox={`0 0 ${width} ${height + 18}`} className="mt-2 w-full" role="img"
        aria-label={`Requests per ${report.days > 31 ? 'week' : 'day'}, peak ${peak?.count ?? 0}`}>
        {[0, niceMax / 2, niceMax].map((g) => {
          const y = height - (g / niceMax) * height
          return (
            <g key={g}>
              <line x1={axis} x2={width} y1={y} y2={y} stroke="#e2e8f0" strokeWidth="1" />
              <text x={0} y={Math.max(8, y + 3)} fontSize="9" fill="#94a3b8">{g}</text>
            </g>
          )
        })}
        {buckets.map((b, i) => {
          const h = (b.count / niceMax) * height
          const isLast = i === buckets.length - 1
          const isPeak = b === peak && b.count > 0
          const x = axis + i * barWidth
          const showLabel = (i % labelEvery === 0 && i < buckets.length - labelEvery / 2) || isLast
          return (
            <g key={b.label}>
              <rect
                x={x + barWidth * 0.15} y={height - h} width={barWidth * 0.7} height={Math.max(h, b.count > 0 ? 2 : 0)} rx="2"
                fill={isLast ? '#059669' : isPeak ? '#0f172a' : '#cbd5e1'}
              >
                <title>{`${formatClassDate(b.label, false)}: ${b.count}`}</title>
              </rect>
              {showLabel && (
                <text
                  x={i === 0 ? x : isLast ? width : x + barWidth / 2} y={height + 13}
                  textAnchor={i === 0 ? 'start' : isLast ? 'end' : 'middle'} fontSize="9" fill="#94a3b8"
                >
                  {formatClassDate(b.label, false)}
                </text>
              )}
            </g>
          )
        })}
      </svg>
      {peak && peak.count > 0 ? (
        <p className="text-[11px] text-slate-500 mt-1">
          Busiest: <span className="font-semibold text-slate-800">{peak.count} on {report.days > 31 ? 'week of ' : ''}{formatClassDate(peak.label, false)}</span>
        </p>
      ) : (
        <p className="text-[11px] text-slate-500 mt-1">No requests in this period.</p>
      )}
    </section>
  )
}

function PerBatch({ report }: { report: AnalyticsReport }) {
  const max = Math.max(1, ...report.perBatch.map((b) => b.count))
  return (
    <section className="bg-white rounded-xl border border-slate-200 p-3.5">
      <h3 className="text-sm font-semibold text-slate-900">By batch</h3>
      {report.perBatch.length === 0 ? (
        <p className="mt-2 text-xs text-slate-400">No requests in this period.</p>
      ) : (
        <ul className="mt-2.5 space-y-2.5">
          {report.perBatch.map((b) => (
            <li key={b.batchId}>
              <div className="flex justify-between gap-2 text-xs">
                <span className="text-slate-700 truncate">{b.batchName}</span>
                <span className="tabular-nums font-semibold">{b.count}</span>
              </div>
              <div className="mt-1 h-2 rounded-full bg-slate-100">
                <div className="h-2 rounded-full bg-slate-800" style={{ width: `${(b.count / max) * 100}%` }} />
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function TopRequesters({ report }: { report: AnalyticsReport }) {
  return (
    <section className="bg-white rounded-xl border border-slate-200 p-3.5">
      <h3 className="text-sm font-semibold text-slate-900">Most frequent requesters</h3>
      {report.topRequesters.length === 0 ? (
        <p className="mt-2 text-xs text-slate-400">No requests in this period.</p>
      ) : (
        <ol className="mt-1 divide-y divide-slate-100">
          {report.topRequesters.map((r, i) => (
            <li key={r.studentId} className="flex items-center gap-3 py-2">
              <span className="w-6 h-6 rounded-full bg-slate-100 text-[11px] font-semibold grid place-items-center shrink-0">{i + 1}</span>
              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium text-slate-900 truncate">{r.name}</p>
                <p className="text-[11px] text-slate-500 truncate">{r.batchNames.join(', ')}</p>
              </div>
              <span className="text-xs font-semibold tabular-nums shrink-0">{r.count} req</span>
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}

function MissedWeekdays({ report }: { report: AnalyticsReport }) {
  const max = Math.max(1, ...report.missedWeekdays.map((d) => d.count))
  return (
    <section className="bg-white rounded-xl border border-slate-200 p-3.5">
      <h3 className="text-sm font-semibold text-slate-900">Which class days get missed</h3>
      <p className="text-[11px] text-slate-500">Requests by the weekday of the missed class</p>
      <div className="mt-2.5 grid grid-cols-7 gap-1.5">
        {report.missedWeekdays.map((d) => {
          const level = d.count / max
          const tone = d.count === 0 ? 'bg-slate-50 text-slate-400'
            : level > 0.66 ? 'bg-emerald-700 text-white'
              : level > 0.33 ? 'bg-emerald-300 text-emerald-950'
                : 'bg-emerald-100 text-emerald-800'
          return (
            <div key={d.day} className="text-center">
              <div className={`h-10 rounded-lg grid place-items-center text-xs font-semibold tabular-nums ${tone}`}>{d.count}</div>
              <p className="text-[10px] text-slate-500 mt-1">{WEEKDAY_LABELS[d.day]}</p>
            </div>
          )
        })}
      </div>
    </section>
  )
}

function groupByWeek(days: { date: string; count: number }[]) {
  const weeks: { label: string; count: number }[] = []
  for (let i = 0; i < days.length; i += 7) {
    const slice = days.slice(i, i + 7)
    weeks.push({ label: slice[0].date, count: slice.reduce((sum, d) => sum + d.count, 0) })
  }
  return weeks
}
