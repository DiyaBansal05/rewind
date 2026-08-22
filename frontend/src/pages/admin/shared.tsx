export { RefreshButton } from '../../components/RefreshButton'

/** Floating action button -- a mobile-native pattern, so it's hidden on desktop in favor of an inline button there. */
export function Fab({ onClick, label }: { onClick: () => void; label: string }) {
  return (
    <button
      onClick={onClick} aria-label={label}
      className="md:hidden fixed bottom-24 right-6 w-14 h-14 rounded-full bg-slate-900 text-white shadow-lg flex items-center justify-center text-2xl active:scale-95 transition z-20"
    >
      +
    </button>
  )
}
