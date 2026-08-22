import type { ReactNode } from 'react'
import { INSTITUTE_NAME } from '../constants'

/**
 * Consistent header used across every entry page (landing, both logins,
 * registration): institute name as a small "eyebrow" label for brand
 * context, with the page's own heading staying the visually dominant part.
 */
export function BrandHeader({ title, subtitle }: { title: string; subtitle?: ReactNode }) {
  return (
    <div className="mb-6">
      <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">{INSTITUTE_NAME}</p>
      <h1 className="mt-1 text-xl font-semibold text-slate-900">{title}</h1>
      {subtitle && <p className="mt-1 text-sm text-slate-500">{subtitle}</p>}
    </div>
  )
}
