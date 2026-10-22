import type {
  EjecucionAnaliticaResponse,
  FiltrosAbc,
  FiltrosMasVendidos,
  FiltrosPronosticos,
  FiltrosReorden,
  FiltrosTendencias,
  MasVendidosResponse,
  PaginaAbc,
  PaginaPronosticos,
  PaginaReorden,
  PronosticoDetalleResponse,
  ResumenAbcResponse,
  TendenciaResponse,
} from '../tipos/analitica'
import { leerArchivo, normalizarErrorBlob, type ArchivoDescargado } from '../lib/descarga'
import type { FormatoExportacion } from '../tipos/reportes'
import { cliente } from './cliente'

type Params = Record<string, string | number | boolean>

function agregarPaginacion(params: Params, f: { page?: number; size?: number; sort?: string }) {
  if (f.page !== undefined) params.page = f.page
  if (f.size !== undefined) params.size = f.size
  if (f.sort) params.sort = f.sort
}

/** Query de más vendidos: omite filtros indefinidos. */
export function parametrosMasVendidos(f: FiltrosMasVendidos): Params {
  const params: Params = {}
  if (f.desde) params.desde = f.desde
  if (f.hasta) params.hasta = f.hasta
  if (f.categoriaId !== undefined) params.categoriaId = f.categoriaId
  if (f.criterio) params.criterio = f.criterio
  if (f.agrupacion) params.agrupacion = f.agrupacion
  if (f.limite !== undefined) params.limite = f.limite
  return params
}

export function parametrosAbc(f: FiltrosAbc): Params {
  const params: Params = {}
  if (f.clase) params.clase = f.clase
  if (f.categoriaId !== undefined) params.categoriaId = f.categoriaId
  if (f.q?.trim()) params.q = f.q.trim()
  agregarPaginacion(params, f)
  return params
}

export function parametrosReorden(f: FiltrosReorden): Params {
  const params: Params = {}
  if (f.clase) params.clase = f.clase
  if (f.categoriaId !== undefined) params.categoriaId = f.categoriaId
  if (f.q?.trim()) params.q = f.q.trim()
  if (f.metodo) params.metodo = f.metodo
  if (f.enAlerta !== undefined) params.enAlerta = f.enAlerta
  agregarPaginacion(params, f)
  return params
}

export async function obtenerMasVendidos(filtros: FiltrosMasVendidos): Promise<MasVendidosResponse> {
  const { data } = await cliente.get<MasVendidosResponse>('/analitica/mas-vendidos', { params: parametrosMasVendidos(filtros) })
  return data
}

export async function listarAbc(filtros: FiltrosAbc): Promise<PaginaAbc> {
  const { data } = await cliente.get<PaginaAbc>('/analitica/abc', { params: parametrosAbc(filtros) })
  return data
}

export async function obtenerResumenAbc(): Promise<ResumenAbcResponse> {
  const { data } = await cliente.get<ResumenAbcResponse>('/analitica/abc/resumen')
  return data
}

export async function listarReorden(filtros: FiltrosReorden): Promise<PaginaReorden> {
  const { data } = await cliente.get<PaginaReorden>('/analitica/reorden', { params: parametrosReorden(filtros) })
  return data
}

/** 204 (nunca corrió) se devuelve como null. */
export async function obtenerUltimaEjecucion(): Promise<EjecucionAnaliticaResponse | null> {
  const respuesta = await cliente.get<EjecucionAnaliticaResponse | ''>('/analitica/ejecuciones/ultima')
  if (respuesta.status === 204 || !respuesta.data) return null
  return respuesta.data
}

export async function recalcularAnalitica(): Promise<EjecucionAnaliticaResponse> {
  const { data } = await cliente.post<EjecucionAnaliticaResponse>('/analitica/recalcular')
  return data
}

export function parametrosTendencias(f: FiltrosTendencias): Params {
  const params: Params = {}
  if (f.desde) params.desde = f.desde
  if (f.hasta) params.hasta = f.hasta
  if (f.categoriaId !== undefined) params.categoriaId = f.categoriaId
  if (f.productoId !== undefined) params.productoId = f.productoId
  return params
}

export function parametrosPronosticos(f: FiltrosPronosticos): Params {
  const params: Params = {}
  if (f.categoriaId !== undefined) params.categoriaId = f.categoriaId
  if (f.q?.trim()) params.q = f.q.trim()
  if (f.metodo) params.metodo = f.metodo
  agregarPaginacion(params, f)
  return params
}

export async function obtenerTendencias(filtros: FiltrosTendencias): Promise<TendenciaResponse> {
  const { data } = await cliente.get<TendenciaResponse>('/analitica/tendencias', { params: parametrosTendencias(filtros) })
  return data
}

/** Exporta las tendencias con los mismos filtros de la consulta (ADMIN, INVENTARIO, CONTABILIDAD). */
export async function exportarTendencias(filtros: FiltrosTendencias, formato: FormatoExportacion): Promise<ArchivoDescargado> {
  try {
    const respuesta = await cliente.get<Blob>('/analitica/tendencias/exportar', {
      params: { ...parametrosTendencias(filtros), formato },
      responseType: 'blob',
    })
    return leerArchivo(respuesta, `tendencias.${formato.toLowerCase()}`)
  } catch (error) {
    return normalizarErrorBlob(error)
  }
}

export async function listarPronosticos(filtros: FiltrosPronosticos): Promise<PaginaPronosticos> {
  const { data } = await cliente.get<PaginaPronosticos>('/analitica/pronosticos', { params: parametrosPronosticos(filtros) })
  return data
}

export async function obtenerPronostico(productoId: number): Promise<PronosticoDetalleResponse> {
  const { data } = await cliente.get<PronosticoDetalleResponse>(`/analitica/pronosticos/${productoId}`)
  return data
}
