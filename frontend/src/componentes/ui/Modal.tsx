import { useEffect, useId, useRef, type KeyboardEvent, type ReactNode } from 'react'
import { createPortal } from 'react-dom'

interface ModalProps {
  abierto: boolean
  titulo: string
  onCerrar: () => void
  /** Si es true, Escape y el clic en el fondo no cierran (p. ej. mientras se guarda). */
  bloqueado?: boolean
  children: ReactNode
}

const FOCALIZABLES = 'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'

/** Diálogo modal accesible: role=dialog, Escape, clic en fondo, foco atrapado y restaurado. */
export function Modal({ abierto, titulo, onCerrar, bloqueado = false, children }: ModalProps) {
  const idTitulo = useId()
  const panel = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!abierto) return
    const previo = document.activeElement instanceof HTMLElement ? document.activeElement : null
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const primero = panel.current?.querySelector<HTMLElement>(FOCALIZABLES)
    ;(primero ?? panel.current)?.focus()
    return () => {
      document.body.style.overflow = overflow
      previo?.focus()
    }
  }, [abierto])

  if (!abierto) return null

  function alTeclear(e: KeyboardEvent<HTMLDivElement>) {
    if (e.key === 'Escape') {
      e.stopPropagation()
      if (!bloqueado) onCerrar()
      return
    }
    if (e.key !== 'Tab' || !panel.current) return
    const items = Array.from(panel.current.querySelectorAll<HTMLElement>(FOCALIZABLES))
    if (items.length === 0) {
      e.preventDefault()
      return
    }
    const primero = items[0]
    const ultimo = items[items.length - 1]
    if (e.shiftKey && (document.activeElement === primero || document.activeElement === panel.current)) {
      e.preventDefault()
      ultimo.focus()
    } else if (!e.shiftKey && document.activeElement === ultimo) {
      e.preventDefault()
      primero.focus()
    }
  }

  return createPortal(
    <div
      className="fixed inset-0 z-50 flex items-end justify-center bg-oxford/50 p-0 sm:items-center sm:p-4"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget && !bloqueado) onCerrar()
      }}
    >
      <div
        ref={panel}
        role="dialog"
        aria-modal="true"
        aria-labelledby={idTitulo}
        tabIndex={-1}
        onKeyDown={alTeclear}
        className="max-h-[90vh] w-full overflow-y-auto rounded-t-lg bg-fondo p-6 shadow-xl outline-none sm:max-w-lg sm:rounded-lg"
      >
        <h2 id={idTitulo} className="mb-4">
          {titulo}
        </h2>
        {children}
      </div>
    </div>,
    document.body,
  )
}
