import { Link } from 'react-router-dom'
import { BrandHeader } from '../components/BrandHeader'

export function Landing() {
  return (
    <main className="min-h-screen flex items-center justify-center bg-slate-50 px-4">
      <div className="w-full max-w-sm rounded-xl border border-slate-200 bg-white p-8 text-center shadow-sm">
        <BrandHeader
          title="Recording Portal"
          subtitle="Missed a class? Get the recording without the back-and-forth calls."
        />
        <div className="flex flex-col gap-2">
          <Link to="/admin/login" className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800">
            Admin sign in
          </Link>
          <Link to="/student/login" className="rounded-md border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50">
            Student sign in
          </Link>
        </div>
        <p className="mt-4 text-xs text-slate-400">
          New student? Ask your instructor to scan you in with your batch's QR code.
        </p>
      </div>
    </main>
  )
}
