import type { CategoriaRef, EstadoStock, Talla } from './catalogo'
import type { Pagina, ParametrosPagina } from './paginacion'

export type ClaseAbc = 'A' | 'B' | 'C'
export type CriterioMasVendidos = 'UNIDADES' | 'INGRESO'
export type AgrupacionMasVendidos = 'PRODUCTO' | 'VARIANTE'
export type MetodoReorden = 'NORMAL' | 'POISSON' | 'SIN_HISTORIA' | 'SIN_DEMANDA'
export type FuenteUmbral = 'ROP' | 'STOCK_MINIMO'
export type OrigenEjecucion = 'NOCTURNA' | 'MANUAL' | 'SEMILLA'
export type EstadoEjecucion = 'EN_CURSO' | 'EXITOSA' | 'FALLIDA'
export type MetodoPronostico = 'SES' | 'HOLT_WINTERS'
export type FuenteDemanda = 'PRONOSTICO' | 'HISTORICA'
export type MotivoSinHw = 'POCA_HISTORIA' | 'MES_EN_CERO' | 'NIVEL_NO_POSITIVO'

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
  /** Los conteos llegan en null mientras está EN_CURSO; si el cálculo falló, solo traen la métrica de alertas. */
  variantesAnalizadas: number | null
  claseA: number | null
  claseB: number | null
  claseC: number | null
  conPuntoReorden: number | null
  alertasEvaluadas: number | null
  alertasFallidas: number | null
  variantesActivas: number | null
  enAlertaStock: number | null
  porcentajeAlertaStock: number | null
  sinRotacionActivas: number | null
  mensaje: string | null
  /** Fase 6: opcionales hasta que el backend los envíe. */
  productosPronosticados?: number | null
  conHoltWinters?: number | null
  conSes?: number | null
  /** 0-1; null si no hay backtest. */
  wapeGlobal?: number | null
  /** Contrafactual: en alerta de stock si no se contara lo que viene en camino. */
  enAlertaSinTransito?: number | null
  conPedidoEnCamino?: number | null
}

export interface ResumenAbcResponse {
  ejecucion: EjecucionAnaliticaResponse | null
  /** null si nunca hubo una ejecución exitosa. */
  ventanaDesde: string | null
  ventanaHasta: string | null
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
  /** null con método SIN_HISTORIA (no hay cálculo). */
  stockSeguridad: number | null
  puntoReorden: number | null
  metodo: MetodoReorden
  stockMinimo: number
  umbralEfectivo: number
  fuenteUmbral: FuenteUmbral
  stockDisponible: number
  estadoStock: EstadoStock
  /** Fase 6: opcionales hasta que el backend los envíe. */
  enCamino?: number | null
  /** disponible + en camino. */
  posicion?: number | null
  fuenteDemanda?: FuenteDemanda | null
  demandaLeadTime?: number | null
  tasaPronosticada?: number | null
}

export type PaginaReorden = Pagina<ReordenVarianteResponse>

/** Periodo mensual `YYYY-MM`. */
export type Periodo = string

export interface FiltrosTendencias {
  /** `YYYY-MM`. Por defecto los últimos 24 meses completos; máximo 36. */
  desde?: Periodo
  hasta?: Periodo
  categoriaId?: number
  productoId?: number
}

export interface PuntoTendencia {
  periodo: Periodo
  unidades: number
  ingreso: number
  unidadesAnioAnterior: number
  ingresoAnioAnterior: number
  /** %, escala 1; null si el año anterior es 0. */
  variacionUnidades: number | null
  variacionIngreso: number | null
}

export interface TendenciaResponse {
  desde: Periodo
  hasta: Periodo
  categoriaId: number | null
  productoId: number | null
  puntos: PuntoTendencia[]
  totalUnidades: number
  totalIngreso: number
}

export interface FiltrosPronosticos extends ParametrosPagina {
  categoriaId?: number
  q?: string
  metodo?: MetodoPronostico
}

export interface PuntoPronostico {
  periodo: Periodo
  cantidad: number
}

export interface PronosticoProductoResponse {
  productoId: number
  codigo: string
  nombre: string
  categoria: string
  /** null si el producto no tiene cálculo (SIN_HISTORIA). */
  metodo: MetodoPronostico | null
  errorWape: number | null
  wapeSes: number | null
  wapeHw: number | null
  motivoSinHw: MotivoSinHw | null
  mesesHistoria: number
  unidadesUltimos12m: number
  unidadesCorregidas12m: number
  proximos: PuntoPronostico[]
}

export type PaginaPronosticos = Pagina<PronosticoProductoResponse>

export interface ParametrosModelo {
  alfa: number | null
  beta: number | null
  gamma: number | null
}

export interface PuntoSerie {
  periodo: Periodo
  observada: number
  /** Fracción 0-1 de días con stock. */
  disponibilidad: number
  corregida: number
  backtestSes: number | null
  backtestHw: number | null
}

export interface PronosticoVarianteResponse {
  varianteId: number
  sku: string
  talla: Talla
  color: string
  /** Fracción 0-1 del pronóstico del producto. */
  participacion: number
  pronostico: PuntoPronostico[]
}

export interface PronosticoDetalleResponse extends PronosticoProductoResponse {
  parametros: ParametrosModelo | null
  serie: PuntoSerie[]
  pronostico: PuntoPronostico[]
  variantes: PronosticoVarianteResponse[]
  calculadoEn: string | null
}
