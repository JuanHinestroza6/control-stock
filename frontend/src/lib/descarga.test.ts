import { AxiosError, type AxiosAdapter, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cliente } from '../api/cliente'
import { exportarRentabilidad } from '../api/reportes'
import { guardarArchivo, nombreDeContentDisposition } from './descarga'
import { mensajeError } from './errores'

const original = cliente.defaults.adapter
afterEach(() => {
  cliente.defaults.adapter = original
  vi.restoreAllMocks()
})

function responder(status: number, data: Blob, headers: Record<string, string> = {}) {
  const adaptador: AxiosAdapter = (config: InternalAxiosRequestConfig) => {
    const respuesta = { data, status, statusText: '', headers, config }
    if (status >= 400) return Promise.reject(new AxiosError('fallo', 'ERR_BAD_REQUEST', config, null, respuesta))
    return Promise.resolve(respuesta)
  }
  cliente.defaults.adapter = adaptador
}

describe('nombreDeContentDisposition', () => {
  it('lee filename entre comillas, sin comillas y filename*', () => {
    expect(nombreDeContentDisposition('attachment; filename="rentabilidad.pdf"')).toBe('rentabilidad.pdf')
    expect(nombreDeContentDisposition('attachment; filename=rentabilidad.xlsx')).toBe('rentabilidad.xlsx')
    expect(nombreDeContentDisposition("attachment; filename*=UTF-8''libro%20ventas.xlsx")).toBe('libro ventas.xlsx')
  })
  it('devuelve null sin nombre', () => {
    expect(nombreDeContentDisposition(undefined)).toBeNull()
    expect(nombreDeContentDisposition('attachment')).toBeNull()
  })
})

describe('exportar con blob', () => {
  it('devuelve el blob y el nombre del encabezado', async () => {
    const blob = new Blob(['pdf'], { type: 'application/pdf' })
    responder(200, blob, { 'content-disposition': 'attachment; filename="rentabilidad-2026-09.pdf"' })
    const archivo = await exportarRentabilidad({ desde: '2026-09-01' }, 'PDF')
    expect(archivo.nombre).toBe('rentabilidad-2026-09.pdf')
    expect(archivo.blob).toBe(blob)
  })

  it('usa un nombre de respaldo sin encabezado', async () => {
    responder(200, new Blob(['x']))
    const archivo = await exportarRentabilidad({}, 'XLSX')
    expect(archivo.nombre).toBe('rentabilidad.xlsx')
  })

  it('envía formato y responseType blob', async () => {
    let vista: InternalAxiosRequestConfig | undefined
    cliente.defaults.adapter = (config) => {
      vista = config
      return Promise.resolve({ data: new Blob(['x']), status: 200, statusText: '', headers: {}, config })
    }
    await exportarRentabilidad({ categoriaId: 3, agrupacion: 'PRODUCTO' }, 'PDF')
    expect(vista?.url).toBe('/reportes/rentabilidad/exportar')
    expect(vista?.responseType).toBe('blob')
    expect(vista?.params).toEqual({ categoriaId: 3, agrupacion: 'PRODUCTO', formato: 'PDF' })
  })

  it('un error blob se convierte en ProblemDetail legible', async () => {
    const problema = { title: 'Unprocessable', status: 422, detail: 'El rango supera el máximo.' }
    responder(422, new Blob([JSON.stringify(problema)], { type: 'application/json' }))
    const error = await exportarRentabilidad({}, 'PDF').catch((e: unknown) => e)
    expect(mensajeError(error)).toBe('El rango supera el máximo.')
  })

  it('un error blob que no es JSON cae en el mensaje por defecto', async () => {
    responder(500, new Blob(['<html>'], { type: 'text/html' }))
    const error = await exportarRentabilidad({}, 'PDF').catch((e: unknown) => e)
    expect(mensajeError(error)).toContain('error inesperado')
  })
})

describe('guardarArchivo', () => {
  it('crea un enlace con el nombre y lo activa', () => {
    URL.createObjectURL = vi.fn(() => 'blob:prueba')
    URL.revokeObjectURL = vi.fn()
    let nombre = ''
    const clic = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      nombre = this.download
    })
    guardarArchivo({ nombre: 'reporte.pdf', blob: new Blob(['x']) })
    expect(clic).toHaveBeenCalledTimes(1)
    expect(nombre).toBe('reporte.pdf')
    expect(document.querySelector('a[download]')).toBeNull()
  })
})
