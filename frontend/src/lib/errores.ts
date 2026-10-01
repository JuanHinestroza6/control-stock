import { isAxiosError } from 'axios'
import type { ErrorCampo, ProblemDetail } from '../tipos/problema'

export const MENSAJE_POR_DEFECTO = 'Ocurrió un error inesperado. Intenta de nuevo.'
export const MENSAJE_SIN_CONEXION = 'No se pudo conectar con el servidor. Revisa tu conexión.'

function esProblema(datos: unknown): datos is ProblemDetail {
  return typeof datos === 'object' && datos !== null && !Array.isArray(datos)
}

function listaErrores(datos: ProblemDetail): ErrorCampo[] {
  if (!Array.isArray(datos.errores)) return []
  return datos.errores.filter(
    (e): e is ErrorCampo => typeof e === 'object' && e !== null && typeof e.campo === 'string' && typeof e.mensaje === 'string',
  )
}

/** Extrae un mensaje legible de cualquier error; prioriza los errores por campo, luego detail y title. */
export function mensajeError(error: unknown): string {
  if (isAxiosError(error)) {
    if (!error.response) return MENSAJE_SIN_CONEXION
    const datos: unknown = error.response.data
    if (esProblema(datos)) {
      const campos = listaErrores(datos)
        .map((e) => (e.campo ? `${e.campo}: ${e.mensaje}` : e.mensaje))
        .filter((m) => m.length > 0)
      if (campos.length > 0) return campos.join('. ')
      if (typeof datos.detail === 'string' && datos.detail) return datos.detail
      if (typeof datos.title === 'string' && datos.title) return datos.title
    }
    return MENSAJE_POR_DEFECTO
  }
  return MENSAJE_POR_DEFECTO
}

/** Errores por campo del ProblemDetail, para asignarlos a formularios (campo -> mensaje). */
export function erroresPorCampo(error: unknown): Record<string, string> {
  if (!isAxiosError(error)) return {}
  const datos: unknown = error.response?.data
  if (!esProblema(datos)) return {}
  return Object.fromEntries(listaErrores(datos).map((e) => [e.campo, e.mensaje]))
}
