import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  listarAbc,
  listarPronosticos,
  listarReorden,
  obtenerMasVendidos,
  obtenerPronostico,
  obtenerResumenAbc,
  obtenerTendencias,
  obtenerUltimaEjecucion,
  recalcularAnalitica,
} from '../api/analitica'
import type {
  EjecucionAnaliticaResponse,
  FiltrosAbc,
  FiltrosMasVendidos,
  FiltrosPronosticos,
  FiltrosReorden,
  FiltrosTendencias,
  MasVendidosResponse,
  PaginaAbc,
  PaginaPronosticos,
  PaginaReorden,
  PronosticoDetalleResponse,
  ResumenAbcResponse,
  TendenciaResponse,
} from '../tipos/analitica'

/** `habilitado = false` deja la consulta en reposo (rol sin acceso). */
export function useMasVendidos(filtros: FiltrosMasVendidos, habilitado = true) {
  return useQuery<MasVendidosResponse>({
    queryKey: ['analitica', 'mas-vendidos', filtros],
    queryFn: () => obtenerMasVendidos(filtros),
    placeholderData: keepPreviousData,
    enabled: habilitado,
  })
}

export function useResumenAbc(habilitado = true) {
  return useQuery<ResumenAbcResponse>({
    queryKey: ['analitica', 'abc', 'resumen'],
    queryFn: obtenerResumenAbc,
    enabled: habilitado,
  })
}

export function useAbc(filtros: FiltrosAbc, habilitado = true) {
  return useQuery<PaginaAbc>({
    queryKey: ['analitica', 'abc', 'lista', filtros],
    queryFn: () => listarAbc(filtros),
    placeholderData: keepPreviousData,
    enabled: habilitado,
  })
}

export function useReorden(filtros: FiltrosReorden, habilitado = true) {
  return useQuery<PaginaReorden>({
    queryKey: ['analitica', 'reorden', filtros],
    queryFn: () => listarReorden(filtros),
    placeholderData: keepPreviousData,
    enabled: habilitado,
  })
}

export function useUltimaEjecucion(habilitado = true) {
  return useQuery<EjecucionAnaliticaResponse | null>({
    queryKey: ['analitica', 'ejecucion'],
    queryFn: obtenerUltimaEjecucion,
    enabled: habilitado,
  })
}

export function useTendencias(filtros: FiltrosTendencias, habilitado = true) {
  return useQuery<TendenciaResponse>({
    queryKey: ['analitica', 'tendencias', filtros],
    queryFn: () => obtenerTendencias(filtros),
    placeholderData: keepPreviousData,
    enabled: habilitado,
  })
}

export function usePronosticos(filtros: FiltrosPronosticos, habilitado = true) {
  return useQuery<PaginaPronosticos>({
    queryKey: ['analitica', 'pronosticos', 'lista', filtros],
    queryFn: () => listarPronosticos(filtros),
    placeholderData: keepPreviousData,
    enabled: habilitado,
  })
}

/** `productoId` indefinido deshabilita la consulta. */
export function usePronostico(productoId: number | undefined, habilitado = true) {
  return useQuery<PronosticoDetalleResponse>({
    queryKey: ['analitica', 'pronosticos', 'detalle', productoId],
    queryFn: () => obtenerPronostico(productoId as number),
    enabled: habilitado && productoId !== undefined,
  })
}

/** Claves que un recálculo deja obsoletas (clase y ROP viajan en inventario; las alertas se re-evalúan). */
export const CLAVES_RECALCULO = ['analitica', 'inventario', 'alertas']

export function useRecalcular() {
  const qc = useQueryClient()
  return useMutation<EjecucionAnaliticaResponse, unknown, void>({
    mutationFn: () => recalcularAnalitica(),
    onSuccess: () => Promise.all(CLAVES_RECALCULO.map((k) => qc.invalidateQueries({ queryKey: [k] }))),
  })
}
