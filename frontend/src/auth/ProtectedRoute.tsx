import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { getRole, type Role } from '../api/client'

export function ProtectedRoute({ role, children }: { role: Role; children: ReactNode }) {
  const currentRole = getRole()
  if (currentRole !== role) {
    return <Navigate to={role === 'ADMIN' ? '/admin/login' : '/student/login'} replace />
  }
  return <>{children}</>
}
