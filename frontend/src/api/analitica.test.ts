import type { AxiosAdapter, InternalAxiosRequestConfig } from 'axios'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderHook, waitFor } from '@testing-library/react'
import { createElement, type ReactNode } from 'react'
import { afterEach, describe, expect, it } from 'vitest'
import { useRecalcular } from '../hooks/useAnalitica'
import { cliente } from './cliente'
import * as api from './analitica'

const original = cliente.defaults.adapter
afterEach(() => {
  cliente.defaults.adapter = original
})

function capturar(status = 200, data: unknown = {}) {
  const vistas: { method?: string; url?: string; params?: unknown }[] = []
  const adaptador: AxiosAdapter = (config: InternalAxiosRequestConfig) => {
    vistas.push({ method: config.method, url: config.url, params: config.params })
    return Promise.resolve({ data, status, statusText: '', headers: {}, config })
  }
  cliente.defaults.adapter = adaptador
  return vistas
}

describe('parámetros de analítica', () => {
  it('más vendidos omite indefinidos y conserva el resto', () => {
    expect(api.parametrosMasVendidos({})).toEqual({})
    expect(
      api.parametrosMasVendidos({ desde: '2026-01-01', hasta: '2026-01-31', categoriaId: 2, criterio: 'INGRESO', agrupacion: 'VARIANTE', limite: 5 }),
    ).toEqual({ desde: '2026-01-01', hasta: '2026-01-31', categoriaId: 2, criterio: 'INGRESO', agrupacion: 'VARIANTE', limite: 5 })
  })
  it('abc y reorden recortan q y arman paginación', () => {
    expect(api.parametrosAbc({ q: '  ' })).toEqual({})
    expect(api.parametrosAbc({ clase: 'A', q: ' liv ', page: 0, size: 20, sort: 'posicion,asc' })).toEqual({
      clase: 'A', q: 'liv', page: 0, size: 20, sort: 'posicion,asc',
    })
    expect(api.parametrosReorden({ metodo: 'POISSON', enAlerta: false, clase: 'C', categoriaId: 1 })).toEqual({
      metodo: 'POISSON', enAlerta: false, clase: 'C', categoriaId: 1,
    })
  })
})

describe('funciones de API', () => {
  it('usa las rutas del contrato', async () => {
    const v = capturar()
    await api.obtenerMasVendidos({ limite: 3 })
    await api.listarAbc({})
    await api.obtenerResumenAbc()
    await api.listarReorden({})
    await api.obtenerUltimaEjecucion()
    await api.recalcularAnalitica()
    expect(v.map((x) => `${x.method} ${x.url}`)).toEqual([
      'get /analitica/mas-vendidos',
      'get /analitica/abc',
      'get /analitica/abc/resumen',
      'get /analitica/reorden',
      'get /analitica/ejecuciones/ultima',
      'post /analitica/recalcular',
    ])
    expect(v[0].params).toEqual({ limite: 3 })
  })
  it('204 de la última ejecución devuelve null', async () => {
    capturar(204, '')
    expect(await api.obtenerUltimaEjecucion()).toBeNull()
  })
})

describe('useRecalcular', () => {
  it('invalida analitica, inventario y alertas al terminar', async () => {
    capturar(200, { id: 1 })
    const qc = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
    const claves = ['analitica', 'inventario', 'alertas', 'ventas']
    claves.forEach((k) => qc.setQueryData([k], 1))
    const envoltorio = ({ children }: { children: ReactNode }) => createElement(QueryClientProvider, { client: qc }, children)
    const { result } = renderHook(() => useRecalcular(), { wrapper: envoltorio })
    result.current.mutate()
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(claves.map((k) => qc.getQueryState([k])?.isInvalidated)).toEqual([true, true, true, false])
  })
})

describe('tendencias y pronósticos (fase 6)', () => {
  it('parametrosTendencias omite indefinidos y conserva el resto', () => {
    expect(api.parametrosTendencias({})).toEqual({})
    expect(api.parametrosTendencias({ desde: '2025-01', hasta: '2026-09', categoriaId: 2, productoId: 7 })).toEqual({
      desde: '2025-01', hasta: '2026-09', categoriaId: 2, productoId: 7,
    })
  })
  it('parametrosPronosticos recorta q y arma paginación', () => {
    expect(api.parametrosPronosticos({ q: '  ' })).toEqual({})
    expect(api.parametrosPronosticos({ q: ' liv ', metodo: 'HOLT_WINTERS', categoriaId: 1, page: 1, size: 20, sort: 'nombre,asc' })).toEqual({
      q: 'liv', metodo: 'HOLT_WINTERS', categoriaId: 1, page: 1, size: 20, sort: 'nombre,asc',
    })
  })
  it('usa las rutas del contrato de tendencias y pronósticos', async () => {
    const v = capturar()
    await api.obtenerTendencias({ productoId: 3 })
    await api.listarPronosticos({ size: 10 })
    await api.obtenerPronostico(42)
    expect(v.map((x) => `${x.method} ${x.url}`)).toEqual([
      'get /analitica/tendencias',
      'get /analitica/pronosticos',
      'get /analitica/pronosticos/42',
    ])
    expect(v[0].params).toEqual({ productoId: 3 })
    expect(v[1].params).toEqual({ size: 10 })
  })
})
