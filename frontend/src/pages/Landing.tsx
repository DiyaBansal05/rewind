import { Link } from 'react-router-dom'

export function Landing() {
  return (
    <main className="min-h-screen flex items-center justify-center bg-slate-50 px-4">
      <div className="w-full max-w-sm rounded-xl border border-slate-200 bg-white p-8 text-center shadow-sm">
        <h1 className="text-xl font-semibold text-slate-900">Recording Portal</h1>
        <p className="mt-2 text-sm text-slate-500">Students register via their batch's QR code. Pick your role below.</p>
        <div className="mt-6 flex flex-col gap-2">
          <Link to="/admin/login" className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800">
            Admin sign in
          </Link>
          <Link to="/student/login" className="rounded-md border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50">
            Student sign in
          </Link>
        </div>
      </div>
    </main>
  )
}
