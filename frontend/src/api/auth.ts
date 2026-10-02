import type { CambiarPasswordRequest, LoginRequest, LoginResponse, UsuarioSesion } from '../tipos/auth'
import { cliente } from './cliente'

export async function login(datos: LoginRequest): Promise<LoginResponse> {
  const { data } = await cliente.post<LoginResponse>('/auth/login', datos)
  return data
}

export async function yo(): Promise<UsuarioSesion> {
  const { data } = await cliente.get<UsuarioSesion>('/auth/yo')
  return data
}

/** Cambia la contraseña propia; el backend revoca los tokens anteriores y devuelve una sesión nueva. */
export async function cambiarPassword(datos: CambiarPasswordRequest): Promise<LoginResponse> {
  const { data } = await cliente.put<LoginResponse>('/auth/yo/password', datos)
  return data
}
