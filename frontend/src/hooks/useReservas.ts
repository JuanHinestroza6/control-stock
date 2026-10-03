import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  cancelarReserva,
  convertirReservas,
  crearReserva,
  extenderReserva,
  listarReservas,
  obtenerReserva,
} from '../api/reservas'
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
import { invalidarStock } from './useInventario'

export function useReservas(filtros: FiltrosReservas) {
  return useQuery<PaginaReservas>({
    queryKey: ['reservas', filtros],
    queryFn: () => listarReservas(filtros),
    placeholderData: keepPreviousData,
  })
}

/** `id` indefinido deshabilita la consulta. */
export function useReserva(id: number | undefined) {
  return useQuery<ReservaResponse>({
    queryKey: ['reserva', id],
    queryFn: () => obtenerReserva(id as number),
    enabled: id !== undefined,
  })
}

export function useCrearReserva() {
  const qc = useQueryClient()
  return useMutation<ReservaResponse[], unknown, CrearReservaRequest>({
    mutationFn: (datos) => crearReserva(datos),
    onSuccess: () => invalidarStock(qc),
  })
}

export function useCancelarReserva(id: number) {
  const qc = useQueryClient()
  return useMutation<ReservaResponse, unknown, CancelarReservaRequest>({
    mutationFn: (datos) => cancelarReserva(id, datos),
    onSuccess: () => invalidarStock(qc),
  })
}

/** Extender no mueve stock: solo refresca reservas. */
export function useExtenderReserva(id: number) {
  const qc = useQueryClient()
  return useMutation<ReservaResponse, unknown, ExtenderReservaRequest>({
    mutationFn: (datos) => extenderReserva(id, datos),
    onSuccess: (reserva) => {
      qc.setQueryData(['reserva', id], reserva)
      return qc.invalidateQueries({ queryKey: ['reservas'] })
    },
  })
}

export function useConvertirReservas() {
  const qc = useQueryClient()
  return useMutation<VentaDetalleResponse, unknown, ConvertirReservasRequest>({
    mutationFn: (datos) => convertirReservas(datos),
    onSuccess: () => invalidarStock(qc, ['ventas', 'venta']),
  })
}
