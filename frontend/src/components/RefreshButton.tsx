export function RefreshButton({ onClick }: { onClick: () => void }) {
  return (
    <button onClick={onClick} className="w-8 h-8 rounded-full bg-slate-100 hover:bg-slate-200 flex items-center justify-center shrink-0" aria-label="Refresh">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <path d="M21 12a9 9 0 1 1-3-6.7M21 4v5h-5" />
      </svg>
    </button>
  )
}
