import { keepPreviousData, useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import {
  actualizarPlan,
  aprobarPlan,
  cancelarPlan,
  crearPlan,
  listarPlanes,
  obtenerPlan,
  recibirPlan,
  sugerirPlan,
} from '../api/compras'
import type {
  ActualizarPlanRequest,
  AprobarPlanRequest,
  CancelarPlanRequest,
  CrearPlanRequest,
  FiltrosPlanes,
  PaginaPlanes,
  PlanCompraDetalleResponse,
  RecibirPlanRequest,
  SugerirPlanRequest,
} from '../tipos/compras'
import { invalidarStock } from './useInventario'

/** Aprobar o cancelar cambian el "en camino" (inventario, reorden, alertas, analítica). */
export const CLAVES_POSICION = ['compras', 'plan', 'inventario', 'alertas', 'analitica']

function invalidarPosicion(qc: QueryClient) {
  return Promise.all(CLAVES_POSICION.map((k) => qc.invalidateQueries({ queryKey: [k] })))
}

export function usePlanes(filtros: FiltrosPlanes, habilitado = true) {
  return useQuery<PaginaPlanes>({
    queryKey: ['compras', filtros],
    queryFn: () => listarPlanes(filtros),
    placeholderData: keepPreviousData,
    enabled: habilitado,
  })
}

/** `id` indefinido deshabilita la consulta. */
export function usePlan(id: number | undefined, habilitado = true) {
  return useQuery<PlanCompraDetalleResponse>({
    queryKey: ['plan', id],
    queryFn: () => obtenerPlan(id as number),
    enabled: habilitado && id !== undefined,
  })
}

/** Crear, sugerir y editar borradores no tocan stock ni posición: solo listados y detalle. */
export function useSugerirPlan() {
  const qc = useQueryClient()
  return useMutation<PlanCompraDetalleResponse, unknown, SugerirPlanRequest>({
    mutationFn: (datos) => sugerirPlan(datos),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['compras'] }),
  })
}

export function useCrearPlan() {
  const qc = useQueryClient()
  return useMutation<PlanCompraDetalleResponse, unknown, CrearPlanRequest>({
    mutationFn: (datos) => crearPlan(datos),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['compras'] }),
  })
}

export function useActualizarPlan(id: number) {
  const qc = useQueryClient()
  return useMutation<PlanCompraDetalleResponse, unknown, ActualizarPlanRequest>({
    mutationFn: (datos) => actualizarPlan(id, datos),
    onSuccess: (plan) => {
      qc.setQueryData(['plan', id], plan)
      return qc.invalidateQueries({ queryKey: ['compras'] })
    },
  })
}

export function useAprobarPlan(id: number) {
  const qc = useQueryClient()
  return useMutation<PlanCompraDetalleResponse, unknown, AprobarPlanRequest>({
    mutationFn: (datos) => aprobarPlan(id, datos),
    onSuccess: (plan) => {
      qc.setQueryData(['plan', id], plan)
      return invalidarPosicion(qc)
    },
  })
}

export function useCancelarPlan(id: number) {
  const qc = useQueryClient()
  return useMutation<PlanCompraDetalleResponse, unknown, CancelarPlanRequest>({
    mutationFn: (datos) => cancelarPlan(id, datos),
    onSuccess: (plan) => {
      qc.setQueryData(['plan', id], plan)
      return invalidarPosicion(qc)
    },
  })
}

/** Recibir genera ENTRADAS: además de la posición, invalida todo lo de stock (kardex, matriz...). */
export function useRecibirPlan(id: number) {
  const qc = useQueryClient()
  return useMutation<PlanCompraDetalleResponse, unknown, RecibirPlanRequest>({
    mutationFn: (datos) => recibirPlan(id, datos),
    onSuccess: (plan) => {
      qc.setQueryData(['plan', id], plan)
      return invalidarStock(qc, CLAVES_POSICION)
    },
  })
}
