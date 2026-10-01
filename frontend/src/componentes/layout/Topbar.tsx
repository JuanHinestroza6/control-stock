import { useSesion } from '../../hooks/useSesion'
import { etiquetaRol } from '../../lib/roles'
import { Boton } from '../ui/Boton'
import { puedeVerAlertas } from './modulos'

interface TopbarProps {
  onAbrirMenu: () => void
  menuAbierto: boolean
  idMenu: string
  /** Alertas activas; el módulo de alertas aún no existe, por eso 0 por defecto. */
  alertas?: number
}

export function Topbar({ onAbrirMenu, menuAbierto, idMenu, alertas = 0 }: TopbarProps) {
  const { usuario, cerrar } = useSesion()

  return (
    <header className="flex h-16 shrink-0 items-center gap-3 border-b border-oxford/10 bg-fondo px-4">
      <button
        type="button"
        onClick={onAbrirMenu}
        aria-label="Abrir menú"
        aria-expanded={menuAbierto}
        aria-controls={idMenu}
        className="rounded-lg p-2 hover:bg-fondo-suave focus-visible:outline-2 focus-visible:outline-acento lg:hidden"
      >
        <svg aria-hidden="true" viewBox="0 0 24 24" className="size-6" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M4 6h16M4 12h16M4 18h16" strokeLinecap="round" />
        </svg>
      </button>

      <input
        type="search"
        disabled
        aria-label="Buscar"
        placeholder="Buscar (próximamente)"
        className="hidden min-h-10 w-full max-w-md rounded-lg border border-oxford/20 bg-fondo-suave px-3 text-sm sm:block"
      />

      <div className="ml-auto flex items-center gap-3">
        {usuario && puedeVerAlertas(usuario.rol) && (
          <button
            type="button"
            aria-label={`Alertas: ${alertas} sin atender`}
            className="relative rounded-lg p-2 hover:bg-fondo-suave focus-visible:outline-2 focus-visible:outline-acento"
          >
            <svg aria-hidden="true" viewBox="0 0 24 24" className="size-6" fill="none" stroke="currentColor" strokeWidth="2">
              <path
                d="M6 9a6 6 0 1112 0c0 5 2 6 2 6H4s2-1 2-6zM10 19a2 2 0 004 0"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
            <span
              aria-hidden="true"
              className={`absolute -right-0.5 -top-0.5 min-w-5 rounded-full px-1 text-center text-xs font-medium text-fondo ${alertas > 0 ? 'bg-critico' : 'bg-oxford/40'}`}
            >
              {alertas}
            </span>
          </button>
        )}

        {usuario && (
          <div className="hidden text-right leading-tight sm:block">
            <p className="text-sm font-medium">{usuario.nombre}</p>
            <p className="text-xs font-medium text-oxford/60">{etiquetaRol(usuario.rol)}</p>
          </div>
        )}
        <Boton variante="primario" onClick={cerrar}>
          Salir
        </Boton>
      </div>
    </header>
  )
}
