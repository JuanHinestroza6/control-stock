import type { AlertaResponse, FiltrosAlertas, PaginaAlertas, ResumenAlertasResponse } from '../tipos/alertas'
import { cliente } from './cliente'

/** Construye los query params; omite los filtros indefinidos. */
export function parametrosAlertas(filtros: FiltrosAlertas): Record<string, string | number> {
  const params: Record<string, string | number> = {}
  if (filtros.estado) params.estado = filtros.estado
  if (filtros.tipo) params.tipo = filtros.tipo
  if (filtros.page !== undefined) params.page = filtros.page
  if (filtros.size !== undefined) params.size = filtros.size
  return params
}

export async function listarAlertas(filtros: FiltrosAlertas): Promise<PaginaAlertas> {
  const { data } = await cliente.get<PaginaAlertas>('/alertas', { params: parametrosAlertas(filtros) })
  return data
}

/** Alertas de stock activas más urgentes (AGOTADO por umbral y antigüedad, luego STOCK_BAJO por cobertura). */
export async function obtenerAlertasUrgentes(limite: number): Promise<AlertaResponse[]> {
  const { data } = await cliente.get<AlertaResponse[]>('/alertas/urgentes', { params: { limite } })
  return data
}

export async function obtenerResumenAlertas(): Promise<ResumenAlertasResponse> {
  const { data } = await cliente.get<ResumenAlertasResponse>('/alertas/resumen')
  return data
}

export async function atenderAlerta(id: number): Promise<AlertaResponse> {
  const { data } = await cliente.post<AlertaResponse>(`/alertas/${id}/atender`)
  return data
}
