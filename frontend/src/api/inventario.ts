import type {
  AjusteRequest,
  EntradaRequest,
  FiltrosInventario,
  MovimientoResponse,
  PaginaInventario,
} from '../tipos/inventario'
import { cliente } from './cliente'

/** Construye los query params; omite los filtros vacíos o indefinidos. */
export function parametrosInventario(filtros: FiltrosInventario): Record<string, string | number | boolean> {
  const params: Record<string, string | number | boolean> = {}
  const q = filtros.q?.trim()
  if (q) params.q = q
  if (filtros.categoriaId !== undefined) params.categoriaId = filtros.categoriaId
  if (filtros.estadoStock) params.estadoStock = filtros.estadoStock
  if (filtros.incluirInactivas !== undefined) params.incluirInactivas = filtros.incluirInactivas
  if (filtros.page !== undefined) params.page = filtros.page
  if (filtros.size !== undefined) params.size = filtros.size
  if (filtros.sort) params.sort = filtros.sort
  return params
}

export async function listarInventario(filtros: FiltrosInventario): Promise<PaginaInventario> {
  const { data } = await cliente.get<PaginaInventario>('/inventario', { params: parametrosInventario(filtros) })
  return data
}

export async function registrarEntrada(datos: EntradaRequest): Promise<MovimientoResponse[]> {
  const { data } = await cliente.post<MovimientoResponse[]>('/inventario/entradas', datos)
  return data
}

export async function registrarAjuste(datos: AjusteRequest): Promise<MovimientoResponse> {
  const { data } = await cliente.post<MovimientoResponse>('/inventario/ajustes', datos)
  return data
}
