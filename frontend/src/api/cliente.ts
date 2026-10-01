import axios, { type AxiosError } from 'axios'
import { borrarSesion, leerSesion } from '../lib/auth'

export const EVENTO_SESION_EXPIRADA = 'control-stock:sesion-expirada'

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
  return Promise.reject(error)
}

cliente.interceptors.response.use((respuesta) => respuesta, manejarErrorRespuesta)

/** Suscribe un callback (p. ej. redirigir a /login) al evento de sesión expirada. Devuelve la función para cancelar. */
export function alExpirarSesion(callback: () => void): () => void {
  window.addEventListener(EVENTO_SESION_EXPIRADA, callback)
  return () => window.removeEventListener(EVENTO_SESION_EXPIRADA, callback)
}
