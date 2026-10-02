import type { FiltrosMovimientos, PaginaMovimientos } from '../tipos/inventario'
import { cliente } from './cliente'

/** Construye los query params del kardex; omite los filtros indefinidos. `sort` no aplica (orden fijo). */
export function parametrosMovimientos(filtros: FiltrosMovimientos): Record<string, string | number> {
  const params: Record<string, string | number> = {}
  if (filtros.varianteId !== undefined) params.varianteId = filtros.varianteId
  if (filtros.tipo) params.tipo = filtros.tipo
  if (filtros.desde) params.desde = filtros.desde
  if (filtros.hasta) params.hasta = filtros.hasta
  if (filtros.page !== undefined) params.page = filtros.page
  if (filtros.size !== undefined) params.size = filtros.size
  return params
}

export async function listarMovimientos(filtros: FiltrosMovimientos): Promise<PaginaMovimientos> {
  const { data } = await cliente.get<PaginaMovimientos>('/movimientos', { params: parametrosMovimientos(filtros) })
  return data
}
