export type Role = 'ADMIN' | 'STUDENT'

/**
 * Empty by default, which keeps every request path relative -- exactly what
 * local dev needs, since Vite's dev server proxies /api to the backend
 * itself (see vite.config.ts). That proxy doesn't exist once the frontend
 * is a static build served from somewhere like Vercel, so production sets
 * VITE_API_BASE_URL (baked in at build time) to the real backend origin.
 */
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

const TOKEN_KEY = 'rp_token'
const ROLE_KEY = 'rp_role'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function getRole(): Role | null {
  return localStorage.getItem(ROLE_KEY) as Role | null
}

export function setAuth(token: string, role: Role) {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(ROLE_KEY, role)
}

export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(ROLE_KEY)
}

export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

export async function apiFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = getToken()
  const headers = new Headers(options.headers)
  headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)

  const res = await fetch(`${API_BASE_URL}${path}`, { ...options, headers })
  if (!res.ok) {
    const text = await res.text().catch(() => '')
    throw new ApiError(res.status, text || `Request failed with ${res.status}`)
  }
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}
