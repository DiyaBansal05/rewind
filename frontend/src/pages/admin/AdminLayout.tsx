import { useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { apiFetch, clearAuth } from '../../api/client'
import type { EnrollmentRequestItem, QueueItem } from '../../api/types'
import { useAutoRefresh } from '../../hooks/useAutoRefresh'
import { INSTITUTE_NAME, ADMIN_DISPLAY_NAME } from '../../constants'

const NAV_ITEMS = [
  { to: '/admin/requests', label: 'Requests', icon: RequestsIcon },
  { to: '/admin/batches', label: 'Batches', icon: BatchesIcon },
  { to: '/admin/students', label: 'Students', icon: StudentsIcon },
]

export function AdminLayout() {
  const navigate = useNavigate()
  const [pendingCount, setPendingCount] = useState(0)
  const [pendingJoinCount, setPendingJoinCount] = useState(0)

  const badgeCounts: Record<string, number> = {
    '/admin/requests': pendingCount,
    '/admin/batches': pendingJoinCount,
  }

  useAutoRefresh(() => {
    apiFetch<QueueItem[]>('/api/admin/recording-requests').then((q) => setPendingCount(q.length)).catch(() => {})
    apiFetch<EnrollmentRequestItem[]>('/api/admin/enrollment-requests').then((q) => setPendingJoinCount(q.length)).catch(() => {})
  })

  function logout() {
    clearAuth()
    navigate('/admin/login')
  }

  return (
    <div className="min-h-screen bg-slate-50">
      {/* Header -- full-bleed background, content constrained inside it */}
      <div className="sticky top-0 z-20 bg-white border-b border-slate-200">
        <div className="mx-auto max-w-6xl px-4 md:px-8 py-3 flex items-center justify-between">
          <div>
            <h1 className="text-lg font-bold text-slate-900">{INSTITUTE_NAME}</h1>
            <p className="text-xs text-slate-400">Welcome, {ADMIN_DISPLAY_NAME}</p>
          </div>
          <button onClick={logout} className="text-sm text-slate-500 hover:text-slate-800">Sign out</button>
        </div>
      </div>

      {/* Desktop top tabs */}
      <div className="hidden md:block mx-auto max-w-6xl px-4 md:px-8 pt-4">
        <div className="bg-slate-200/60 rounded-xl p-1 grid grid-cols-3 text-sm font-medium max-w-lg">
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to} to={item.to}
              className={({ isActive }) =>
                `rounded-lg py-2 text-center transition ${isActive ? 'bg-white shadow-sm text-slate-900' : 'text-slate-500 hover:text-slate-700'}`
              }
            >
              {item.label}
              {badgeCounts[item.to] > 0 && (
                <span className="ml-1.5 inline-flex items-center justify-center rounded-full bg-red-500 text-white text-[10px] w-4 h-4">
                  {badgeCounts[item.to]}
                </span>
              )}
            </NavLink>
          ))}
        </div>
      </div>

      {/* Page content */}
      <div className="mx-auto max-w-6xl px-4 md:px-8 py-4 pb-24 md:pb-8">
        <Outlet />
      </div>

      {/* Mobile bottom tab bar */}
      <div className="md:hidden fixed bottom-0 left-0 right-0 bg-white border-t border-slate-200 z-20">
        <div className="mx-auto max-w-6xl grid grid-cols-3">
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to} to={item.to}
              className={({ isActive }) => `flex flex-col items-center gap-1 py-3 ${isActive ? 'text-slate-900' : 'text-slate-400'}`}
            >
              <div className="relative">
                <item.icon />
                {badgeCounts[item.to] > 0 && (
                  <span className="absolute -top-1 -right-2 bg-red-500 text-white text-[9px] rounded-full w-4 h-4 flex items-center justify-center">
                    {badgeCounts[item.to]}
                  </span>
                )}
              </div>
              <span className="text-[11px] font-medium">{item.label}</span>
            </NavLink>
          ))}
        </div>
      </div>
    </div>
  )
}

function RequestsIcon() {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <path d="M9 11l3 3L22 4M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11" />
    </svg>
  )
}
function BatchesIcon() {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <rect x="3" y="4" width="18" height="18" rx="2" /><path d="M16 2v4M8 2v4M3 10h18" />
    </svg>
  )
}
function StudentsIcon() {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" />
      <path d="M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" />
    </svg>
  )
}
