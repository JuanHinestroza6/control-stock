import type { Talla } from './catalogo'
import type { Pagina, ParametrosPagina } from './paginacion'

export type TipoAlerta = 'STOCK_BAJO' | 'AGOTADO' | 'SIN_ROTACION'
export type EstadoAlerta = 'ACTIVA' | 'ATENDIDA'

export interface AlertaResponse {
  id: number
  tipo: TipoAlerta
  estado: EstadoAlerta
  varianteId: number
  sku: string
  productoNombre: string
  talla: Talla
  color: string
  stockDisponible: number
  umbral: number
  creadaEn: string
  revisadaEn: string
  atendidaEn: string | null
  /** null = sistema (la condición se normalizó). */
  atendidaPor: string | null
}

export interface ResumenAlertasResponse {
  activas: number
  agotado: number
  stockBajo: number
}

export interface FiltrosAlertas extends ParametrosPagina {
  estado?: EstadoAlerta
  tipo?: TipoAlerta
}

export type PaginaAlertas = Pagina<AlertaResponse>
