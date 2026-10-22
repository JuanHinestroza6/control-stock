import { isAxiosError, type AxiosResponse } from 'axios'
import type { ProblemDetail } from '../tipos/problema'

export interface ArchivoDescargado {
  nombre: string
  blob: Blob
}

/** Extrae el nombre de `Content-Disposition` (`filename*=UTF-8''…` o `filename="…"`); null si no hay. */
export function nombreDeContentDisposition(encabezado: unknown): string | null {
  if (typeof encabezado !== 'string') return null
  const ext = /filename\*\s*=\s*(?:UTF-8|utf-8)''([^;]+)/i.exec(encabezado)
  if (ext) {
    try {
      return decodeURIComponent(ext[1].trim().replace(/^"|"$/g, ''))
    } catch {
      /* cae al nombre simple */
    }
  }
  const simple = /filename\s*=\s*("([^"]*)"|[^;]+)/i.exec(encabezado)
  if (!simple) return null
  const nombre = (simple[2] ?? simple[1]).trim()
  return nombre || null
}

export function leerArchivo(respuesta: Pick<AxiosResponse<Blob>, 'data' | 'headers'>, respaldo: string): ArchivoDescargado {
  const cabecera: unknown = respuesta.headers?.['content-disposition']
  return { nombre: nombreDeContentDisposition(cabecera) ?? respaldo, blob: respuesta.data }
}

/** Dispara la descarga en el navegador con un enlace temporal. */
export function guardarArchivo({ nombre, blob }: ArchivoDescargado): void {
  const url = URL.createObjectURL(blob)
  const enlace = document.createElement('a')
  enlace.href = url
  enlace.download = nombre
  enlace.style.display = 'none'
  document.body.appendChild(enlace)
  enlace.click()
  enlace.remove()
  setTimeout(() => URL.revokeObjectURL(url), 0)
}

async function blobAProblema(blob: Blob): Promise<ProblemDetail | null> {
  try {
    const texto = await blob.text()
    const datos: unknown = JSON.parse(texto)
    return typeof datos === 'object' && datos !== null && !Array.isArray(datos) ? (datos as ProblemDetail) : null
  } catch {
    return null
  }
}

/**
 * Cuando se pide `responseType: 'blob'`, un error llega también como Blob. Lo convierte a `ProblemDetail` y lo deja en
 * `error.response.data` para que `mensajeError` lo lea como cualquier otro error. Siempre relanza el error.
 */
export async function normalizarErrorBlob(error: unknown): Promise<never> {
  if (isAxiosError(error) && error.response && error.response.data instanceof Blob) {
    const problema = await blobAProblema(error.response.data)
    error.response.data = problema ?? {}
  }
  throw error
}
