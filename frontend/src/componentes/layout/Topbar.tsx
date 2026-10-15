import { Link } from 'react-router'
import { useResumenAlertas } from '../../hooks/useAlertas'
import { useSesion } from '../../hooks/useSesion'
import { etiquetaRol } from '../../lib/roles'
import { Boton } from '../ui/Boton'
import { puedeVerAlertas } from './modulos'

interface TopbarProps {
  onAbrirMenu: () => void
  menuAbierto: boolean
  idMenu: string
}

export function Topbar({ onAbrirMenu, menuAbierto, idMenu }: TopbarProps) {
  const { usuario, cerrar } = useSesion()
  const verAlertas = usuario !== null && usuario !== undefined && puedeVerAlertas(usuario.rol)
  // Solo se consulta si el rol puede ver alertas (CONTABILIDAD recibiría 403).
  const resumen = useResumenAlertas(verAlertas)
  const alertas = resumen.data?.activas ?? 0

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
        {verAlertas && (
          <Link
            to="/alertas"
            aria-label={`Alertas: ${alertas} sin atender`}
            title={`Agotado: ${resumen.data?.agotado ?? 0} · Stock bajo: ${resumen.data?.stockBajo ?? 0} · Sin rotación: ${resumen.data?.sinRotacion ?? 0}`}
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
          </Link>
        )}

        {usuario && (
          <div className="hidden text-right leading-tight sm:block">
            <p className="text-sm font-medium">{usuario.nombre}</p>
            <p className="text-xs font-medium text-oxford/60">{etiquetaRol(usuario.rol)}</p>
          </div>
        )}
        {usuario && (
          <Link to="/cuenta" className="text-sm font-medium text-acento hover:underline focus-visible:outline-2 focus-visible:outline-acento">
            Mi cuenta
          </Link>
        )}
        <Boton variante="primario" onClick={cerrar}>
          Salir
        </Boton>
      </div>
    </header>
  )
}
