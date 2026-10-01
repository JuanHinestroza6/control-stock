import type { LoginResponse, Rol, UsuarioSesion } from '../tipos/auth'

export interface Sesion {
  token: string
  /** Instante de expiración en ISO-8601. */
  expiraEn: string
  usuario: UsuarioSesion
}

export const CLAVE_SESION = 'control-stock.sesion'

export function sesionDesdeLogin(respuesta: LoginResponse): Sesion {
  return { token: respuesta.token, expiraEn: respuesta.expiraEn, usuario: respuesta.usuario }
}

export function guardarSesion(sesion: Sesion): void {
  try {
    localStorage.setItem(CLAVE_SESION, JSON.stringify(sesion))
  } catch {
    // Almacenamiento no disponible: la sesión vive solo en memoria de la página.
  }
}

export function borrarSesion(): void {
  try {
    localStorage.removeItem(CLAVE_SESION)
  } catch {
    // Nada que borrar si el almacenamiento no está disponible.
  }
}

export function sesionVencida(sesion: Sesion, ahora: Date = new Date()): boolean {
  const expira = Date.parse(sesion.expiraEn)
  return Number.isNaN(expira) || expira <= ahora.getTime()
}

function esSesion(valor: unknown): valor is Sesion {
  if (typeof valor !== 'object' || valor === null) return false
  const s = valor as Partial<Sesion>
  const u = s.usuario as Partial<UsuarioSesion> | undefined
  return (
    typeof s.token === 'string' &&
    typeof s.expiraEn === 'string' &&
    typeof u === 'object' &&
    u !== null &&
    typeof u.id === 'number' &&
    typeof u.nombre === 'string' &&
    typeof u.email === 'string' &&
    typeof u.rol === 'string'
  )
}

/** Lee la sesión guardada. Devuelve null si no existe, está corrupta o vencida (y en ese caso la borra). */
export function leerSesion(ahora: Date = new Date()): Sesion | null {
  let crudo: string | null
  try {
    crudo = localStorage.getItem(CLAVE_SESION)
  } catch {
    return null
  }
  if (crudo === null) return null
  let sesion: unknown
  try {
    sesion = JSON.parse(crudo)
  } catch {
    borrarSesion()
    return null
  }
  if (!esSesion(sesion) || sesionVencida(sesion, ahora)) {
    borrarSesion()
    return null
  }
  return sesion
}

export function tieneRol(sesion: Sesion | null | undefined, ...roles: Rol[]): boolean {
  return sesion != null && roles.includes(sesion.usuario.rol)
}
