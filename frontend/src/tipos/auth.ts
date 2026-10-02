export type Rol = 'ADMIN' | 'INVENTARIO' | 'VENDEDOR' | 'CONTABILIDAD'

export interface LoginRequest {
  email: string
  password: string
}

export interface UsuarioSesion {
  id: number
  nombre: string
  email: string
  rol: Rol
  /** Contraseña temporal: solo se permite /cuenta hasta cambiarla. */
  debeCambiarPassword: boolean
}

export interface CambiarPasswordRequest {
  actual: string
  nueva: string
}

export interface LoginResponse {
  token: string
  tipo: 'Bearer'
  /** Instante de expiración en ISO-8601. */
  expiraEn: string
  usuario: UsuarioSesion
}
