import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { apiFetch, setAuth, ApiError } from '../api/client'
import { BrandHeader } from '../components/BrandHeader'

export function StudentLogin() {
  const [phoneNumber, setPhoneNumber] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      const { token } = await apiFetch<{ token: string }>('/api/auth/student/login', {
        method: 'POST',
        body: JSON.stringify({ phoneNumber }),
      })
      setAuth(token, 'STUDENT')
      navigate('/student')
    } catch (err) {
      setError(
        err instanceof ApiError && err.status === 404
          ? "We don't recognize that number. Register via your batch's QR code first."
          : 'Something went wrong',
      )
    } finally {
      setLoading(false)
    }
  }

  return (
    <main className="min-h-screen flex items-center justify-center bg-slate-50 px-4">
      <form onSubmit={handleSubmit} className="w-full max-w-sm rounded-xl border border-slate-200 bg-white p-8 shadow-sm">
        <BrandHeader title="Student sign in" subtitle="Enter the phone number you registered with." />

        <label className="mt-6 block text-sm font-medium text-slate-700">Phone number</label>
        <input
          type="tel" required value={phoneNumber} onChange={(e) => setPhoneNumber(e.target.value)}
          placeholder="+91XXXXXXXXXX"
          className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-slate-900"
        />

        {error && <p className="mt-4 text-sm text-red-600">{error}</p>}

        <button
          type="submit" disabled={loading}
          className="mt-6 w-full rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-50"
        >
          {loading ? 'Signing in...' : 'Sign in'}
        </button>

        <p className="mt-4 text-center text-xs text-slate-400">
          New here? <Link to="/" className="underline">Ask your instructor for your batch's QR code.</Link>
        </p>
      </form>
    </main>
  )
}
