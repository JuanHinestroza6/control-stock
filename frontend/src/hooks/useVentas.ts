import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { anularVenta, listarVentas, obtenerVenta, registrarDevolucion, registrarVenta } from '../api/ventas'
import type {
  AnularVentaRequest,
  DevolucionRequest,
  DevolucionResponse,
  FiltrosVentas,
  PaginaVentas,
  RegistrarVentaRequest,
  VentaDetalleResponse,
} from '../tipos/ventas'
import { invalidarStock } from './useInventario'

export function useVentas(filtros: FiltrosVentas) {
  return useQuery<PaginaVentas>({
    queryKey: ['ventas', filtros],
    queryFn: () => listarVentas(filtros),
    placeholderData: keepPreviousData,
  })
}

/** `id` indefinido deshabilita la consulta. */
export function useVenta(id: number | undefined) {
  return useQuery<VentaDetalleResponse>({
    queryKey: ['venta', id],
    queryFn: () => obtenerVenta(id as number),
    enabled: id !== undefined,
  })
}

export function useRegistrarVenta() {
  const qc = useQueryClient()
  return useMutation<VentaDetalleResponse, unknown, RegistrarVentaRequest>({
    mutationFn: (datos) => registrarVenta(datos),
    onSuccess: () => invalidarStock(qc, ['ventas', 'venta']),
  })
}

export function useAnularVenta(id: number) {
  const qc = useQueryClient()
  return useMutation<VentaDetalleResponse, unknown, AnularVentaRequest>({
    mutationFn: (datos) => anularVenta(id, datos),
    onSuccess: (venta) => {
      qc.setQueryData(['venta', id], venta)
      return invalidarStock(qc, ['ventas', 'venta'])
    },
  })
}

export function useRegistrarDevolucion(ventaId: number) {
  const qc = useQueryClient()
  return useMutation<DevolucionResponse, unknown, DevolucionRequest>({
    mutationFn: (datos) => registrarDevolucion(ventaId, datos),
    onSuccess: () => invalidarStock(qc, ['ventas', 'venta']),
  })
}
