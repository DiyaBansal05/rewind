import { useEffect, type ReactNode } from 'react'

/**
 * A panel that slides up from the bottom on phones (within thumb reach) and
 * shows as a centered dialog on wider screens. Closes on backdrop tap or
 * Escape.
 */
export function BottomSheet({
  title, onClose, children,
}: {
  title?: ReactNode
  onClose: () => void
  children: ReactNode
}) {
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = previousOverflow
    }
  }, [onClose])

  return (
    <div className="fixed inset-0 z-50 bg-slate-900/40 flex items-end md:items-center justify-center md:p-4" onClick={onClose}>
      <div
        role="dialog" aria-modal="true"
        className="sheet-in w-full md:max-w-md max-h-[90vh] overflow-y-auto bg-white rounded-t-3xl md:rounded-2xl shadow-xl p-5 pb-[calc(1.5rem+env(safe-area-inset-bottom))] md:pb-5"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="md:hidden w-10 h-1 rounded-full bg-slate-200 mx-auto mb-4" aria-hidden="true" />
        {title && <h2 className="text-lg font-semibold text-slate-900 mb-1">{title}</h2>}
        {children}
      </div>
    </div>
  )
}
