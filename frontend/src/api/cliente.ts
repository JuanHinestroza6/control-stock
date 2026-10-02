import axios, { type AxiosError } from 'axios'
import { type Sesion, borrarSesion, leerSesion, marcarCambioPasswordRequerido } from '../lib/auth'
import type { ProblemDetail } from '../tipos/problema'

export const EVENTO_SESION_EXPIRADA = 'control-stock:sesion-expirada'
export const EVENTO_CAMBIO_PASSWORD = 'control-stock:cambio-password-requerido'

export const cliente = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? '/api',
})

cliente.interceptors.request.use((config) => {
  const sesion = leerSesion()
  if (sesion) config.headers.set('Authorization', `Bearer ${sesion.token}`)
  return config
})

/** Rutas donde un 401 es una respuesta esperada (credenciales incorrectas) y no una sesión vencida. */
const RUTAS_PUBLICAS = ['/auth/login']

export function manejarErrorRespuesta(error: AxiosError): Promise<never> {
  if (error.response?.status === 401 && !RUTAS_PUBLICAS.includes(error.config?.url ?? '')) {
    borrarSesion()
    window.dispatchEvent(new Event(EVENTO_SESION_EXPIRADA))
  }
  if (error.response?.status === 403 && (error.response.data as ProblemDetail | undefined)?.codigo === 'CAMBIO_PASSWORD_REQUERIDO') {
    const sesion = marcarCambioPasswordRequerido()
    if (sesion) window.dispatchEvent(new CustomEvent(EVENTO_CAMBIO_PASSWORD, { detail: sesion }))
  }
  return Promise.reject(error)
}

cliente.interceptors.response.use((respuesta) => respuesta, manejarErrorRespuesta)

/** Suscribe un callback al 403 CAMBIO_PASSWORD_REQUERIDO; recibe la sesión ya marcada. */
export function alRequerirCambioPassword(callback: (sesion: Sesion) => void): () => void {
  const manejador = (e: Event) => callback((e as CustomEvent<Sesion>).detail)
  window.addEventListener(EVENTO_CAMBIO_PASSWORD, manejador)
  return () => window.removeEventListener(EVENTO_CAMBIO_PASSWORD, manejador)
}

/** Suscribe un callback (p. ej. redirigir a /login) al evento de sesión expirada. Devuelve la función para cancelar. */
export function alExpirarSesion(callback: () => void): () => void {
  window.addEventListener(EVENTO_SESION_EXPIRADA, callback)
  return () => window.removeEventListener(EVENTO_SESION_EXPIRADA, callback)
}
