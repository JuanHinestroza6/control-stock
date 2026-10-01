import { createContext } from 'react'
import type { Sesion } from '../lib/auth'
import type { UsuarioSesion } from '../tipos/auth'

export interface SesionContexto {
  sesion: Sesion | null
  usuario: UsuarioSesion | null
  /** Guarda la sesión (localStorage + estado). */
  iniciar: (sesion: Sesion) => void
  /** True tras pulsar Salir: el siguiente login no debe recordar la ruta anterior. */
  cierreVoluntario: boolean
  /** Borra la sesión y vuelve a /login. */
  cerrar: () => void
}

export const ContextoSesion = createContext<SesionContexto | null>(null)
