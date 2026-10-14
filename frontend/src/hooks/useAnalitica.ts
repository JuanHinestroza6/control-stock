import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  listarAbc,
  listarReorden,
  obtenerMasVendidos,
  obtenerResumenAbc,
  obtenerUltimaEjecucion,
  recalcularAnalitica,
} from '../api/analitica'
import type {
  EjecucionAnaliticaResponse,
  FiltrosAbc,
  FiltrosMasVendidos,
  FiltrosReorden,
  MasVendidosResponse,
  PaginaAbc,
  PaginaReorden,
  ResumenAbcResponse,
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

/** Claves que un recálculo deja obsoletas (clase y ROP viajan en inventario; las alertas se re-evalúan). */
export const CLAVES_RECALCULO = ['analitica', 'inventario', 'alertas']

export function useRecalcular() {
  const qc = useQueryClient()
  return useMutation<EjecucionAnaliticaResponse, unknown, void>({
    mutationFn: () => recalcularAnalitica(),
    onSuccess: () => Promise.all(CLAVES_RECALCULO.map((k) => qc.invalidateQueries({ queryKey: [k] }))),
  })
}
