import type {
  CancelarReservaRequest,
  ConvertirReservasRequest,
  CrearReservaRequest,
  ExtenderReservaRequest,
  FiltrosReservas,
  PaginaReservas,
  ReservaResponse,
} from '../tipos/reservas'
import type { VentaDetalleResponse } from '../tipos/ventas'
import { cliente } from './cliente'

/** Construye los query params; omite los filtros vacíos o indefinidos. */
export function parametrosReservas(filtros: FiltrosReservas): Record<string, string | number | boolean> {
  const params: Record<string, string | number | boolean> = {}
  if (filtros.estado) params.estado = filtros.estado
  const q = filtros.q?.trim()
  if (q) params.q = q
  if (filtros.varianteId !== undefined) params.varianteId = filtros.varianteId
  if (filtros.porVencer !== undefined) params.porVencer = filtros.porVencer
  if (filtros.page !== undefined) params.page = filtros.page
  if (filtros.size !== undefined) params.size = filtros.size
  if (filtros.sort) params.sort = filtros.sort
  return params
}

export async function listarReservas(filtros: FiltrosReservas): Promise<PaginaReservas> {
  const { data } = await cliente.get<PaginaReservas>('/reservas', { params: parametrosReservas(filtros) })
  return data
}

export async function obtenerReserva(id: number): Promise<ReservaResponse> {
  const { data } = await cliente.get<ReservaResponse>(`/reservas/${id}`)
  return data
}

export async function crearReserva(datos: CrearReservaRequest): Promise<ReservaResponse[]> {
  const { data } = await cliente.post<ReservaResponse[]>('/reservas', datos)
  return data
}

export async function cancelarReserva(id: number, datos: CancelarReservaRequest): Promise<ReservaResponse> {
  const { data } = await cliente.post<ReservaResponse>(`/reservas/${id}/cancelar`, datos)
  return data
}

export async function extenderReserva(id: number, datos: ExtenderReservaRequest): Promise<ReservaResponse> {
  const { data } = await cliente.post<ReservaResponse>(`/reservas/${id}/extender`, datos)
  return data
}

export async function convertirReservas(datos: ConvertirReservasRequest): Promise<VentaDetalleResponse> {
  const { data } = await cliente.post<VentaDetalleResponse>('/ventas/desde-reservas', datos)
  return data
}
