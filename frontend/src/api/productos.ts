import type { FiltrosProductos, PaginaProductos } from '../tipos/catalogo'
import { cliente } from './cliente'

/** Construye los query params del listado; omite los filtros vacíos o indefinidos. */
export function parametrosProductos(filtros: FiltrosProductos): Record<string, string | number | boolean> {
  const params: Record<string, string | number | boolean> = {}
  const q = filtros.q?.trim()
  if (q) params.q = q
  if (filtros.categoriaId !== undefined) params.categoriaId = filtros.categoriaId
  if (filtros.activo !== undefined) params.activo = filtros.activo
  if (filtros.page !== undefined) params.page = filtros.page
  if (filtros.size !== undefined) params.size = filtros.size
  if (filtros.sort) params.sort = filtros.sort
  return params
}

export async function listarProductos(filtros: FiltrosProductos): Promise<PaginaProductos> {
  const { data } = await cliente.get<PaginaProductos>('/productos', { params: parametrosProductos(filtros) })
  return data
}
