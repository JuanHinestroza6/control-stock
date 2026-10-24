import type { AxiosAdapter, InternalAxiosRequestConfig } from 'axios'
import { QueryClient } from '@tanstack/react-query'
import { afterEach, describe, expect, it } from 'vitest'
import { invalidarStock } from '../hooks/useInventario'
import { parametrosAlertas, atenderAlerta, obtenerAlertasUrgentes, obtenerResumenAlertas } from './alertas'
import { cliente } from './cliente'
import { cancelarReserva, convertirReservas, crearReserva, extenderReserva, parametrosReservas } from './reservas'

describe('parámetros de consulta', () => {
  it('reservas omite vacíos y recorta q', () => {
    expect(parametrosReservas({ q: '  ', varianteId: undefined })).toEqual({})
    expect(parametrosReservas({ estado: 'ACTIVA', q: ' ana ', varianteId: 4, porVencer: false, page: 0, size: 20, sort: 'venceEn,asc' })).toEqual({
      estado: 'ACTIVA', q: 'ana', varianteId: 4, porVencer: false, page: 0, size: 20, sort: 'venceEn,asc',
    })
  })
  it('alertas omite indefinidos', () => {
    expect(parametrosAlertas({})).toEqual({})
    expect(parametrosAlertas({ estado: 'ACTIVA', tipo: 'AGOTADO', page: 1 })).toEqual({ estado: 'ACTIVA', tipo: 'AGOTADO', page: 1 })
  })
})

describe('escrituras y lecturas', () => {
  const original = cliente.defaults.adapter
  afterEach(() => {
    cliente.defaults.adapter = original
  })
  function capturar() {
    const vistas: { method?: string; url?: string; data?: unknown }[] = []
    const adaptador: AxiosAdapter = (config: InternalAxiosRequestConfig) => {
      vistas.push({ method: config.method, url: config.url, data: config.data ? JSON.parse(config.data as string) : undefined })
      return Promise.resolve({ data: {}, status: 200, statusText: 'OK', headers: {}, config })
    }
    cliente.defaults.adapter = adaptador
    return vistas
  }
  it('usa las rutas del contrato', async () => {
    const v = capturar()
    await crearReserva({ clienteNombre: 'Ana', clienteContacto: '300', lineas: [{ varianteId: 1, cantidad: 2 }] })
    await cancelarReserva(7, { motivo: 'Cliente desistió' })
    await extenderReserva(7, { horas: 24 })
    await convertirReservas({ reservaIds: [7, 8] })
    await obtenerResumenAlertas()
    await obtenerAlertasUrgentes(5)
    await atenderAlerta(3)
    expect(v.map((x) => `${x.method} ${x.url}`)).toEqual([
      'post /reservas',
      'post /reservas/7/cancelar',
      'post /reservas/7/extender',
      'post /ventas/desde-reservas',
      'get /alertas/resumen',
      'get /alertas/urgentes',
      'post /alertas/3/atender',
    ])
    expect(v[2].data).toEqual({ horas: 24 })
  })
})

describe('invalidarStock', () => {
  it('invalida también reservas y alertas', async () => {
    const qc = new QueryClient()
    const claves = ['reservas', 'reserva', 'alertas', 'inventario']
    claves.forEach((k) => qc.setQueryData([k], 1))
    await invalidarStock(qc)
    claves.forEach((k) => expect(qc.getQueryState([k])?.isInvalidated).toBe(true))
  })
})
