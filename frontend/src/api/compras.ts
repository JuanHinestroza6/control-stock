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
import { cliente } from './cliente'

export function parametrosPlanes(f: FiltrosPlanes): Record<string, string | number> {
  const params: Record<string, string | number> = {}
  if (f.estado) params.estado = f.estado
  if (f.page !== undefined) params.page = f.page
  if (f.size !== undefined) params.size = f.size
  if (f.sort) params.sort = f.sort
  return params
}

export async function listarPlanes(filtros: FiltrosPlanes): Promise<PaginaPlanes> {
  const { data } = await cliente.get<PaginaPlanes>('/compras/planes', { params: parametrosPlanes(filtros) })
  return data
}

export async function obtenerPlan(id: number): Promise<PlanCompraDetalleResponse> {
  const { data } = await cliente.get<PlanCompraDetalleResponse>(`/compras/planes/${id}`)
  return data
}

export async function sugerirPlan(datos: SugerirPlanRequest): Promise<PlanCompraDetalleResponse> {
  const { data } = await cliente.post<PlanCompraDetalleResponse>('/compras/planes/sugerido', datos)
  return data
}

export async function crearPlan(datos: CrearPlanRequest): Promise<PlanCompraDetalleResponse> {
  const { data } = await cliente.post<PlanCompraDetalleResponse>('/compras/planes', datos)
  return data
}

export async function actualizarPlan(id: number, datos: ActualizarPlanRequest): Promise<PlanCompraDetalleResponse> {
  const { data } = await cliente.put<PlanCompraDetalleResponse>(`/compras/planes/${id}`, datos)
  return data
}

export async function aprobarPlan(id: number, datos: AprobarPlanRequest): Promise<PlanCompraDetalleResponse> {
  const { data } = await cliente.post<PlanCompraDetalleResponse>(`/compras/planes/${id}/aprobar`, datos)
  return data
}

export async function cancelarPlan(id: number, datos: CancelarPlanRequest): Promise<PlanCompraDetalleResponse> {
  const { data } = await cliente.post<PlanCompraDetalleResponse>(`/compras/planes/${id}/cancelar`, datos)
  return data
}

export async function recibirPlan(id: number, datos: RecibirPlanRequest): Promise<PlanCompraDetalleResponse> {
  const { data } = await cliente.post<PlanCompraDetalleResponse>(`/compras/planes/${id}/recibir`, datos)
  return data
}
