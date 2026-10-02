import type { AxiosAdapter, InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it } from 'vitest'
import { cliente } from './cliente'
import { parametrosInventario, registrarAjuste, registrarEntrada } from './inventario'
import { parametrosMovimientos } from './movimientos'
import { anularVenta, parametrosVentas, registrarDevolucion, registrarVenta } from './ventas'

describe('parámetros de consulta', () => {
  it('inventario omite vacíos y recorta q', () => {
    expect(parametrosInventario({ q: '  ', incluirInactivas: undefined })).toEqual({})
    expect(
      parametrosInventario({ q: ' liv ', categoriaId: 1, estadoStock: 'BAJO', incluirInactivas: false, page: 0, size: 20, sort: 'sku,asc' }),
    ).toEqual({ q: 'liv', categoriaId: 1, estadoStock: 'BAJO', incluirInactivas: false, page: 0, size: 20, sort: 'sku,asc' })
  })
  it('movimientos no envía sort', () => {
    expect(
      parametrosMovimientos({ varianteId: 3, tipo: 'ENTRADA', desde: '2026-10-01', hasta: '2026-10-02', page: 1, sort: 'x' }),
    ).toEqual({ varianteId: 3, tipo: 'ENTRADA', desde: '2026-10-01', hasta: '2026-10-02', page: 1 })
  })
  it('ventas omite indefinidos', () => {
    expect(parametrosVentas({})).toEqual({})
    expect(parametrosVentas({ estado: 'ANULADA', sort: 'fecha,desc' })).toEqual({ estado: 'ANULADA', sort: 'fecha,desc' })
  })
})

describe('escrituras', () => {
  const original = cliente.defaults.adapter
  afterEach(() => {
    cliente.defaults.adapter = original
  })

  it('usa método y URL del contrato', async () => {
    const peticiones: InternalAxiosRequestConfig[] = []
    const adaptador: AxiosAdapter = (config) => {
      peticiones.push(config)
      return Promise.resolve({ status: 200, data: {}, statusText: '', headers: {}, config })
    }
    cliente.defaults.adapter = adaptador
    await registrarEntrada({ lineas: [{ varianteId: 1, cantidad: 2, costoUnitario: 10 }] })
    await registrarAjuste({ varianteId: 1, tipo: 'AJUSTE_NEGATIVO', cantidad: 1, observacion: 'merma' })
    await registrarVenta({ lineas: [{ varianteId: 1, cantidad: 1 }] })
    await anularVenta(7, { motivo: 'error de digitación' })
    await registrarDevolucion(7, { detalleVentaId: 2, cantidad: 1, motivo: 'talla errada', aptaParaVenta: true })
    expect(peticiones.map((p) => `${p.method} ${p.url}`)).toEqual([
      'post /inventario/entradas',
      'post /inventario/ajustes',
      'post /ventas',
      'post /ventas/7/anular',
      'post /ventas/7/devoluciones',
    ])
  })
})
