import type { AxiosAdapter, InternalAxiosRequestConfig } from 'axios'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderHook, waitFor } from '@testing-library/react'
import { createElement, type ReactNode } from 'react'
import { afterEach, describe, expect, it } from 'vitest'
import { useAprobarPlan, useCancelarPlan, useCrearPlan, useRecibirPlan } from '../hooks/useCompras'
import { cliente } from './cliente'
import * as api from './compras'
import * as analitica from './analitica'

const original = cliente.defaults.adapter
afterEach(() => {
  cliente.defaults.adapter = original
})

function capturar(status = 200, data: unknown = {}) {
  const vistas: { method?: string; url?: string; params?: unknown; data?: unknown }[] = []
  const adaptador: AxiosAdapter = (config: InternalAxiosRequestConfig) => {
    vistas.push({ method: config.method, url: config.url, params: config.params, data: config.data })
    return Promise.resolve({ data, status, statusText: '', headers: {}, config })
  }
  cliente.defaults.adapter = adaptador
  return vistas
}

describe('parámetros de la fase 6', () => {
  it('planes omite indefinidos', () => {
    expect(api.parametrosPlanes({})).toEqual({})
    expect(api.parametrosPlanes({ estado: 'APROBADO', page: 0, size: 20, sort: 'creadoEn,desc' })).toEqual({
      estado: 'APROBADO', page: 0, size: 20, sort: 'creadoEn,desc',
    })
  })
  it('tendencias y pronósticos', () => {
    expect(analitica.parametrosTendencias({})).toEqual({})
    expect(analitica.parametrosTendencias({ desde: '2025-01', hasta: '2026-09', categoriaId: 1, productoId: 4 })).toEqual({
      desde: '2025-01', hasta: '2026-09', categoriaId: 1, productoId: 4,
    })
    expect(analitica.parametrosPronosticos({ q: '  ', page: 1 })).toEqual({ page: 1 })
    expect(analitica.parametrosPronosticos({ q: ' liv ', metodo: 'HOLT_WINTERS', categoriaId: 2 })).toEqual({
      q: 'liv', metodo: 'HOLT_WINTERS', categoriaId: 2,
    })
  })
})

describe('funciones de API de la fase 6', () => {
  it('compras usa las rutas del contrato', async () => {
    const v = capturar()
    await api.listarPlanes({ estado: 'BORRADOR' })
    await api.obtenerPlan(7)
    await api.sugerirPlan({ categoriaId: 2 })
    await api.crearPlan({ lineas: [{ varianteId: 1, cantidad: 5 }] })
    await api.actualizarPlan(7, { version: 1, lineas: [{ varianteId: 1, cantidad: 5 }] })
    await api.aprobarPlan(7, { version: 2 })
    await api.cancelarPlan(7, { motivo: 'Proveedor sin stock' })
    await api.recibirPlan(7, { lineas: [{ detalleId: 1, cantidadRecibida: 5, costoUnitario: 10 }] })
    expect(v.map((x) => `${x.method} ${x.url}`)).toEqual([
      'get /compras/planes',
      'get /compras/planes/7',
      'post /compras/planes/sugerido',
      'post /compras/planes',
      'put /compras/planes/7',
      'post /compras/planes/7/aprobar',
      'post /compras/planes/7/cancelar',
      'post /compras/planes/7/recibir',
    ])
    expect(v[0].params).toEqual({ estado: 'BORRADOR' })
  })
  it('analitica nueva usa las rutas del contrato', async () => {
    const v = capturar()
    await analitica.obtenerTendencias({ desde: '2025-01' })
    await analitica.listarPronosticos({})
    await analitica.obtenerPronostico(9)
    expect(v.map((x) => `${x.method} ${x.url}`)).toEqual([
      'get /analitica/tendencias',
      'get /analitica/pronosticos',
      'get /analitica/pronosticos/9',
    ])
  })
})

function envoltorio(qc: QueryClient) {
  return ({ children }: { children: ReactNode }) => createElement(QueryClientProvider, { client: qc }, children)
}

function clienteConClaves(claves: string[]) {
  const qc = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  claves.forEach((k) => qc.setQueryData([k], 1))
  return qc
}

describe('invalidaciones de compras', () => {
  const todas = ['compras', 'plan', 'inventario', 'alertas', 'analitica', 'kardex', 'movimientos', 'ventas']

  it('crear solo invalida compras', async () => {
    capturar(201, { id: 1 })
    const qc = clienteConClaves(todas)
    const { result } = renderHook(() => useCrearPlan(), { wrapper: envoltorio(qc) })
    result.current.mutate({ lineas: [{ varianteId: 1, cantidad: 1 }] })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(todas.map((k) => qc.getQueryState([k])?.isInvalidated)).toEqual([true, false, false, false, false, false, false, false])
  })

  it('aprobar y cancelar invalidan posición (compras, inventario, alertas, analitica)', async () => {
    capturar(200, { id: 7 })
    for (const hook of [useAprobarPlan, useCancelarPlan]) {
      const qc = clienteConClaves(todas)
      const { result } = renderHook(() => hook(7), { wrapper: envoltorio(qc) })
      result.current.mutate({ version: 1, motivo: 'Motivo válido' })
      await waitFor(() => expect(result.current.isSuccess).toBe(true))
      expect(todas.map((k) => qc.getQueryState([k])?.isInvalidated)).toEqual([true, true, true, true, true, false, false, false])
    }
  })

  it('recibir invalida además el stock (movimientos) y no toca ventas', async () => {
    capturar(200, { id: 7 })
    const qc = clienteConClaves(todas)
    const { result } = renderHook(() => useRecibirPlan(7), { wrapper: envoltorio(qc) })
    result.current.mutate({ lineas: [{ detalleId: 1, cantidadRecibida: 1, costoUnitario: 1 }] })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(todas.map((k) => qc.getQueryState([k])?.isInvalidated)).toEqual([true, true, true, true, true, false, true, false])
  })
})
