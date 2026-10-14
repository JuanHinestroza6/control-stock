import type { CategoriaRef, EstadoStock, Talla } from './catalogo'
import type { Pagina, ParametrosPagina } from './paginacion'

export type ClaseAbc = 'A' | 'B' | 'C'
export type CriterioMasVendidos = 'UNIDADES' | 'INGRESO'
export type AgrupacionMasVendidos = 'PRODUCTO' | 'VARIANTE'
export type MetodoReorden = 'NORMAL' | 'POISSON' | 'SIN_HISTORIA' | 'SIN_DEMANDA'
export type FuenteUmbral = 'ROP' | 'STOCK_MINIMO'
export type OrigenEjecucion = 'NOCTURNA' | 'MANUAL' | 'SEMILLA'
export type EstadoEjecucion = 'EN_CURSO' | 'EXITOSA' | 'FALLIDA'

export interface FiltrosMasVendidos {
  /** ISO `YYYY-MM-DD`. Por defecto el servidor usa hoy - 29 días. */
  desde?: string
  hasta?: string
  categoriaId?: number
  criterio?: CriterioMasVendidos
  agrupacion?: AgrupacionMasVendidos
  /** 1-50 (por defecto 10). */
  limite?: number
}

export interface MasVendidoItem {
  posicion: number
  productoId: number
  productoCodigo: string
  productoNombre: string
  categoria: CategoriaRef
  /** Solo con agrupación VARIANTE. */
  varianteId?: number | null
  sku?: string | null
  talla?: Talla | null
  color?: string | null
  unidades: number
  ingreso: number
  /** 0-100, escala 1. */
  participacion: number
  activo: boolean
}

export interface MasVendidosResponse {
  desde: string
  hasta: string
  criterio: CriterioMasVendidos
  agrupacion: AgrupacionMasVendidos
  categoriaId: number | null
  totalUnidades: number
  totalIngreso: number
  items: MasVendidoItem[]
}

export interface FiltrosAbc extends ParametrosPagina {
  clase?: ClaseAbc
  categoriaId?: number
  q?: string
}

export interface AbcVarianteResponse {
  varianteId: number
  sku: string
  productoId: number
  productoNombre: string
  categoria: CategoriaRef
  talla: Talla
  color: string
  posicion: number
  unidadesNetas: number
  ingresoNeto: number
  /** 0-100, escala 2. */
  participacion: number
  acumulado: number
  claseAbc: ClaseAbc
}

export type PaginaAbc = Pagina<AbcVarianteResponse>

export interface ResumenClaseAbc {
  clase: ClaseAbc
  variantes: number
  ingreso: number
  participacion: number
}

export interface EjecucionAnaliticaResponse {
  id: number
  origen: OrigenEjecucion
  /** Nombre del usuario; null en procesos del sistema. */
  usuario: string | null
  iniciadaEn: string
  terminadaEn: string | null
  estado: EstadoEjecucion
  fechaCorte: string
  duracionMs: number | null
  variantesAnalizadas: number
  claseA: number
  claseB: number
  claseC: number
  conPuntoReorden: number
  alertasEvaluadas: number
  alertasFallidas: number
  variantesActivas: number
  enAlertaStock: number
  porcentajeAlertaStock: number
  sinRotacionActivas: number
  mensaje: string | null
}

export interface ResumenAbcResponse {
  ejecucion: EjecucionAnaliticaResponse | null
  ventanaDesde: string
  ventanaHasta: string
  totalIngreso: number
  clases: ResumenClaseAbc[]
}

export interface FiltrosReorden extends ParametrosPagina {
  clase?: ClaseAbc
  categoriaId?: number
  q?: string
  metodo?: MetodoReorden
  enAlerta?: boolean
}

export interface ReordenVarianteResponse {
  varianteId: number
  sku: string
  productoNombre: string
  categoria: CategoriaRef
  talla: Talla
  color: string
  claseAbc: ClaseAbc | null
  diasHistoria: number
  demandaMedia: number
  desviacion: number
  leadTimeDias: number
  nivelServicio: number
  z: number
  stockSeguridad: number
  puntoReorden: number | null
  metodo: MetodoReorden
  stockMinimo: number
  umbralEfectivo: number
  fuenteUmbral: FuenteUmbral
  stockDisponible: number
  estadoStock: EstadoStock
}

export type PaginaReorden = Pagina<ReordenVarianteResponse>
