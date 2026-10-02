import type { Rol } from './auth'
import type { Pagina, ParametrosPagina } from './paginacion'

/** Refleja `UsuarioResponse` del backend (nunca incluye el hash). */
export interface UsuarioResponse {
  id: number
  nombre: string
  email: string
  rol: Rol
  activo: boolean
  debeCambiarPassword: boolean
  creadoEn: string
  actualizadoEn: string
}

export interface CrearUsuarioRequest {
  nombre: string
  email: string
  rol: Rol
  passwordTemporal: string
}

export interface ActualizarUsuarioRequest {
  nombre: string
  email: string
  rol: Rol
}

export interface FiltrosUsuarios extends ParametrosPagina {
  q?: string
  rol?: Rol
  activo?: boolean
}

export type PaginaUsuarios = Pagina<UsuarioResponse>
