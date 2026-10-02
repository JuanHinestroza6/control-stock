import type {
  ActualizarUsuarioRequest,
  CrearUsuarioRequest,
  FiltrosUsuarios,
  PaginaUsuarios,
  UsuarioResponse,
} from '../tipos/usuarios'
import { cliente } from './cliente'

/** Construye los query params del listado; omite los filtros vacíos o indefinidos. */
export function parametrosUsuarios(filtros: FiltrosUsuarios): Record<string, string | number | boolean> {
  const params: Record<string, string | number | boolean> = {}
  const q = filtros.q?.trim()
  if (q) params.q = q
  if (filtros.rol) params.rol = filtros.rol
  if (filtros.activo !== undefined) params.activo = filtros.activo
  if (filtros.page !== undefined) params.page = filtros.page
  if (filtros.size !== undefined) params.size = filtros.size
  if (filtros.sort) params.sort = filtros.sort
  return params
}

export async function listarUsuarios(filtros: FiltrosUsuarios): Promise<PaginaUsuarios> {
  const { data } = await cliente.get<PaginaUsuarios>('/usuarios', { params: parametrosUsuarios(filtros) })
  return data
}

export async function crearUsuario(datos: CrearUsuarioRequest): Promise<UsuarioResponse> {
  const { data } = await cliente.post<UsuarioResponse>('/usuarios', datos)
  return data
}

export async function actualizarUsuario(id: number, datos: ActualizarUsuarioRequest): Promise<UsuarioResponse> {
  const { data } = await cliente.put<UsuarioResponse>(`/usuarios/${id}`, datos)
  return data
}

export async function cambiarEstadoUsuario(id: number, activo: boolean): Promise<UsuarioResponse> {
  const { data } = await cliente.patch<UsuarioResponse>(`/usuarios/${id}/estado`, { activo })
  return data
}

/** Fija una contraseña temporal (el usuario deberá cambiarla y sus tokens quedan revocados). */
export async function restablecerPassword(id: number, passwordTemporal: string): Promise<void> {
  await cliente.put(`/usuarios/${id}/password`, { passwordTemporal })
}
