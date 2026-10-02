import type {
  ActualizarProductoRequest,
  CrearProductoRequest,
  FiltrosProductos,
  PaginaProductos,
  ProductoDetalleResponse,
} from '../tipos/catalogo'
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

export async function obtenerProducto(id: number): Promise<ProductoDetalleResponse> {
  const { data } = await cliente.get<ProductoDetalleResponse>(`/productos/${id}`)
  return data
}

export async function crearProducto(datos: CrearProductoRequest): Promise<ProductoDetalleResponse> {
  const { data } = await cliente.post<ProductoDetalleResponse>('/productos', datos)
  return data
}

export async function actualizarProducto(id: number, datos: ActualizarProductoRequest): Promise<ProductoDetalleResponse> {
  const { data } = await cliente.put<ProductoDetalleResponse>(`/productos/${id}`, datos)
  return data
}

export async function cambiarEstadoProducto(id: number, activo: boolean): Promise<ProductoDetalleResponse> {
  const { data } = await cliente.patch<ProductoDetalleResponse>(`/productos/${id}/estado`, { activo })
  return data
}
