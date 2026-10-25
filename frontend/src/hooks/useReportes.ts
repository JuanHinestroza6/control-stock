import { keepPreviousData, useMutation, useQuery } from '@tanstack/react-query'
import { exportarTendencias } from '../api/analitica'
import {
  exportarLibroVentas,
  exportarMovimientos,
  exportarRentabilidad,
  exportarValorizacion,
  obtenerLibroVentas,
  obtenerMovimientos,
  obtenerRentabilidad,
  obtenerValorizacion,
} from '../api/reportes'
import { guardarArchivo, type ArchivoDescargado } from '../lib/descarga'
import type { FiltrosTendencias } from '../tipos/analitica'
import type {
  FiltrosLibroVentas,
  FiltrosMovimientos,
  FiltrosRentabilidad,
  FiltrosValorizacion,
  FormatoExportacion,
  LibroVentasResponse,
  MovimientosResponse,
  RentabilidadResponse,
  ValorizacionResponse,
} from '../tipos/reportes'

/** `habilitado = false` deja la consulta en reposo (pestaña inactiva o rol sin acceso). */
export function useRentabilidad(filtros: FiltrosRentabilidad, habilitado = true) {
  return useQuery<RentabilidadResponse>({
    queryKey: ['reportes', 'rentabilidad', filtros],
    queryFn: () => obtenerRentabilidad(filtros),
    placeholderData: keepPreviousData,
    enabled: habilitado,
  })
}

export function useLibroVentas(filtros: FiltrosLibroVentas, habilitado = true) {
  return useQuery<LibroVentasResponse>({
    queryKey: ['reportes', 'libro-ventas', filtros],
    queryFn: () => obtenerLibroVentas(filtros),
    placeholderData: keepPreviousData,
    enabled: habilitado,
  })
}

export function useValorizacion(filtros: FiltrosValorizacion, habilitado = true) {
  return useQuery<ValorizacionResponse>({
    queryKey: ['reportes', 'valorizacion', filtros],
    queryFn: () => obtenerValorizacion(filtros),
    placeholderData: keepPreviousData,
    enabled: habilitado,
  })
}

export function useMovimientosPeriodo(filtros: FiltrosMovimientos, habilitado = true) {
  return useQuery<MovimientosResponse>({
    queryKey: ['reportes', 'movimientos', filtros],
    queryFn: () => obtenerMovimientos(filtros),
    placeholderData: keepPreviousData,
    enabled: habilitado,
  })
}

async function conDescarga(obtener: () => Promise<ArchivoDescargado>) {
  const archivo = await obtener()
  guardarArchivo(archivo)
  return archivo
}

/** Exportan y descargan; los errores (incluso los que llegan como blob) se leen con `mensajeError`. */
export function useExportarRentabilidad() {
  return useMutation({
    mutationFn: ({ filtros, formato }: { filtros: FiltrosRentabilidad; formato: FormatoExportacion }) =>
      conDescarga(() => exportarRentabilidad(filtros, formato)),
  })
}

export function useExportarLibroVentas() {
  return useMutation({
    mutationFn: ({ filtros, formato }: { filtros: FiltrosLibroVentas; formato: FormatoExportacion }) =>
      conDescarga(() => exportarLibroVentas(filtros, formato)),
  })
}

export function useExportarValorizacion() {
  return useMutation({
    mutationFn: ({ filtros, formato }: { filtros: FiltrosValorizacion; formato: FormatoExportacion }) =>
      conDescarga(() => exportarValorizacion(filtros, formato)),
  })
}

export function useExportarMovimientos() {
  return useMutation({
    mutationFn: ({ filtros, formato }: { filtros: FiltrosMovimientos; formato: FormatoExportacion }) =>
      conDescarga(() => exportarMovimientos(filtros, formato)),
  })
}

export function useExportarTendencias() {
  return useMutation({
    mutationFn: ({ filtros, formato }: { filtros: FiltrosTendencias; formato: FormatoExportacion }) =>
      conDescarga(() => exportarTendencias(filtros, formato)),
  })
}
