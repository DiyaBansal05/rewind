import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { apiFetch, setAuth } from '../api/client'

interface BatchInfo {
  batchName: string
  courseName: string
}

export function Register() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') ?? ''
  const navigate = useNavigate()

  const [batchInfo, setBatchInfo] = useState<BatchInfo | null>(null)
  const [linkError, setLinkError] = useState<string | null>(null)
  const [name, setName] = useState('')
  const [phoneNumber, setPhoneNumber] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)

  useEffect(() => {
    if (!token) {
      setLinkError('Missing registration link. Scan your batch QR code again.')
      return
    }
    apiFetch<BatchInfo>(`/api/register/batch-info?token=${encodeURIComponent(token)}`)
      .then(setBatchInfo)
      .catch(() => setLinkError('This registration link is invalid or expired.'))
  }, [token])

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setSubmitError(null)
    setSubmitting(true)
    try {
      const res = await apiFetch<{ token: string; alreadyEnrolled: boolean }>('/api/register', {
        method: 'POST',
        body: JSON.stringify({ token, name, phoneNumber }),
      })
      setAuth(res.token, 'STUDENT')
      navigate('/student')
    } catch {
      setSubmitError('Registration failed. Check your details and try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="min-h-screen flex items-center justify-center bg-slate-50 px-4">
      <div className="w-full max-w-sm rounded-xl border border-slate-200 bg-white p-8 shadow-sm">
        <h1 className="text-xl font-semibold text-slate-900">Batch registration</h1>

        {linkError && <p className="mt-4 text-sm text-red-600">{linkError}</p>}

        {batchInfo && (
          <>
            <p className="mt-1 text-sm text-slate-500">
              Registering for <span className="font-medium text-slate-700">{batchInfo.batchName}</span> &middot; {batchInfo.courseName}
            </p>

            <form onSubmit={handleSubmit}>
              <label className="mt-6 block text-sm font-medium text-slate-700">Your name</label>
              <input
                type="text" required value={name} onChange={(e) => setName(e.target.value)}
                className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-slate-900"
              />

              <label className="mt-4 block text-sm font-medium text-slate-700">Phone number</label>
              <input
                type="tel" required value={phoneNumber} onChange={(e) => setPhoneNumber(e.target.value)}
                placeholder="+91XXXXXXXXXX"
                className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-slate-900"
              />

              {submitError && <p className="mt-4 text-sm text-red-600">{submitError}</p>}

              <button
                type="submit" disabled={submitting}
                className="mt-6 w-full rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-50"
              >
                {submitting ? 'Registering...' : 'Register'}
              </button>
            </form>
          </>
        )}
      </div>
    </main>
  )
}
