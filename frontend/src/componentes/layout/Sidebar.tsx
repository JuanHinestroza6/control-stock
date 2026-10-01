import { useEffect } from 'react'
import { NavLink } from 'react-router'
import type { Rol } from '../../tipos/auth'
import { modulosPermitidos } from './modulos'

interface SidebarProps {
  rol: Rol
  /** Estado del drawer en móvil (en escritorio la barra siempre es visible). */
  abierto: boolean
  onCerrar: () => void
  id?: string
}

export function Sidebar({ rol, abierto, onCerrar, id = 'menu-principal' }: SidebarProps) {
  useEffect(() => {
    if (!abierto) return
    const alPulsar = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onCerrar()
    }
    window.addEventListener('keydown', alPulsar)
    return () => window.removeEventListener('keydown', alPulsar)
  }, [abierto, onCerrar])

  return (
    <>
      {abierto && (
        <div
          aria-hidden="true"
          data-testid="sidebar-fondo"
          onClick={onCerrar}
          className="fixed inset-0 z-30 bg-oxford/50 lg:hidden"
        />
      )}
      <aside
        id={id}
        aria-label="Navegación principal"
        className={`fixed inset-y-0 left-0 z-40 flex w-64 flex-col bg-oxford text-fondo transition-transform lg:static lg:visible lg:translate-x-0 ${abierto ? 'visible translate-x-0' : 'invisible -translate-x-full'}`}
      >
        <div className="flex h-16 items-center justify-between px-4">
          <span className="font-titulo text-lg font-bold">Control Stock</span>
          <button
            type="button"
            onClick={onCerrar}
            aria-label="Cerrar menú"
            className="rounded-lg p-2 hover:bg-fondo/10 focus-visible:outline-2 focus-visible:outline-acento lg:hidden"
          >
            <svg aria-hidden="true" viewBox="0 0 24 24" className="size-5" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M6 6l12 12M18 6L6 18" strokeLinecap="round" />
            </svg>
          </button>
        </div>
        <nav aria-label="Módulos" className="flex-1 overflow-y-auto px-2 py-2">
          <ul className="flex flex-col gap-1">
            {modulosPermitidos(rol).map((m) => (
              <li key={m.clave}>
                <NavLink
                  to={m.ruta}
                  end={m.ruta === '/'}
                  onClick={onCerrar}
                  className={({ isActive }) =>
                    `block rounded-lg px-3 py-2 text-sm font-medium transition-colors focus-visible:outline-2 focus-visible:outline-acento ${isActive ? 'bg-acento text-fondo' : 'text-fondo/80 hover:bg-fondo/10 hover:text-fondo'}`
                  }
                >
                  {m.etiqueta}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
        <p className="px-4 py-3 text-xs font-medium text-fondo/50">Livorno Clothing</p>
      </aside>
    </>
  )
}
