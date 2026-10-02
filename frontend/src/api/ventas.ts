import type {
  AnularVentaRequest,
  DevolucionRequest,
  DevolucionResponse,
  FiltrosVentas,
  PaginaVentas,
  RegistrarVentaRequest,
  VentaDetalleResponse,
} from '../tipos/ventas'
import { cliente } from './cliente'

/** Construye los query params del listado; omite los filtros indefinidos. */
export function parametrosVentas(filtros: FiltrosVentas): Record<string, string | number> {
  const params: Record<string, string | number> = {}
  if (filtros.desde) params.desde = filtros.desde
  if (filtros.hasta) params.hasta = filtros.hasta
  if (filtros.estado) params.estado = filtros.estado
  if (filtros.page !== undefined) params.page = filtros.page
  if (filtros.size !== undefined) params.size = filtros.size
  if (filtros.sort) params.sort = filtros.sort
  return params
}

export async function listarVentas(filtros: FiltrosVentas): Promise<PaginaVentas> {
  const { data } = await cliente.get<PaginaVentas>('/ventas', { params: parametrosVentas(filtros) })
  return data
}

export async function obtenerVenta(id: number): Promise<VentaDetalleResponse> {
  const { data } = await cliente.get<VentaDetalleResponse>(`/ventas/${id}`)
  return data
}

export async function registrarVenta(datos: RegistrarVentaRequest): Promise<VentaDetalleResponse> {
  const { data } = await cliente.post<VentaDetalleResponse>('/ventas', datos)
  return data
}

export async function anularVenta(id: number, datos: AnularVentaRequest): Promise<VentaDetalleResponse> {
  const { data } = await cliente.post<VentaDetalleResponse>(`/ventas/${id}/anular`, datos)
  return data
}

export async function registrarDevolucion(ventaId: number, datos: DevolucionRequest): Promise<DevolucionResponse> {
  const { data } = await cliente.post<DevolucionResponse>(`/ventas/${ventaId}/devoluciones`, datos)
  return data
}
