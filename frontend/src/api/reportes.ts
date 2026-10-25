import { leerArchivo, normalizarErrorBlob, type ArchivoDescargado } from '../lib/descarga'
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
import { cliente } from './cliente'

type Params = Record<string, string | number | boolean>

export function parametrosRentabilidad(f: FiltrosRentabilidad): Params {
  const params: Params = {}
  if (f.desde) params.desde = f.desde
  if (f.hasta) params.hasta = f.hasta
  if (f.categoriaId !== undefined) params.categoriaId = f.categoriaId
  if (f.agrupacion) params.agrupacion = f.agrupacion
  if (f.orden) params.orden = f.orden
  return params
}

export function parametrosLibroVentas(f: FiltrosLibroVentas): Params {
  const params: Params = {}
  if (f.desde) params.desde = f.desde
  if (f.hasta) params.hasta = f.hasta
  if (f.agrupacion) params.agrupacion = f.agrupacion
  return params
}

export function parametrosValorizacion(f: FiltrosValorizacion): Params {
  const params: Params = {}
  if (f.categoriaId !== undefined) params.categoriaId = f.categoriaId
  if (f.agrupacion) params.agrupacion = f.agrupacion
  if (f.incluirInactivas !== undefined) params.incluirInactivas = f.incluirInactivas
  return params
}

export function parametrosMovimientos(f: FiltrosMovimientos): Params {
  const params: Params = {}
  if (f.desde) params.desde = f.desde
  if (f.hasta) params.hasta = f.hasta
  if (f.categoriaId !== undefined) params.categoriaId = f.categoriaId
  if (f.agrupacion) params.agrupacion = f.agrupacion
  return params
}

export async function obtenerRentabilidad(filtros: FiltrosRentabilidad): Promise<RentabilidadResponse> {
  const { data } = await cliente.get<RentabilidadResponse>('/reportes/rentabilidad', { params: parametrosRentabilidad(filtros) })
  return data
}

export async function obtenerLibroVentas(filtros: FiltrosLibroVentas): Promise<LibroVentasResponse> {
  const { data } = await cliente.get<LibroVentasResponse>('/reportes/libro-ventas', { params: parametrosLibroVentas(filtros) })
  return data
}

export async function obtenerValorizacion(filtros: FiltrosValorizacion): Promise<ValorizacionResponse> {
  const { data } = await cliente.get<ValorizacionResponse>('/reportes/valorizacion', { params: parametrosValorizacion(filtros) })
  return data
}

export async function obtenerMovimientos(filtros: FiltrosMovimientos): Promise<MovimientosResponse> {
  const { data } = await cliente.get<MovimientosResponse>('/reportes/movimientos', { params: parametrosMovimientos(filtros) })
  return data
}

async function descargar(url: string, params: Params, formato: FormatoExportacion, respaldo: string): Promise<ArchivoDescargado> {
  try {
    const respuesta = await cliente.get<Blob>(url, { params: { ...params, formato }, responseType: 'blob' })
    return leerArchivo(respuesta, `${respaldo}.${formato.toLowerCase()}`)
  } catch (error) {
    return normalizarErrorBlob(error)
  }
}

export function exportarRentabilidad(filtros: FiltrosRentabilidad, formato: FormatoExportacion): Promise<ArchivoDescargado> {
  return descargar('/reportes/rentabilidad/exportar', parametrosRentabilidad(filtros), formato, 'rentabilidad')
}

export function exportarLibroVentas(filtros: FiltrosLibroVentas, formato: FormatoExportacion): Promise<ArchivoDescargado> {
  return descargar('/reportes/libro-ventas/exportar', parametrosLibroVentas(filtros), formato, 'libro-ventas')
}

export function exportarValorizacion(filtros: FiltrosValorizacion, formato: FormatoExportacion): Promise<ArchivoDescargado> {
  return descargar('/reportes/valorizacion/exportar', parametrosValorizacion(filtros), formato, 'valorizacion')
}

export function exportarMovimientos(filtros: FiltrosMovimientos, formato: FormatoExportacion): Promise<ArchivoDescargado> {
  return descargar('/reportes/movimientos/exportar', parametrosMovimientos(filtros), formato, 'movimientos')
}
