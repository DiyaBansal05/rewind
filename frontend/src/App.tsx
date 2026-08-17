import { useEffect, useState } from 'react'

type HealthResponse = {
  status: string
  timestamp: string
}

function App() {
  const [health, setHealth] = useState<HealthResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetch('/api/health')
      .then((res) => {
        if (!res.ok) throw new Error(`Request failed: ${res.status}`)
        return res.json() as Promise<HealthResponse>
      })
      .then(setHealth)
      .catch((err: Error) => setError(err.message))
  }, [])

  return (
    <main className="min-h-screen flex items-center justify-center bg-slate-50">
      <div className="rounded-lg border border-slate-200 bg-white p-8 shadow-sm">
        <h1 className="text-xl font-semibold text-slate-900">Recording Portal</h1>
        <p className="mt-2 text-sm text-slate-600">Backend health check</p>
        {error && <p className="mt-4 text-sm text-red-600">Error: {error}</p>}
        {health && (
          <div className="mt-4 text-sm text-slate-700">
            <p>status: <span className="font-mono text-emerald-600">{health.status}</span></p>
            <p>timestamp: <span className="font-mono">{health.timestamp}</span></p>
          </div>
        )}
        {!health && !error && <p className="mt-4 text-sm text-slate-400">Loading...</p>}
      </div>
    </main>
  )
}

export default App
