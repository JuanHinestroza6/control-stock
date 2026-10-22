import { describe, expect, it } from 'vitest'
import { exportarTendencias } from './analitica'
import { cliente } from './cliente'
import { parametrosLibroVentas, parametrosRentabilidad, parametrosValorizacion } from './reportes'

describe('parámetros de reportes', () => {
  it('omiten indefinidos y conservan false', () => {
    expect(parametrosRentabilidad({})).toEqual({})
    expect(parametrosRentabilidad({ desde: '2026-01-01', orden: 'MARGEN', categoriaId: 2 })).toEqual({
      desde: '2026-01-01',
      orden: 'MARGEN',
      categoriaId: 2,
    })
    expect(parametrosLibroVentas({ agrupacion: 'MES' })).toEqual({ agrupacion: 'MES' })
    expect(parametrosValorizacion({ incluirInactivas: false })).toEqual({ incluirInactivas: false })
  })
})

describe('exportarTendencias', () => {
  it('usa los filtros de tendencias más el formato', async () => {
    const original = cliente.defaults.adapter
    let url: string | undefined
    let params: unknown
    cliente.defaults.adapter = (config) => {
      url = config.url
      params = config.params
      return Promise.resolve({ data: new Blob(['x']), status: 200, statusText: '', headers: {}, config })
    }
    const archivo = await exportarTendencias({ desde: '2025-01', productoId: 4 }, 'XLSX')
    cliente.defaults.adapter = original
    expect(url).toBe('/analitica/tendencias/exportar')
    expect(params).toEqual({ desde: '2025-01', productoId: 4, formato: 'XLSX' })
    expect(archivo.nombre).toBe('tendencias.xlsx')
  })
})
