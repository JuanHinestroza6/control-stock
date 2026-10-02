import type { ReactNode } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router'
import { useSesion } from '../../hooks/useSesion'
import { AccesoDenegado } from '../../paginas/comun/AccesoDenegado'
import type { Rol } from '../../tipos/auth'
import type { EstadoLogin } from '../../tipos/navegacion'

const RUTA_CUENTA = '/cuenta'

interface RutaProtegidaProps {
  /** Roles permitidos. Si se omite, basta con tener sesión. */
  roles?: Rol[]
  /** Contenido; si se omite se renderiza el <Outlet /> (uso como ruta de layout). */
  children?: ReactNode
}

/** Sin sesión redirige a /login conservando el destino; con un rol no permitido muestra acceso denegado. */
export function RutaProtegida({ roles, children }: RutaProtegidaProps) {
  const { usuario, cierreVoluntario } = useSesion()
  const ubicacion = useLocation()

  if (!usuario) {
    // Tras Salir (cierre voluntario) no se recuerda la ruta; con acceso directo o sesión expirada sí.
    const estado: EstadoLogin = { desde: cierreVoluntario ? undefined : ubicacion.pathname + ubicacion.search }
    return <Navigate to="/login" replace state={estado} />
  }
  // Contraseña temporal: el backend rechaza todo salvo /yo; aquí se lleva al usuario a /cuenta.
  if (usuario.debeCambiarPassword && ubicacion.pathname !== RUTA_CUENTA) return <Navigate to={RUTA_CUENTA} replace />
  if (roles && !roles.includes(usuario.rol)) return <AccesoDenegado />
  return children ?? <Outlet />
}
